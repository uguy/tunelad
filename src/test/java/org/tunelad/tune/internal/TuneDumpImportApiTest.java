package org.tunelad.tune.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Tests for openspec/specs/tune (the bulk import), against a local server answering like the dump's host. */
class TuneDumpImportApiTest extends AbstractApiTest {

	static final AtomicBoolean DOWN = new AtomicBoolean();
	static final HttpServer SERVER = serve();

	static HttpServer serve() {
		try {
			var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/tunes.csv", exchange -> reply(exchange, """
					tune_id,setting_id,name,type,meter,mode,abc,date,username,composer
					27,1,Cooley's,reel,4/4,Edorian,"EBBA B2 EB|\r\nB2 AB dBAG|",2001,someone,Unknown
					27,2,Cooley's,reel,4/4,Gmajor,"SECOND SETTING",2002,other,
					28,3,Odd One,foxtrot,4/4,Gmajor,"abc",2002,other,
					29,4,The Kesh,jig,6/8,Gmajor,"GAB c2A|",2003,other,
					"""));
			server.createContext("/aliases.csv", exchange -> reply(exchange, """
					tune_id,alias,name
					27,Cooley's Reel,Cooley's
					"""));
			server.start();
			return server;
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
		var bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(DOWN.get() ? 503 : 200, DOWN.get() ? -1 : bytes.length);
		if (!DOWN.get()) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

	@DynamicPropertySource
	static void dump(DynamicPropertyRegistry registry) {
		registry.add("tunelad.tune.dump-url", () -> "http://localhost:" + SERVER.getAddress().getPort());
	}

	@AfterAll
	static void stop() {
		SERVER.stop(0);
	}

	@Autowired
	TuneDumpImport dumpImport;

	String admin = TestTokens.admin();

	@BeforeEach
	void reset() {
		DOWN.set(false);
		await().atMost(Duration.ofSeconds(10)).until(() -> dumpImport.last().state() != TuneDumpImport.State.running);
	}

	private void importDump() {
		client.post().uri("/api/tunes/import-dump").headers(h -> h.setBearerAuth(admin)).exchange().expectStatus().isAccepted();
		await().atMost(Duration.ofSeconds(20)).until(() -> dumpImport.last().state() != TuneDumpImport.State.running);
	}

	@Test
	void import_the_dump() {
		importDump();

		assertThat(dumpImport.last().state()).isEqualTo(TuneDumpImport.State.done);
		assertThat(count("tune_tunes", "name = 'Cooley''s' AND dance_type = 'reel' AND author = 'Unknown' "
				+ "AND aliases = ARRAY['Cooley''s Reel'] AND reference_abc LIKE '%EBBA B2 EB|%' AND reference_abc NOT LIKE '%SECOND%'")).isEqualTo(1);
		assertThat(count("tune_tunes", null)).isEqualTo(2);
		assertThat(dumpImport.last().created()).isEqualTo(2);
		assertThat(dumpImport.last().skipped()).isEqualTo(1);
	}

	@Test
	void run_again() {
		importDump();
		importDump();

		assertThat(count("tune_tunes", null)).isEqualTo(2);
		assertThat(dumpImport.last().created()).isZero();
	}

	@Test
	void resume_after_an_interruption() {
		importDump();
		db.sql("DELETE FROM tune_tunes WHERE source_id = 29").fetch().rowsUpdated().block();
		db.sql("UPDATE tune_tunes SET author = 'Edited' WHERE source_id = 27").fetch().rowsUpdated().block();

		importDump();

		assertThat(count("tune_tunes", "source_id = 29")).isEqualTo(1);
		assertThat(count("tune_tunes", "source_id = 27 AND author = 'Edited'")).isEqualTo(1);
	}

	@Test
	void not_an_administrator() {
		client.post().uri("/api/tunes/import-dump").headers(h -> h.setBearerAuth(UPLOADER_TOKEN)).exchange().expectStatus().isForbidden();
		client.get().uri("/api/tunes/import-dump").headers(h -> h.setBearerAuth(UPLOADER_TOKEN)).exchange().expectStatus().isForbidden();
		client.post().uri("/api/tunes/import-dump").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void read_the_progress() {
		importDump();

		client.get().uri("/api/tunes/import-dump").headers(h -> h.setBearerAuth(admin)).exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.state").isEqualTo("done").jsonPath("$.created").isEqualTo(2);
	}

	@Test
	void download_fails() {
		DOWN.set(true);

		importDump();

		assertThat(dumpImport.last().state()).isEqualTo(TuneDumpImport.State.failed);
		assertThat(dumpImport.last().reason()).isNotBlank();
	}
}

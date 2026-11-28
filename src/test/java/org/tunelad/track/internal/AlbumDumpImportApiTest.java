package org.tunelad.track.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;
import org.tunelad.track.AlbumDTO;
import org.tunelad.track.TrackDTO;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Tests for the recordings import of openspec/changes/albums-and-catalogue-tracks-from-thesession-org-recordings, with a fake dump. */
@Import(AlbumDumpImportApiTest.FakeDump.class)
class AlbumDumpImportApiTest extends AbstractApiTest {

	static final AtomicReference<Flux<Map<String, String>>> DUMP = new AtomicReference<>(Flux.empty());

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeDump {
		@Bean
		@Primary
		AlbumDump fakeAlbumDump() {
			return () -> Flux.defer(DUMP::get);
		}
	}

	@Autowired
	AlbumDumpImport dumpImport;

	final String admin = TestTokens.admin();

	@BeforeEach
	void reset() {
		await().atMost(Duration.ofSeconds(10)).until(() -> dumpImport.last().state() != AlbumDumpImport.State.running);
		DUMP.set(Flux.empty());
	}

	private static Map<String, String> row(int id, String artist, String recording, int track, int number, String tune, Integer tuneId) {
		return Map.of("id", String.valueOf(id), "artist", artist, "recording", recording, "track", String.valueOf(track),
				"number", String.valueOf(number), "tune", tune, "tune_id", tuneId == null ? "" : String.valueOf(tuneId));
	}

	private void dump(Map<String, String>... rows) {
		DUMP.set(Flux.just(rows));
	}

	private void tune(int sourceId, String name) {
		db.sql("INSERT INTO tune_tunes (id, source_id, name, dance_type, created_by) VALUES (:id, :source, :name, 'reel', 'test')")
				.bind("id", "tune-" + sourceId).bind("source", sourceId).bind("name", name).fetch().rowsUpdated().block();
	}

	private void importDump() {
		client.post().uri("/api/albums/import-dump").headers(h -> h.setBearerAuth(admin)).exchange().expectStatus().isAccepted();
		await().atMost(Duration.ofSeconds(30)).until(() -> dumpImport.last().state() != AlbumDumpImport.State.running);
	}

	private AlbumDTO onlyAlbum() {
		var list = client.get().uri("/api/albums").exchange().expectStatus().isOk().expectBodyList(AlbumDTO.class).returnResult().getResponseBody();
		assertThat(list).hasSize(1);
		return client.get().uri("/api/albums/{id}", list.get(0).id()).exchange().expectStatus().isOk()
				.expectBody(AlbumDTO.class).returnResult().getResponseBody();
	}

	@Test
	@SuppressWarnings("unchecked")
	void all_tunes_known() {
		tune(27, "Cooley's");
		tune(28, "The Kesh");
		// out of order on purpose: the number orders the tunes of a track
		dump(row(1, "Dervish", "At the End of the Day", 1, 2, "The Kesh", 28), row(1, "Dervish", "At the End of the Day", 1, 1, "Cooley's", 27));

		importDump();

		assertThat(dumpImport.last().state()).isEqualTo(AlbumDumpImport.State.done);
		assertThat(dumpImport.last().albumsCreated()).isEqualTo(1);
		assertThat(dumpImport.last().tracksCreated()).isEqualTo(1);
		var album = onlyAlbum();
		assertThat(album.artist()).isEqualTo("Dervish");
		assertThat(count("track_artists", "name = 'Dervish' AND kind IS NULL")).isEqualTo(1);
		assertThat(album.tracks()).hasSize(1);
		var track = album.tracks().get(0);
		assertThat(track.catalogue()).isTrue();
		assertThat(track.albumPosition()).isEqualTo(1);
		assertThat(track.set()).isNotNull();
		assertThat(track.set().tunes()).extracting(t -> t.name()).containsExactly("Cooley's", "The Kesh");
		assertThat(track.performances()).extracting(p -> p.tuneId()).containsExactly("tune-27", "tune-28");
	}

	@Test
	@SuppressWarnings("unchecked")
	void unknown_tune_leaves_the_track_without_a_set() {
		tune(27, "Cooley's");
		dump(row(1, "Dervish", "At the End of the Day", 1, 1, "Cooley's", 27), row(1, "Dervish", "At the End of the Day", 1, 2, "The Silver Spear", 999999));

		importDump();

		var track = onlyAlbum().tracks().get(0);
		assertThat(track.set()).isNull();
		assertThat(track.performances()).hasSize(2);
		assertThat(track.performances().get(0).tuneId()).isEqualTo("tune-27");
		assertThat(track.performances().get(1).tuneId()).isNull();
		assertThat(track.performances().get(1).listedName()).isEqualTo("The Silver Spear");
	}

	@Test
	@SuppressWarnings("unchecked")
	void run_again_creates_nothing_twice() {
		tune(27, "Cooley's");
		dump(row(1, "Dervish", "At the End of the Day", 1, 1, "Cooley's", 27), row(1, "Dervish", "At the End of the Day", 2, 1, "Odd", null),
				row(2, "dervish", "AT THE END OF THE DAY", 1, 1, "Cooley's", 27), row(3, "Altan", "Harvest Storm", 1, 1, "Odd", null),
				row(4, "", "No Artist", 1, 1, "Odd", null));

		importDump();
		assertThat(dumpImport.last().albumsCreated()).isEqualTo(2);
		assertThat(dumpImport.last().tracksCreated()).isEqualTo(3);
		assertThat(dumpImport.last().skipped()).isEqualTo(1);
		importDump();

		assertThat(dumpImport.last().state()).isEqualTo(AlbumDumpImport.State.done);
		assertThat(dumpImport.last().albumsCreated()).isZero();
		assertThat(dumpImport.last().tracksCreated()).isZero();
		assertThat(count("track_albums", null)).isEqualTo(2);
		assertThat(count("track_tracks", "catalogue")).isEqualTo(3);
		assertThat(count("track_artists", null)).isEqualTo(2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void resume_adds_only_what_is_missing() {
		dump(row(1, "Dervish", "Album", 1, 1, "A", null), row(1, "Dervish", "Album", 2, 1, "B", null));
		importDump();
		db.sql("DELETE FROM track_performances WHERE track_id IN (SELECT id FROM track_tracks WHERE album_position = 2)").fetch().rowsUpdated().block();
		db.sql("DELETE FROM track_tracks WHERE album_position = 2").fetch().rowsUpdated().block();
		db.sql("UPDATE track_tracks SET title = 'Edited' WHERE album_position = 1").fetch().rowsUpdated().block();

		importDump();

		assertThat(dumpImport.last().albumsCreated()).isZero();
		assertThat(dumpImport.last().tracksCreated()).isEqualTo(1);
		assertThat(count("track_tracks", "title = 'Edited'")).isEqualTo(1);
		assertThat(count("track_tracks", "catalogue")).isEqualTo(2);
	}

	@Test
	void not_an_administrator() {
		client.post().uri("/api/albums/import-dump").headers(h -> h.setBearerAuth(UPLOADER_TOKEN)).exchange().expectStatus().isForbidden();
		client.get().uri("/api/albums/import-dump").headers(h -> h.setBearerAuth(UPLOADER_TOKEN)).exchange().expectStatus().isForbidden();
		client.post().uri("/api/albums/import-dump").exchange().expectStatus().isUnauthorized();
		client.get().uri("/api/albums/import-dump").exchange().expectStatus().isUnauthorized();
	}

	@Test
	@SuppressWarnings("unchecked")
	void read_the_progress() {
		dump(row(1, "Dervish", "Album", 1, 1, "A", null));
		importDump();

		client.get().uri("/api/albums/import-dump").headers(h -> h.setBearerAuth(admin)).exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.state").isEqualTo("done").jsonPath("$.albumsCreated").isEqualTo(1)
				.jsonPath("$.tracksCreated").isEqualTo(1).jsonPath("$.skipped").isEqualTo(0);
	}

	@Test
	void download_fails_midway_and_the_albums_already_created_are_kept() {
		var rows = new ArrayList<Map<String, String>>();
		for (var i = 1; i <= 60; i++) { // more than a batch, which is written once complete
			rows.add(row(i, "Dervish", "Album " + i, 1, 1, "A", null));
		}
		DUMP.set(Flux.concat(Flux.fromIterable(rows), Flux.error(new IllegalStateException("The dump is down"))));

		importDump();

		assertThat(dumpImport.last().state()).isEqualTo(AlbumDumpImport.State.failed);
		assertThat(dumpImport.last().reason()).contains("The dump is down");
		assertThat(count("track_albums", null)).isGreaterThanOrEqualTo(50);
		assertThat(dumpImport.last().albumsCreated()).isGreaterThanOrEqualTo(50);
	}

	@Test
	void download_fails_at_once() {
		DUMP.set(Flux.error(new IllegalStateException("503 Service Unavailable")));

		importDump();

		assertThat(dumpImport.last().state()).isEqualTo(AlbumDumpImport.State.failed);
		assertThat(dumpImport.last().reason()).isNotBlank();
	}
}

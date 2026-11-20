package org.tunelad;

import java.util.Collections;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Imports sample tracks into a running application (http://localhost:8080), as alice from the local Keycloak realm
 * (docker compose): enable it and run it by hand.
 */
@Disabled
@Slf4j
public class ImporterTest {

	private static final String TOKEN_URL = "http://localhost:8180/realms/tunelad/protocol/openid-connect/token";

	private final ResourceLoader resourceLoader = new DefaultResourceLoader();

	private final WebClient client = WebClient.builder()
			.baseUrl("http://localhost:8080")
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultUriVariables(Collections.singletonMap("url", "http://localhost:8080"))
			.build();

	/** An access token for alice, with the dev realm's password grant. */
	private static String aliceToken() {
		var form = new LinkedMultiValueMap<String, String>();
		form.add("grant_type", "password");
		form.add("client_id", "tunelad");
		form.add("client_secret", "tunelad-dev-secret");
		form.add("username", "alice");
		form.add("password", "alice");
		var response = WebClient.create(TOKEN_URL).post()
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(BodyInserters.fromFormData(form))
				.retrieve()
				.bodyToMono(Map.class)
				.block();
		return (String) response.get("access_token");
	}

	@Test
	void import_knees_up() {

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("title", "Knees Up");
		builder.part("description", "Knees Up is a set mixing a reel and a song from Frenzy of the Meeting album by Breabach.");
		builder.part("artist", "Breabach");
		builder.part("album", "Frenzy of the Meeting");
		builder.part("file", resourceLoader.getResource("classpath:mp3/Knees_Up.mp3"));

		client.post().uri("api/tracks")
				.headers(headers -> headers.setBearerAuth(aliceToken()))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.exchangeToMono(response -> {
					log.info("Import track response: {}", response.statusCode());
					assertThat(response.statusCode().isError()).isFalse();
					return Mono.empty();
				}).block();
	}

	@Test
	void import_mossy_bank() {

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("title", "Mossy banks");
		builder.part("description", "Mossy banks and Ravens rock is a set of reels by Stephen Doherty and Patrick Doocey.");
		builder.part("artist", "Stephen Doherty");
		builder.part("album", "The Foxford Way");
		builder.part("file", resourceLoader.getResource("classpath:mp3/Stephen_Doherty_Patrick_Doocey-Mossy_banks_Ravens_rock.mp3"));

		client.post().uri("api/tracks")
				.headers(headers -> headers.setBearerAuth(aliceToken()))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.exchangeToMono(response -> {
					log.info("Import track response: {}", response.statusCode());
					assertThat(response.statusCode().isError()).isFalse();
					return Mono.empty();
				}).block();
	}
}

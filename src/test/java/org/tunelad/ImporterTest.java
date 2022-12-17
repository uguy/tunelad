package org.tunelad;

import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@Slf4j
public class ImporterTest {

	private final ResourceLoader resourceLoader = new DefaultResourceLoader();

	private final WebClient client = WebClient.builder()
			.baseUrl("http://localhost:8080")
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultUriVariables(Collections.singletonMap("url", "http://localhost:8080"))
			.build();

	@Test
	void import_knees_up() {

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("title", "Knees Up");
		builder.part("description", "Knees Up is a set mixing a reel and a song from Frenzy of the Meeting album by Breabach.");
		builder.part("artist", "Breabach");
		builder.part("album", "Frenzy of the Meeting");
		builder.part("file", resourceLoader.getResource("classpath:mp3/Knees_Up.mp3"));

		client.post().uri("api/tracks")
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
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.exchangeToMono(response -> {
					log.info("Import track response: {}", response.statusCode());
					assertThat(response.statusCode().isError()).isFalse();
					return Mono.empty();
				}).block();
	}
}

package org.tunelad.tune.internal;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.tunelad.tune.DanceType;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** thesession.org, through its JSON API: {@code GET /tunes/{id}?format=json}. */
@Component
class ThesessionTuneSource implements TuneSource {

	private final WebClient client;

	ThesessionTuneSource(@Value("${tunelad.tune.thesession-url:https://thesession.org}") String url) {
		this.client = WebClient.builder().baseUrl(url).build();
	}

	@Override
	public Mono<SourceTune> fetch(int sourceId) {
		return client.get().uri("/tunes/{id}?format=json", sourceId)
				.retrieve().bodyToMono(JsonNode.class)
				.timeout(Duration.ofSeconds(10))
				.flatMap(json -> Mono.justOrEmpty(toTune(sourceId, json)))
				.onErrorResume(WebClientResponseException.class, e -> e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
						? Mono.empty() : Mono.error(new SourceUnavailableException("thesession.org answered " + e.getStatusCode(), e)))
				.onErrorMap(e -> !(e instanceof SourceUnavailableException),
						e -> new SourceUnavailableException("thesession.org is unreachable", e));
	}

	static SourceTune toTune(int sourceId, JsonNode json) {
		var name = json.path("name").asText("").trim();
		var type = DanceType.fromValue(json.path("type").asText(null));
		if (name.isEmpty() || type.isEmpty()) {
			return null;
		}
		var aliases = new ArrayList<String>();
		json.path("aliases").forEach(alias -> aliases.add(alias.asText()));
		var setting = json.path("settings").path(0);
		var key = setting.path("key").asText(null);
		var body = setting.path("abc").asText(null);
		return new SourceTune(sourceId, name, List.copyOf(aliases), null, type.get(), key, body == null ? null : abc(name, type.get(), key, body));
	}

	/** A complete ABC tune: thesession.org settings are only the notes. */
	static String abc(String name, DanceType type, String key, String body) {
		return "X:1\nT:" + name + "\nM:" + type.meter() + "\nL:1/8\nK:" + abcKey(key) + "\n" + body.replace("\r\n", "\n").replace("!", "\n");
	}

	/** thesession.org keys look like "Edorian" or "Gmajor"; ABC wants "Edor" or "G". */
	static String abcKey(String key) {
		if (key == null || key.isBlank()) {
			return "C";
		}
		return key.replace("major", "").replace("minor", "m").replace("dorian", "dor")
				.replace("mixolydian", "mix").replace("lydian", "lyd").replace("phrygian", "phr");
	}
}

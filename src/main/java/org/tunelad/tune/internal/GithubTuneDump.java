package org.tunelad.tune.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/** adactio/TheSession-data on GitHub, streamed when an import runs; nothing is stored. */
@Component
class GithubTuneDump implements TuneDump {

	private final WebClient client;

	GithubTuneDump(@Value("${tunelad.tune.dump-url:https://raw.githubusercontent.com/adactio/TheSession-data/main/csv}") String url) {
		this.client = WebClient.builder().baseUrl(url).build();
	}

	@Override
	public Flux<Map<String, String>> tunes() {
		return rows("tunes.csv");
	}

	@Override
	public Flux<Map<String, String>> aliases() {
		return rows("aliases.csv");
	}

	private Flux<Map<String, String>> rows(String file) {
		var bytes = client.get().uri("/{file}", file).retrieve().bodyToFlux(DataBuffer.class).map(buffer -> {
			try {
				var chunk = new byte[buffer.readableByteCount()];
				buffer.read(chunk);
				return chunk;
			} finally {
				DataBufferUtils.release(buffer);
			}
		});
		return CsvParser.withHeader(bytes);
	}
}

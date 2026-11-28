package org.tunelad.track.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tunelad.infrastructure.CsvDump;
import reactor.core.publisher.Flux;

import java.util.Map;

/** adactio/TheSession-data on GitHub, streamed when an import runs; nothing is stored. */
@Component
class GithubAlbumDump implements AlbumDump {

	private final CsvDump dump;

	GithubAlbumDump(@Value("${tunelad.track.dump-url:" + CsvDump.DEFAULT_URL + "}") String url) {
		this.dump = new CsvDump(url);
	}

	@Override
	public Flux<Map<String, String>> recordings() {
		return dump.rows("recordings.csv");
	}
}

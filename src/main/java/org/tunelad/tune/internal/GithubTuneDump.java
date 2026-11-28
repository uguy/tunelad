package org.tunelad.tune.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tunelad.infrastructure.CsvDump;
import reactor.core.publisher.Flux;

import java.util.Map;

/** adactio/TheSession-data on GitHub, streamed when an import runs; nothing is stored. */
@Component
class GithubTuneDump implements TuneDump {

	private final CsvDump dump;

	GithubTuneDump(@Value("${tunelad.tune.dump-url:" + CsvDump.DEFAULT_URL + "}") String url) {
		this.dump = new CsvDump(url);
	}

	@Override
	public Flux<Map<String, String>> tunes() {
		return dump.rows("tunes.csv");
	}

	@Override
	public Flux<Map<String, String>> aliases() {
		return dump.rows("aliases.csv");
	}
}

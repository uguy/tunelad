package org.tunelad.infrastructure;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/** CSV files of a data dump (adactio/TheSession-data on GitHub), streamed when an import runs; nothing is stored. */
public final class CsvDump {

	/** The GitHub raw base of adactio/TheSession-data's CSV files. */
	public static final String DEFAULT_URL = "https://raw.githubusercontent.com/adactio/TheSession-data/main/csv";

	private final WebClient client;

	public CsvDump(String url) {
		this.client = WebClient.builder().baseUrl(url).build();
	}

	/** The rows of the file, one map per row keyed by the column names of its header line. */
	public Flux<Map<String, String>> rows(String file) {
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

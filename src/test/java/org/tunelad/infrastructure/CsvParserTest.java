package org.tunelad.infrastructure;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

	private static Flux<byte[]> chunks(String text, int size) {
		var bytes = text.getBytes(StandardCharsets.UTF_8);
		var parts = new java.util.ArrayList<byte[]>();
		for (var i = 0; i < bytes.length; i += size) {
			parts.add(java.util.Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + size)));
		}
		return Flux.fromIterable(parts);
	}

	@Test
	void reads_quoted_fields_with_commas_quotes_and_line_breaks() {
		var text = "id,name,abc\r\n1,\"Cooley's, the \"\"reel\"\"\",\"EBBA\r\nB2 EB|\"\r\n2,plain,\r\n";

		var rows = CsvParser.records(chunks(text, 3)).collectList().block();

		assertThat(rows).containsExactly(List.of("id", "name", "abc"),
				List.of("1", "Cooley's, the \"reel\"", "EBBA\r\nB2 EB|"), List.of("2", "plain", ""));
	}

	@Test
	void keeps_multi_byte_characters_split_between_chunks() {
		var rows = CsvParser.records(chunks("a,Séamus Ó Dálaigh\n", 2)).collectList().block();

		assertThat(rows).containsExactly(List.of("a", "Séamus Ó Dálaigh"));
	}

	@Test
	void reads_the_last_record_without_a_line_break() {
		var rows = CsvParser.withHeader(chunks("id,name\n1,Kesh", 5)).collectList().block();

		assertThat(rows).containsExactly(Map.of("id", "1", "name", "Kesh"));
	}
}

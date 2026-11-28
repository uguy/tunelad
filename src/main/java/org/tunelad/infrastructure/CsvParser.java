package org.tunelad.infrastructure;

import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An incremental RFC 4180 reader: quoted fields may hold commas, doubled quotes and line breaks. Fed with chunks
 * of text of any size, it returns the records completed so far.
 */
public final class CsvParser {

	private final List<String> record = new ArrayList<>();
	private final StringBuilder field = new StringBuilder();
	private boolean quoted;
	private boolean quoteSeen;
	private boolean skipLineFeed;

	/** Reads a stream of bytes (UTF-8) into records, without holding the whole file. */
	public static Flux<List<String>> records(Flux<byte[]> bytes) {
		return Flux.defer(() -> {
			var parser = new CsvParser();
			var decoder = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
			var pending = new ByteBuffer[] {ByteBuffer.allocate(0)};
			return bytes.concatMapIterable(chunk -> parser.feed(decode(decoder, pending, chunk)))
					.concatWith(Flux.defer(() -> Flux.fromIterable(parser.end())));
		});
	}

	/** The records of a stream with a header line, as maps from the column names. */
	public static Flux<Map<String, String>> withHeader(Flux<byte[]> bytes) {
		return Flux.defer(() -> {
			var header = new List[] {null};
			return records(bytes).<Map<String, String>>handle((row, sink) -> {
				if (header[0] == null) {
					header[0] = row;
					return;
				}
				var map = new HashMap<String, String>();
				for (var i = 0; i < header[0].size() && i < row.size(); i++) {
					map.put((String) header[0].get(i), row.get(i));
				}
				sink.next(map);
			});
		});
	}

	private static String decode(CharsetDecoder decoder, ByteBuffer[] pending, byte[] chunk) {
		var input = ByteBuffer.allocate(pending[0].remaining() + chunk.length).put(pending[0]).put(chunk).flip();
		var output = CharBuffer.allocate(input.remaining() + 1);
		decoder.decode(input, output, false);
		pending[0] = input.slice(); // an incomplete multi-byte character waits for the next chunk
		return output.flip().toString();
	}

	public List<List<String>> feed(String text) {
		var done = new ArrayList<List<String>>();
		for (var i = 0; i < text.length(); i++) {
			var c = text.charAt(i);
			if (skipLineFeed) {
				skipLineFeed = false;
				if (c == '\n') {
					continue;
				}
			}
			if (quoted) {
				if (c == '"') {
					quoted = false;
					quoteSeen = true;
				} else {
					field.append(c);
				}
			} else if (c == '"' && quoteSeen) { // a doubled quote inside a quoted field
				field.append('"');
				quoted = true;
				quoteSeen = false;
			} else if (c == '"' && field.isEmpty()) {
				quoted = true;
			} else if (c == ',') {
				endField();
			} else if (c == '\n' || c == '\r') {
				skipLineFeed = c == '\r';
				endField();
				done.add(List.copyOf(record));
				record.clear();
			} else {
				quoteSeen = false;
				field.append(c);
			}
		}
		return done;
	}

	/** The last record when the text does not end with a line break. */
	public List<List<String>> end() {
		if (field.isEmpty() && record.isEmpty() && !quoteSeen) {
			return List.of();
		}
		endField();
		var last = List.copyOf(record);
		record.clear();
		return List.of(last);
	}

	private void endField() {
		record.add(field.toString());
		field.setLength(0);
		quoteSeen = false;
	}
}

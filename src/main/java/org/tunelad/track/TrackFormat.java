package org.tunelad.track;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The audio format of a track, told by its file name's extension. */
public enum TrackFormat {
	MP3("audio/mpeg", "mp3"),
	M4A("audio/mp4", "m4a"),
	OGG("audio/ogg", "ogg"),
	FLAC("audio/flac", "flac"),
	WAV("audio/wav", "wav"),
	/** Only for reading tracks stored before unsupported files were refused: never given to a new upload. */
	UNSUPPORTED("audio/mpeg");

	private final String mimeType;
	private final List<String> extensions;

	TrackFormat(String mimeType, String... extensions) {
		this.mimeType = mimeType;
		this.extensions = List.of(extensions);
	}

	/** The content type the audio is played back with. */
	public String mimeType() {
		return mimeType;
	}

	/** The supported format of a file, by its name's extension (case-insensitive); empty when it is not supported. */
	public static Optional<TrackFormat> forFile(String filename) {
		var extension = getExtension(filename);
		return Arrays.stream(values())
				.filter(format -> format.extensions.contains(extension))
				.findFirst();
	}

	private static String getExtension(String filename) {
		return Optional.ofNullable(filename)
				.filter(f -> f.contains("."))
				.map(f -> f.substring(f.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT))
				.orElse("");
	}
}

package org.tunelad.track.spi;

import java.util.Optional;

public enum TrackFormat {
	MP3("mp3"),
	UNSUPPORTED("");

	private String extension;

	TrackFormat(String extension) {
		this.extension = extension;
	}

	public static TrackFormat forFile(String file) {
		var extension = getExtension(file);
		switch (extension) {
			case "mp3":
				return TrackFormat.MP3;
			default:
				return UNSUPPORTED;
		}
	}

	private static String getExtension(String filename) {
		return Optional.ofNullable(filename)
				.filter(f -> f.contains("."))
				.map(f -> f.substring(filename.lastIndexOf(".") + 1)).orElse("");
	}
}

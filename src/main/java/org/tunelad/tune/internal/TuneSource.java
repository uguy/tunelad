package org.tunelad.tune.internal;

import org.tunelad.tune.DanceType;
import reactor.core.publisher.Mono;

import java.util.List;

/** Where Tunes can be imported from. */
public interface TuneSource {

	/** What the source knows of a Tune. */
	record SourceTune(int sourceId, String name, List<String> aliases, String author, DanceType danceType, String key, String abc) {}

	/** The source's Tune; empty when it does not know the id; errors with {@link SourceUnavailableException} when unreachable. */
	Mono<SourceTune> fetch(int sourceId);

	class SourceUnavailableException extends RuntimeException {
		public SourceUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}

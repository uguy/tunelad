package org.tunelad.media;

import reactor.core.publisher.Mono;

/** Reads the tags of audio stored in the media module. */
public interface MediaTags {

    /**
     * The title, artist, album and comment tags of the audio stored under the key. Never fails for a file that has no
     * tags, whose tags cannot be read, or when no tag reader is available: the result is then {@link AudioTags#NONE}
     * (or has fewer values). Empty when there is no audio under the key.
     */
    Mono<AudioTags> read(String key);
}

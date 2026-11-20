package org.tunelad.media.internal;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

/**
 * Stored audio: its size, its content type, and a way to read any slice of it.
 */
public interface Media {

    /** The content type audio stored without one is played back with: all audio was mp3 before formats were recorded. */
    String DEFAULT_CONTENT_TYPE = "audio/mpeg";

    /** The content type the audio was stored with, or {@link #DEFAULT_CONTENT_TYPE} when it was stored without one. */
    String contentType();

    /** Total size of the audio, in bytes. */
    long length();

    /**
     * Reads the bytes from {@code first} to {@code last}, both inclusive. Nothing is read until the
     * returned flux is subscribed. Returns an empty flux when {@code last < first}.
     *
     * @throws IllegalArgumentException when {@code first} is negative or {@code last} is not before {@link #length()}
     */
    Flux<DataBuffer> content(long first, long last);
}

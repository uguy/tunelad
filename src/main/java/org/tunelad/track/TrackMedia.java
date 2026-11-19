package org.tunelad.track;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

/**
 * The stored audio of a track: its size, and a way to read any slice of it.
 */
public interface TrackMedia {

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

package org.tunelad.media;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Stores audio for other modules. The media module gives the key no meaning of its own. */
public interface MediaStore {

    /**
     * Stores the audio under the given key, with the content type it is played back with (such as {@code audio/flac});
     * nothing is read until the returned Mono is subscribed.
     */
    Mono<Void> store(String key, Publisher<DataBuffer> audio, String contentType);

    /**
     * The whole audio stored under the given key, in order; empty when there is none. Nothing is read until the
     * returned Flux is subscribed, and the buffers are the subscriber's to release.
     */
    Flux<DataBuffer> read(String key);

    /** Deletes the audio stored under the given key; completes normally when there is none. */
    Mono<Void> delete(String key);
}

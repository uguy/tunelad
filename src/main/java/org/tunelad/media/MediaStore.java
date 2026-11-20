package org.tunelad.media;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

/** Stores audio for other modules. The media module gives the key no meaning of its own. */
public interface MediaStore {

    /** Stores the audio under the given key; nothing is read until the returned Mono is subscribed. */
    Mono<Void> store(String key, Publisher<DataBuffer> audio);

    /** Deletes the audio stored under the given key; completes normally when there is none. */
    Mono<Void> delete(String key);
}

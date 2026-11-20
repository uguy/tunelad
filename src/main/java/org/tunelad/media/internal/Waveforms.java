package org.tunelad.media.internal;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/** The waveforms of stored audio, by the audio's key. */
public interface Waveforms extends ReactiveMongoRepository<Waveform, String> {
}

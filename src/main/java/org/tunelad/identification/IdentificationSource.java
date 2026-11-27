package org.tunelad.identification;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

import java.util.Collection;

/** What hears the Tunes in a recording. */
public interface IdentificationSource {

    /**
     * The Tunes heard in the audio, in time order, among the given thesession.org tune ids only. Fails when the
     * analysis cannot be done (service down, audio it cannot decode, timeout).
     */
    Flux<DetectedTune> detect(Flux<DataBuffer> audio, Collection<String> tuneIds);
}

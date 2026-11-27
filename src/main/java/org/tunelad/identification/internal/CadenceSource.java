package org.tunelad.identification.internal;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.tunelad.identification.DetectedTune;
import org.tunelad.identification.IdentificationSource;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Comparator;

/** Asks the sidecar that runs Cadence's recognition engine (sidecar/identification), streaming the audio to it. */
@Component
class CadenceSource implements IdentificationSource {

    private final WebClient client;

    private final IdentificationProperties properties;

    CadenceSource(IdentificationProperties properties) {
        this.client = WebClient.builder().baseUrl(properties.url()).build();
        this.properties = properties;
    }

    @Override
    public Flux<DetectedTune> detect(Flux<DataBuffer> audio, Collection<String> tuneIds) {
        if (tuneIds.isEmpty()) {
            return Flux.empty();
        }
        return client.post()
                .uri(uri -> uri.path("/identify").queryParam("tunes", String.join(",", tuneIds)).build())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(audio, DataBuffer.class)
                .retrieve()
                .bodyToFlux(DetectedTune.class)
                .timeout(properties.timeout())
                // Whatever the sidecar says, only the Tunes asked for are reported, in time order
                .filter(detected -> tuneIds.contains(detected.tuneId()))
                .sort(Comparator.comparingDouble(DetectedTune::start));
    }
}

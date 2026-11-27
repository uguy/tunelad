package org.tunelad.identification.internal;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
    public Flux<DetectedTune> detectAny(Flux<DataBuffer> audio) {
        return request(audio, null).sort(Comparator.comparingDouble(DetectedTune::start));
    }

    private Flux<DetectedTune> request(Flux<DataBuffer> audio, Collection<String> tuneIds) {
        return client.post()
                .uri(uri -> {
                    uri.path("/identify");
                    if (tuneIds != null) {
                        uri.queryParam("tunes", String.join(",", tuneIds));
                    }
                    return uri.build();
                })
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(audio, DataBuffer.class)
                .retrieve()
                .bodyToFlux(DetectedTune.class)
                // The sidecar explains a refusal in its body: without it the log only says "400 Bad Request"
                .onErrorMap(WebClientResponseException.class, CadenceSource::explained)
                .timeout(properties.timeout());
    }

    private static final int MAX_EXPLANATION = 300;

    static IllegalStateException explained(WebClientResponseException error) {
        var body = error.getResponseBodyAsString().strip();
        var message = "analysis service answered " + error.getStatusCode().value();
        if (!body.isEmpty()) {
            message += ": " + (body.length() > MAX_EXPLANATION ? body.substring(0, MAX_EXPLANATION) + "..." : body);
        }
        return new IllegalStateException(message, error);
    }

    @Override
    public Flux<DetectedTune> detect(Flux<DataBuffer> audio, Collection<String> tuneIds) {
        if (tuneIds.isEmpty()) {
            return Flux.empty();
        }
        return request(audio, tuneIds)
                // Whatever the sidecar says, only the Tunes asked for are reported, in time order
                .filter(detected -> tuneIds.contains(detected.tuneId()))
                .sort(Comparator.comparingDouble(DetectedTune::start));
    }
}

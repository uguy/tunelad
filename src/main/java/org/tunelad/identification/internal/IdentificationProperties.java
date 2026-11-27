package org.tunelad.identification.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where the analysis sidecar runs.
 *
 * @param url           its base URL
 * @param timeout       how long one analysis may take (the sidecar needs about 5 % of the audio's duration)
 * @param minConfidence the lowest confidence at which a Tune heard without being asked for is kept
 */
@ConfigurationProperties("tunelad.identification")
public record IdentificationProperties(@DefaultValue("http://localhost:8090") String url,
                                       @DefaultValue("10m") Duration timeout,
                                       @DefaultValue("0.5") double minConfidence) {
}

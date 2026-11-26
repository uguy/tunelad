package org.tunelad.live.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * How live UI updates travel: through NATS between application instances, then to browsers over Server-Sent Events.
 *
 * @param nats      the NATS server the instances share
 * @param heartbeat how often an idle event stream receives a comment line, so proxies do not close it
 */
@ConfigurationProperties("tunelad.live")
public record LiveProperties(@DefaultValue Nats nats, @DefaultValue("15s") Duration heartbeat) {

    /**
     * @param url where to reach NATS, such as {@code nats://localhost:4222}
     */
    public record Nats(@DefaultValue("nats://localhost:4222") String url) {
    }
}

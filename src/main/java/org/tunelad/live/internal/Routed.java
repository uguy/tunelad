package org.tunelad.live.internal;

/**
 * A live event on its way to the connections it is meant for.
 *
 * @param audience who may receive it
 * @param type     the event type, which names the event on the stream
 * @param body     the JSON the stream sends as the event's data, forwarded as published
 */
public record Routed(Audience audience, String type, String body) {
}

package org.tunelad.live.internal;

import java.util.function.BiConsumer;

/** The messaging between application instances that live events travel on. Delivery is best effort. */
public interface LiveBus {

    /** Publishes the message on the subject. Never throws: a message that cannot be sent is dropped. */
    void publish(String subject, String body);

    /** Calls the handler with the subject and body of every message published on a matching subject, by any instance. */
    void subscribe(String subject, BiConsumer<String, String> handler);
}

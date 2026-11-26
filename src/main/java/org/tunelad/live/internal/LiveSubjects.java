package org.tunelad.live.internal;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * The NATS subjects live events travel on: {@code tunelad.library.<type>} for everyone, and
 * {@code tunelad.account.<accountKey>.<type>} for one account. The event type is dotted ({@code waveform.computed}).
 * Ids never go in a subject: they are arbitrary strings, and a {@code .}, {@code *}, {@code >} or space would break
 * it. They are in the body.
 */
public final class LiveSubjects {

    /** Matches every live subject. */
    static final String ALL = "tunelad.>";

    private static final String LIBRARY_PREFIX = "tunelad.library.";
    private static final String ACCOUNT_PREFIX = "tunelad.account.";

    private LiveSubjects() {
    }

    /** The subject of an event for everyone. */
    public static String library(String type) {
        return LIBRARY_PREFIX + type;
    }

    /** The subject of an event for the account with that id. */
    public static String account(String accountId, String type) {
        return ACCOUNT_PREFIX + accountKey(accountId) + "." + type;
    }

    /**
     * The account's id as it appears in a subject: account ids are arbitrary strings, so they are encoded
     * (unpadded base64url, which has no {@code .}, {@code *}, {@code >} or space).
     */
    public static String accountKey(String accountId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(accountId.getBytes(StandardCharsets.UTF_8));
    }

    /** Where an event published on the subject goes, or empty when the subject is none of ours. */
    static Optional<Routed> route(String subject, @Nullable String body) {
        if (body == null) {
            return Optional.empty();
        }
        if (subject.startsWith(LIBRARY_PREFIX)) {
            var type = subject.substring(LIBRARY_PREFIX.length());
            return type.isEmpty() ? Optional.empty() : Optional.of(new Routed(Audience.LIBRARY, type, body));
        }
        if (subject.startsWith(ACCOUNT_PREFIX)) {
            var rest = subject.substring(ACCOUNT_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot <= 0 || dot == rest.length() - 1) {
                return Optional.empty();
            }
            return Optional.of(new Routed(new Audience(rest.substring(0, dot)), rest.substring(dot + 1), body));
        }
        return Optional.empty();
    }
}

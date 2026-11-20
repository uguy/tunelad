package org.tunelad.account.internal;

import java.util.List;
import java.util.Map;

/** Picks the name to show for an identity from its claims. */
final class DisplayNames {

    private static final List<String> CLAIMS_BY_PREFERENCE = List.of("name", "preferred_username", "email");

    private DisplayNames() {
    }

    /** The full name if present, otherwise the preferred username, otherwise the email, otherwise the subject. */
    static String of(Map<String, Object> claims, String subject) {
        return CLAIMS_BY_PREFERENCE.stream()
                .map(claims::get)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(subject);
    }
}

package org.tunelad.account.internal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayNamesTest {

    @Test
    void prefers_the_full_name() {
        var claims = Map.<String, Object>of("name", "Alice Liddell", "preferred_username", "alice", "email", "a@x.test");

        assertThat(DisplayNames.of(claims, "sub")).isEqualTo("Alice Liddell");
    }

    @Test
    void falls_back_to_the_username() {
        assertThat(DisplayNames.of(Map.of("preferred_username", "carol"), "sub")).isEqualTo("carol");
    }

    @Test
    void falls_back_to_the_email() {
        assertThat(DisplayNames.of(Map.of("email", "dave@tunelad.test"), "sub")).isEqualTo("dave@tunelad.test");
    }

    @Test
    void falls_back_to_the_subject() {
        assertThat(DisplayNames.of(Map.of(), "sub-erin")).isEqualTo("sub-erin");
    }

    @Test
    void ignores_blank_claims() {
        assertThat(DisplayNames.of(Map.of("name", "  ", "preferred_username", "carol"), "sub")).isEqualTo("carol");
    }
}

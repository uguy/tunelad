package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSubjectsTest {

    private static final String BODY = "{\"type\":\"track.indexed\",\"id\":\"42\"}";

    @Test
    void a_library_subject_is_for_everyone_and_names_the_type() {
        var routed = LiveSubjects.route(LiveSubjects.library("track.indexed"), BODY).orElseThrow();

        assertThat(LiveSubjects.library("track.indexed")).isEqualTo("tunelad.library.track.indexed");
        assertThat(routed).isEqualTo(new Routed(Audience.LIBRARY, "track.indexed", BODY));
    }

    @Test
    void an_account_subject_is_for_that_account_and_names_the_type() {
        var routed = LiveSubjects.route(LiveSubjects.account("alice", "favourites.changed"), BODY).orElseThrow();

        assertThat(routed.audience()).isEqualTo(new Audience(LiveSubjects.accountKey("alice")));
        assertThat(routed.type()).isEqualTo("favourites.changed");
        assertThat(routed.body()).isEqualTo(BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "https://idp.test/realms/tunelad|a.b.c", "a*b", "a>b", "with space", "tab\there", "ünï", ""})
    void an_account_id_never_breaks_the_subject(String accountId) {
        var subject = LiveSubjects.account(accountId, "favourites.changed");

        assertThat(LiveSubjects.accountKey(accountId)).doesNotContain(".", "*", ">", " ", "=");
        if (!accountId.isEmpty()) {
            assertThat(LiveSubjects.route(subject, BODY).orElseThrow().audience())
                    .isEqualTo(new Audience(LiveSubjects.accountKey(accountId)));
        }
    }

    @Test
    void different_account_ids_have_different_keys() {
        assertThat(LiveSubjects.accountKey("a.b")).isNotEqualTo(LiveSubjects.accountKey("a_b"));
        assertThat(LiveSubjects.accountKey("alice")).isNotEqualTo(LiveSubjects.accountKey("bob"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "tunelad", "tunelad.", "tunelad.library", "tunelad.library.", "tunelad.account.",
            "tunelad.account.key", "tunelad.account.key.", "tunelad.account..type", "tunelad.other.thing",
            "other.library.track.indexed"})
    void a_subject_that_is_none_of_ours_goes_nowhere(String subject) {
        assertThat(LiveSubjects.route(subject, BODY)).isEmpty();
    }

    @Test
    void a_message_without_a_body_goes_nowhere() {
        assertThat(LiveSubjects.route(LiveSubjects.library("track.indexed"), null)).isEmpty();
    }

    @Test
    void the_body_is_forwarded_as_it_was_published() {
        var odd = "not even json {";

        assertThat(LiveSubjects.route(LiveSubjects.library("track.indexed"), odd).orElseThrow().body()).isEqualTo(odd);
    }

    @Test
    void the_library_is_for_everyone_and_an_account_only_for_itself() {
        var alice = new Audience(LiveSubjects.accountKey("alice"));

        assertThat(Audience.LIBRARY.admits(null)).isTrue();
        assertThat(Audience.LIBRARY.admits(LiveSubjects.accountKey("bob"))).isTrue();
        assertThat(alice.admits(LiveSubjects.accountKey("alice"))).isTrue();
        assertThat(alice.admits(LiveSubjects.accountKey("bob"))).isFalse();
        assertThat(alice.admits(null)).isFalse();
    }
}

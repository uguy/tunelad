package org.tunelad.live.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Routing on its own: no NATS, the bus is a stand-in that hands over what the test "publishes". */
class LiveRouterTest {

    private static final String ALICE = "alice-id.with.dots*and>stars";
    private static final String BOB = "bob";

    private String subscribedTo;
    private BiConsumer<String, String> deliver;
    private LiveRouter router;

    @BeforeEach
    void routerOnAStandInBus() {
        router = new LiveRouter(new LiveBus() {
            @Override
            public void publish(String subject, String body) {
                deliver.accept(subject, body);
            }

            @Override
            public void subscribe(String subject, BiConsumer<String, String> handler) {
                subscribedTo = subject;
                deliver = handler;
            }
        });
    }

    @Test
    void listens_to_every_live_subject_once() {
        assertThat(subscribedTo).isEqualTo("tunelad.>");
    }

    @Test
    void a_library_event_reaches_anonymous_and_logged_in_viewers() {
        var anonymous = new CopyOnWriteArrayList<String>();
        var alice = new CopyOnWriteArrayList<String>();
        router.stream(null).subscribe(routed -> anonymous.add(routed.type()));
        router.stream(LiveSubjects.accountKey(ALICE)).subscribe(routed -> alice.add(routed.type()));

        deliver.accept(LiveSubjects.library("track.indexed"), "{}");

        assertThat(anonymous).containsExactly("track.indexed");
        assertThat(alice).containsExactly("track.indexed");
    }

    @Test
    void an_account_event_reaches_only_that_account() {
        var alice = LiveSubjects.accountKey(ALICE);

        StepVerifier.create(router.stream(alice))
                .then(() -> {
                    deliver.accept(LiveSubjects.account(BOB, "favourites.changed"), "for bob");
                    deliver.accept(LiveSubjects.account(ALICE, "favourites.changed"), "for alice");
                })
                .assertNext(routed -> assertThat(routed.body()).isEqualTo("for alice"))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void an_anonymous_viewer_gets_no_account_event() {
        StepVerifier.create(router.stream(null))
                .then(() -> {
                    deliver.accept(LiveSubjects.account(ALICE, "favourites.changed"), "for alice");
                    deliver.accept(LiveSubjects.library("track.indexed"), "for everyone");
                })
                .assertNext(routed -> assertThat(routed.body()).isEqualTo("for everyone"))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void a_message_on_another_subject_is_ignored() {
        StepVerifier.create(router.stream(null))
                .then(() -> {
                    deliver.accept("other.subject", "ignored");
                    deliver.accept("tunelad.library.", "ignored");
                    deliver.accept("tunelad.account.nokey", "ignored");
                    deliver.accept(LiveSubjects.library("track.indexed"), "kept");
                })
                .assertNext(routed -> assertThat(routed.body()).isEqualTo("kept"))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void an_event_nobody_follows_is_dropped_without_error() {
        deliver.accept(LiveSubjects.library("track.indexed"), "{}");

        StepVerifier.create(router.stream(null).take(Duration.ofMillis(100))).verifyComplete();
    }

    @Test
    void a_viewer_that_stopped_reading_does_not_hold_up_another() {
        // Subscribed, never asks for anything
        router.stream(null).subscribe(new reactor.core.publisher.BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
            }
        });

        StepVerifier.create(router.stream(null).take(3))
                .then(() -> {
                    for (int i = 0; i < 3; i++) {
                        deliver.accept(LiveSubjects.library("track.indexed"), "event " + i);
                    }
                })
                .expectNextCount(3)
                .verifyComplete();
    }
}

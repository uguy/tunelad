package org.tunelad.identification.internal;

import org.junit.jupiter.api.Test;
import org.tunelad.identification.DetectedTune;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SetDerivationTest {

    private static DetectedTune heard(String id, double start, double confidence) {
        return new DetectedTune(id, start, start + 60, confidence);
    }

    @Test
    void keeps_the_tunes_in_the_order_first_heard() {
        var entries = SetDerivation.derive(List.of(heard("2", 95, 0.7), heard("1", 0, 0.8)), 0.5);

        assertThat(entries).containsExactly(new SetDerivation.Entry("1", 0), new SetDerivation.Entry("2", 95));
    }

    @Test
    void ignores_weak_detections() {
        var entries = SetDerivation.derive(List.of(heard("1", 0, 0.8), heard("2", 95, 0.2)), 0.5);

        assertThat(entries).containsExactly(new SetDerivation.Entry("1", 0));
    }

    @Test
    void a_confidence_equal_to_the_minimum_is_kept() {
        assertThat(SetDerivation.derive(List.of(heard("1", 0, 0.5)), 0.5)).hasSize(1);
    }

    @Test
    void merges_consecutive_detections_of_a_tune_keeping_the_first_start() {
        var entries = SetDerivation.derive(List.of(heard("1", 0, 0.8), heard("1", 20, 0.9), heard("2", 95, 0.7)), 0.5);

        assertThat(entries).containsExactly(new SetDerivation.Entry("1", 0), new SetDerivation.Entry("2", 95));
    }

    @Test
    void a_weak_detection_between_does_not_split_a_tune() {
        var entries = SetDerivation.derive(List.of(heard("1", 0, 0.8), heard("2", 10, 0.1), heard("1", 20, 0.9)), 0.5);

        assertThat(entries).containsExactly(new SetDerivation.Entry("1", 0));
    }

    @Test
    void keeps_a_tune_heard_again_after_another() {
        var entries = SetDerivation.derive(List.of(heard("1", 0, 0.8), heard("2", 60, 0.8), heard("1", 120, 0.8)), 0.5);

        assertThat(entries).extracting(SetDerivation.Entry::sourceId).containsExactly("1", "2", "1");
    }

    @Test
    void keeps_the_first_twenty() {
        var all = IntStream.range(0, 25).mapToObj(i -> heard(String.valueOf(i), i * 10, 0.9)).toList();

        var entries = SetDerivation.derive(all, 0.5);

        assertThat(entries).hasSize(20);
        assertThat(entries.getLast().sourceId()).isEqualTo("19");
    }

    @Test
    void nothing_heard() {
        assertThat(SetDerivation.derive(List.of(), 0.5)).isEmpty();
    }
}

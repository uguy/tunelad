package org.tunelad.tune.event;

/** A Tune was created or imported; {@code tuneId} is null when a bulk import of many Tunes ended. */
public record TuneChanged(String tuneId) {
}

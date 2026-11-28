package org.tunelad.track.command;

import jakarta.validation.constraints.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

/**
 * Gives a Catalogue Track its audio, which turns it into an ordinary Track owned by {@code owner}. The Track keeps its
 * title, Album, position, Performances and Set.
 */
public record AttachAudio(String trackId, Publisher<DataBuffer> data, TrackFormat format, @NotNull TrackOwner owner) implements TrackCommand {
}

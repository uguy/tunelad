package org.tunelad.track.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

import java.util.List;

public record AddTrackCommand(
        /** Null or blank: the track takes {@code fileName} without its extension as its title. */
        @Size(max = 80)
        String title,
        /** The uploaded file's name, which titles a track that was given no title. */
        String fileName,
        String artist,
        String album,
        String description,
        /** Null: the track has no Set yet, and so no Performances. */
        String setId,
        /** One per Tune of the Set, in order; null gives every Performance the defaults. */
        List<PerformanceInput> performances,
        Publisher<DataBuffer> data,
        TrackFormat format,
        @NotNull TrackOwner owner) implements TrackCommand {
}

package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;

import java.util.List;

public record AddTrackCommand(
        @NotBlank(message = "A track needs a title.")
        @Size(min = 1, max = 80)
        String title,
        String artist,
        String album,
        String description,
        @NotBlank(message = "A track needs a set.") String setId,
        /** One per Tune of the Set, in order; null gives every Performance the defaults. */
        List<PerformanceInput> performances,
        Publisher<DataBuffer> data,
        TrackFormat format,
        @NotNull TrackOwner owner) implements TrackCommand {
}

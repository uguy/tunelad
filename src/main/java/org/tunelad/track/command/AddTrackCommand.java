package org.tunelad.track.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.tunelad.track.TrackFormat;

public record AddTrackCommand(
        @NotBlank(message = "A track needs a title.")
        @Size(min = 1, max = 80)
        String title,
        String artist,
        String album,
        String description,
        Publisher<DataBuffer> data,
        TrackFormat format) implements TrackCommand {
}

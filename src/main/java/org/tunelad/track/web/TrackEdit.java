package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.tunelad.track.command.PerformanceInput;

import java.util.List;

@Schema(description = "A track's new metadata: replaces all of it, so an omitted description, artist or album is cleared")
public record TrackEdit(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Knees Up (live)")
        @NotBlank(message = "A track needs a title.")
        String title,
        @Schema(example = "Recorded at Celtic Connections")
        String description,
        @Schema(example = "Breabach")
        String artist,
        @Schema(example = "Live")
        String album,
        @Schema(description = "One entry per performance of the track, in order, replacing their start offset, key and notes; "
                + "omitted leaves them unchanged. The set and the order of the performances never change.")
        List<PerformanceInput> performances) {
}

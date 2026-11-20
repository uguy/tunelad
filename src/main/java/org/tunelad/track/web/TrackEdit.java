package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "A track's new metadata: replaces all of it, so an omitted artist or album is cleared")
public record TrackEdit(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Knees Up (live)")
        @NotBlank(message = "A track needs a title.")
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Recorded at Celtic Connections")
        @NotBlank(message = "A track needs a description.")
        String description,
        @Schema(example = "Breabach")
        String artist,
        @Schema(example = "Live")
        String album) {
}

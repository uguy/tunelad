package org.tunelad.media.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.media.internal.RenditionStatus;

@Schema(name = "Audio", description = "Whether a track's audio can be played")
public record AudioResponse(

        @Schema(description = "READY when the audio can be played (always, for formats browsers play), PENDING while "
                + "its AAC rendition is being made, FAILED when it could not be made",
                requiredMode = Schema.RequiredMode.REQUIRED)
        RenditionStatus status
) {
}

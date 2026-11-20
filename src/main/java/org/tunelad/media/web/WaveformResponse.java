package org.tunelad.media.web;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.media.internal.Peaks;
import org.tunelad.media.internal.Waveform;
import org.tunelad.media.internal.WaveformStatus;

import java.util.List;

@Schema(name = "Waveform", description = "The waveform of a track's audio, to draw it in a player")
public record WaveformResponse(

        @Schema(description = "PENDING while it is being computed, READY once it has its peaks, FAILED when the "
                + "audio could not be decoded", requiredMode = Schema.RequiredMode.REQUIRED)
        WaveformStatus status,

        @ArraySchema(arraySchema = @Schema(description = "The loudness of the audio along the track, in playback "
                + "order: exactly " + Peaks.COUNT + " values from 0 to 1 (the loudest being 1) when READY, none "
                + "otherwise", requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(minimum = "0", maximum = "1"), maxItems = Peaks.COUNT)
        List<Double> peaks
) {

    static WaveformResponse of(Waveform waveform) {
        return new WaveformResponse(waveform.status(), waveform.peaks());
    }
}

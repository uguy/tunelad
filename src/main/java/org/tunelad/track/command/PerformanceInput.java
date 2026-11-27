package org.tunelad.track.command;

import io.swagger.v3.oas.annotations.media.Schema;
import org.tunelad.infrastructure.BadRequestException;

/** What an Account says of one Performance: its start offset, key, notes and transcription. Every value is optional. */
@Schema(description = "A performance's start offset, key, notes and transcription; all optional")
public record PerformanceInput(
        @Schema(description = "Where the tune starts in the audio, in seconds; at least 0, default 0", example = "95.5")
        Double startOffsetSeconds,
        @Schema(description = "The key, at most 30 characters", example = "Em")
        String key,
        @Schema(description = "Notes on melodic variation, at most 2000 characters")
        String notes,
        @Schema(description = "The transcription, ABC notation of at most 20000 characters; omitted or blank clears it")
        String abc) {

    public static final int MAX_KEY = 30;
    public static final int MAX_NOTES = 2000;
    public static final int MAX_ABC = 20_000;
    public static final double MAX_OFFSET = 86_400;

    public static final PerformanceInput DEFAULT = new PerformanceInput(null, null, null, null);

    public double offset() {
        return startOffsetSeconds == null ? 0 : startOffsetSeconds;
    }

    /** The key without surrounding whitespace, null when blank. */
    public String cleanKey() {
        return blankToNull(key);
    }

    public String cleanNotes() {
        return blankToNull(notes);
    }

    /** The transcription without surrounding whitespace, null when blank. */
    public String cleanAbc() {
        return blankToNull(abc);
    }

    /** Fails with {@link BadRequestException} unless the values are within their limits. */
    public void validate() {
        if (startOffsetSeconds != null && (startOffsetSeconds.isNaN() || startOffsetSeconds < 0 || startOffsetSeconds > MAX_OFFSET)) {
            throw new BadRequestException("A start offset is between 0 and " + (long) MAX_OFFSET + " seconds.");
        }
        if (cleanKey() != null && cleanKey().length() > MAX_KEY) {
            throw new BadRequestException("A key has at most " + MAX_KEY + " characters.");
        }
        if (cleanNotes() != null && cleanNotes().length() > MAX_NOTES) {
            throw new BadRequestException("Notes have at most " + MAX_NOTES + " characters.");
        }
        if (cleanAbc() != null && cleanAbc().length() > MAX_ABC) {
            throw new BadRequestException("A transcription has at most " + MAX_ABC + " characters.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

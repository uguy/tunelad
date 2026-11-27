package org.tunelad.track;

/** Where a Performance stands in Identification. */
public enum IdentificationState {
    /** Nothing has been proposed for it. */
    PENDING,
    /** Proposed by the analysis of the audio; a later run may replace it. */
    IDENTIFIED,
    /** Edited by an Account: no analysis overwrites it. */
    CORRECTED;

    public String column() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static IdentificationState fromColumn(String value) {
        return valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}

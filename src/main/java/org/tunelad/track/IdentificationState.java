package org.tunelad.track;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Where a Performance stands in Identification. The value is the name used by the API and the database. */
public enum IdentificationState {
    /** Not analysed yet, or the analysis could not be done. */
    PENDING("pending"),
    /** Proposed by the analysis of the audio; a later run may replace it. */
    IDENTIFIED("identified"),
    /** An analysis finished without hearing its Tune. A later run may still find it. */
    NOTHING_FOUND("nothing_found"),
    /** Edited by an Account: no analysis overwrites it. */
    CORRECTED("corrected");

    private final String value;

    IdentificationState(String value) {
        this.value = value;
    }

    @JsonValue
    public String column() {
        return value;
    }

    @JsonCreator
    public static IdentificationState fromColumn(String value) {
        return Arrays.stream(values()).filter(state -> state.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown identification state: " + value));
    }
}

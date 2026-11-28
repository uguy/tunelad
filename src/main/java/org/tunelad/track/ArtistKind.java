package org.tunelad.track;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** What an Artist is: a person or a group of people. The value is the name used by the API and the database. */
public enum ArtistKind {
    MUSICIAN("musician"),
    BAND("band");

    private final String value;

    ArtistKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String column() {
        return value;
    }

    @JsonCreator
    public static ArtistKind fromColumn(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values()).filter(kind -> kind.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown artist kind: " + value));
    }
}

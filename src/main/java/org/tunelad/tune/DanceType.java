package org.tunelad.tune;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.tunelad.infrastructure.BadRequestException;

import java.util.Arrays;
import java.util.Optional;

/** The kind of a Tune. The value is the name used by the API and by thesession.org; the meter feeds the ABC header. */
public enum DanceType {
	JIG("jig", "6/8"),
	REEL("reel", "4/4"),
	HORNPIPE("hornpipe", "4/4"),
	SLIDE("slide", "12/8"),
	POLKA("polka", "2/4"),
	SLIP_JIG("slip jig", "9/8"),
	WALTZ("waltz", "3/4"),
	BARNDANCE("barndance", "4/4"),
	STRATHSPEY("strathspey", "4/4"),
	MARCH("march", "4/4"),
	MAZURKA("mazurka", "3/4"),
	THREE_TWO("three-two", "3/2");

	private final String value;
	private final String meter;

	DanceType(String value, String meter) {
		this.value = value;
		this.meter = meter;
	}

	@JsonValue
	public String value() {
		return value;
	}

	public String meter() {
		return meter;
	}

	public static Optional<DanceType> fromValue(String value) {
		return Arrays.stream(values()).filter(type -> type.value.equalsIgnoreCase(value == null ? "" : value.trim())).findFirst();
	}

	@JsonCreator
	public static DanceType parse(String value) {
		return fromValue(value).orElseThrow(() -> new BadRequestException("Unknown dance type: " + value));
	}
}

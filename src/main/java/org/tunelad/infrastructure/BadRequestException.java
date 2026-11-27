package org.tunelad.infrastructure;

/** The request is well formed but asks for something that cannot be done: answered with 400 Bad Request. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}

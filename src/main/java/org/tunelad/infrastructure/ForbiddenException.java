package org.tunelad.infrastructure;

/** The caller is known but may not do this: answered with 403 Forbidden. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}

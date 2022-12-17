package org.tunelad.infrastructure;

public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("Entity not found");
	}
	public NotFoundException(String message) {
		super(message);
	}
}

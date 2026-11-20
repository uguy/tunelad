package org.tunelad.infrastructure.web.config;

import org.tunelad.infrastructure.ForbiddenException;
import org.tunelad.infrastructure.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionTranslator {

	@ExceptionHandler({ NotFoundException.class })
	public final ResponseEntity handleException() {
		return ResponseEntity.notFound().build();
	}

	@ExceptionHandler({ ForbiddenException.class })
	public final ResponseEntity handleForbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}
}

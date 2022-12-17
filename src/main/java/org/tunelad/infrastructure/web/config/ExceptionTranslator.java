package org.tunelad.infrastructure.web.config;

import org.tunelad.infrastructure.NotFoundException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class ExceptionTranslator {

	@ExceptionHandler({ NotFoundException.class })
	public final ResponseEntity handleException() {
		return ResponseEntity.notFound().build();
	}
}

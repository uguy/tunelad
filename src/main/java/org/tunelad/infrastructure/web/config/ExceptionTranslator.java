package org.tunelad.infrastructure.web.config;

import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.ForbiddenException;
import org.tunelad.infrastructure.NotFoundException;

import org.springframework.core.io.buffer.DataBufferLimitException;
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

	/** A request body over a codec limit, such as an uploaded file over the multipart disk usage limit per part. */
	@ExceptionHandler({ DataBufferLimitException.class })
	public final ResponseEntity handleTooLarge() {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
	}

	@ExceptionHandler({ BadRequestException.class })
	public final ResponseEntity handleBadRequest() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}

	/** Two changes raced for the same unique value, such as a playlist position: the loser may try again. */
	@ExceptionHandler({ org.springframework.dao.DuplicateKeyException.class })
	public final ResponseEntity handleConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).build();
	}

	@ExceptionHandler({ ForbiddenException.class })
	public final ResponseEntity handleForbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}
}

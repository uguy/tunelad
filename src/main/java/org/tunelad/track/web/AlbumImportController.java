package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.track.internal.AlbumDumpImport;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Tag(name = "Albums")
@RequestMapping(value = "api/albums/import-dump", produces = MediaType.APPLICATION_JSON_VALUE)
public class AlbumImportController {

	private final AlbumDumpImport dumpImport;
	private final CurrentAccount currentAccount;

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Import the thesession.org recordings dump",
			description = "Administrators only. Downloads recordings.csv from adactio/TheSession-data and creates the Albums, with their Catalogue Tracks and Performances, "
					+ "that the collection lacks. Artists are found by name or created unclassified. A tune whose thesession.org id is in the Tune catalogue is resolved; "
					+ "otherwise its Performance stays unresolved with the listed name (Tunes are never created: run the Tune import first). A Track gets a Set only when all its tunes resolve. "
					+ "What exists already is left unchanged, so running it again resumes an interrupted run. The import runs in the background: read its progress with GET.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "202", description = "The import started")
	@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	@ApiResponse(responseCode = "403", description = "Not an administrator, or a browser session request without a valid CSRF token", content = @Content)
	@ApiResponse(responseCode = "409", description = "An import is running already", content = @Content)
	public Mono<AlbumDumpImport.Run> start() {
		return administrator().map(account -> {
			if (!dumpImport.start(account)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "An import is running already");
			}
			return dumpImport.last();
		});
	}

	@GetMapping
	@Operation(summary = "Progress of the thesession.org recordings import",
			description = "Administrators only: the state of the last import (idle, running, done or failed), the Albums and Tracks it created, the rows it skipped, and why it failed.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "200", description = "The last import")
	@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	@ApiResponse(responseCode = "403", description = "Not an administrator", content = @Content)
	public Mono<AlbumDumpImport.Run> progress() {
		return administrator().map(account -> dumpImport.last());
	}

	private Mono<AccountRef> administrator() {
		return currentAccount.get().switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
				.filter(AccountRef::admin).switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.FORBIDDEN)));
	}
}

package org.tunelad.tune.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.tune.DanceType;
import org.tunelad.tune.TuneDTO;
import org.tunelad.tune.internal.TuneService;
import org.tunelad.tune.internal.TuneSource.SourceUnavailableException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@RestController
@RequiredArgsConstructor
@Tag(name = "Tunes", description = "Traditional tunes, with their dance type and reference transcription")
public class TuneController {

	/** What an import needs: the thesession.org tune id. */
	@Schema(description = "A tune to import from thesession.org")
	public record ImportRequest(@Schema(description = "thesession.org tune id", requiredMode = Schema.RequiredMode.REQUIRED) Integer id) {}

	private final TuneService tunes;
	private final CurrentAccount currentAccount;

	@GetMapping(value = "api/dance-types", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "List the dance types", description = "The fixed list of kinds a tune can have.")
	@ApiResponse(responseCode = "200", description = "The dance types",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = DanceType.class))))
	public Flux<DanceType> danceTypes() {
		return Flux.fromArray(DanceType.values());
	}

	@GetMapping(value = "api/tunes", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "List tunes", description = "Tunes ordered by name, optionally of one dance type and matching a text in the name or an alias.")
	@ApiResponse(responseCode = "200", description = "The tunes",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = TuneDTO.class))))
	@ApiResponse(responseCode = "400", description = "Unknown dance type", content = @Content)
	public Flux<TuneDTO> list(@RequestParam(required = false) String danceType, @RequestParam(required = false) String q) {
		return tunes.list(danceType == null || danceType.isBlank() ? null : DanceType.parse(danceType), q);
	}

	@GetMapping(value = "api/tunes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get a tune")
	@ApiResponse(responseCode = "200", description = "The tune")
	@ApiResponse(responseCode = "404", description = "No such tune", content = @Content)
	public Mono<TuneDTO> get(@PathVariable String id) {
		return tunes.findById(id);
	}

	@PostMapping(value = "api/tunes", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a tune by hand",
			description = "For a tune thesession.org does not have. The id and sourceId of the body are ignored.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "201", description = "The tune was created")
	@ApiResponse(responseCode = "400", description = "No name, or unknown dance type", content = @Content)
	@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	@ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
	public Mono<TuneDTO> create(@RequestBody TuneDTO tune) {
		return account().flatMap(account -> tunes.create(account, tune));
	}

	@PostMapping(value = "api/tunes/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Import a tune from thesession.org",
			description = "Importing an id that was already imported answers with the existing tune.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "201", description = "The tune was imported")
	@ApiResponse(responseCode = "200", description = "The tune had been imported already")
	@ApiResponse(responseCode = "400", description = "No id", content = @Content)
	@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	@ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
	@ApiResponse(responseCode = "404", description = "thesession.org does not know this id", content = @Content)
	@ApiResponse(responseCode = "502", description = "thesession.org is unreachable", content = @Content)
	public Mono<ResponseEntity<TuneDTO>> importTune(@RequestBody ImportRequest request) {
		if (request.id() == null) {
			return Mono.just(ResponseEntity.badRequest().build());
		}
		return account().flatMap(account -> tunes.importTune(account, request.id()))
				.map(imported -> ResponseEntity.status(imported.created() ? HttpStatus.CREATED : HttpStatus.OK).body(imported.tune()))
				.onErrorMap(SourceUnavailableException.class, e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage()));
	}

	private Mono<AccountRef> account() {
		return currentAccount.get().switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
	}
}

package org.tunelad.set.web;

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
import org.tunelad.set.SetDTO;
import org.tunelad.set.internal.SetService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Sets", description = "Ordered lists of tunes, one set per distinct list")
public class SetController {

	/** The Tunes of the Set to create, in order. */
	@Schema(description = "The set to create")
	public record CreateRequest(
			@Schema(description = "One to twenty tune ids, in order; a tune may repeat", requiredMode = Schema.RequiredMode.REQUIRED)
			List<String> tuneIds) {}

	private final SetService sets;
	private final CurrentAccount currentAccount;

	@PostMapping(value = "api/sets", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Create a set",
			description = "There is one set per distinct ordered list of tunes: a list that has a set already answers with it.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "201", description = "The set was created")
	@ApiResponse(responseCode = "200", description = "The set existed already")
	@ApiResponse(responseCode = "400", description = "An empty or too long list, or an unknown tune", content = @Content)
	@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	@ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
	public Mono<ResponseEntity<SetDTO>> create(@RequestBody CreateRequest request) {
		return account().flatMap(account -> sets.create(account, request.tuneIds()))
				.map(created -> ResponseEntity.status(created.created() ? HttpStatus.CREATED : HttpStatus.OK).body(created.set()));
	}

	@GetMapping(value = "api/sets", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "List sets", description = "Sets ordered by the names of their tunes, optionally only those with one tune.")
	@ApiResponse(responseCode = "200", description = "The sets",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = SetDTO.class))))
	public Flux<SetDTO> list(@RequestParam(required = false) String tuneId) {
		return sets.list(tuneId);
	}

	@GetMapping(value = "api/sets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get a set")
	@ApiResponse(responseCode = "200", description = "The set")
	@ApiResponse(responseCode = "404", description = "No such set", content = @Content)
	public Mono<SetDTO> get(@PathVariable String id) {
		return sets.findById(id);
	}

	private Mono<AccountRef> account() {
		return currentAccount.get().switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
	}
}

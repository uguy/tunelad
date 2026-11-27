package org.tunelad.identification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.identification.internal.IdentificationService;
import org.tunelad.infrastructure.ForbiddenException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.track.TrackService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
@Tag(name = "Identification", description = "Identification of the tunes played in a track")
@RequestMapping(value = "api/tracks/{id}/identification", produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentificationController {

    private final IdentificationService identification;

    private final TrackService tracks;

    private final CurrentAccount currentAccount;

    IdentificationController(IdentificationService identification, TrackService tracks, CurrentAccount currentAccount) {
        this.identification = identification;
        this.tracks = tracks;
        this.currentAccount = currentAccount;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Run identification again",
            description = "Hears the track's audio again and proposes the start of each performance. Performances "
                    + "an account corrected are never changed. Runs in the background: the response does not wait "
                    + "for it. Only the track's owner or an administrator may ask.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Identification started", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not the track's owner or an administrator, or a browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Mono<Void> run(@Parameter(description = "Track ID", required = true) @PathVariable String id) {
        return currentAccount.get()
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .flatMap(account -> tracks.findById(id)
                        .switchIfEmpty(Mono.error(() -> new NotFoundException("No track found for id: " + id)))
                        .doOnNext(track -> {
                            if (!mayRun(track.owner() == null ? null : track.owner().id(), account)) {
                                throw new ForbiddenException("Account " + account.id() + " may not change track " + id);
                            }
                        }))
                // Not awaited: the analysis takes a while and the answer is only that it started
                .doOnNext(track -> identification.identify(id)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(ignored -> { }, error -> log.warn("Identification of track {} failed: {}", id, error.toString())))
                .then();
    }

    private static boolean mayRun(String ownerId, AccountRef account) {
        return account.admin() || (ownerId != null && ownerId.equals(account.id()));
    }
}

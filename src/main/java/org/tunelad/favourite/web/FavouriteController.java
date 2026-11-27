package org.tunelad.favourite.web;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.favourite.internal.FavouriteService;
import org.tunelad.track.TrackDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Tag(name = "Favourites", description = "The authenticated user's favourite tracks")
public class FavouriteController {

    private final FavouriteService favourites;
    private final CurrentAccount currentAccount;

    @PutMapping("api/tracks/{id}/favourite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark a favourite",
            description = "Adds the track to the user's favourites. Idempotent: marking a favourite again changes nothing.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "The track is a favourite")
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such track", content = @Content)
    public Mono<Void> mark(@PathVariable String id) {
        return account().flatMap(account -> favourites.mark(account, id));
    }

    @DeleteMapping("api/tracks/{id}/favourite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unmark a favourite",
            description = "Removes the track from the user's favourites. Idempotent: unmarking a track that is not a favourite changes nothing.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "The track is not a favourite")
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
    public Mono<Void> unmark(@PathVariable String id) {
        return account().flatMap(account -> favourites.unmark(account, id));
    }

    @GetMapping(value = "api/me/favourites", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "My favourite tracks",
            description = "Returns the user's favourite tracks, most recently marked first.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The user's favourite tracks",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TrackDTO.class))))
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    public Flux<TrackDTO> list() {
        return account().flatMapMany(favourites::list);
    }

    /** The current account: security already answered 401 to anonymous requests, so empty is not expected. */
    private Mono<AccountRef> account() {
        return currentAccount.get().switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }
}

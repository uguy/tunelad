package org.tunelad.playlist.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.playlist.PlaylistDTO;
import org.tunelad.playlist.PlaylistInput;
import org.tunelad.playlist.PlaylistSummary;
import org.tunelad.playlist.internal.PlaylistService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Playlists", description = "The authenticated user's private playlists")
@RequestMapping(value = "api/playlists", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlaylistController {

    private static final String BEARER = "bearerAuth";

    private final PlaylistService playlists;
    private final CurrentAccount currentAccount;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a playlist", description = "Creates an empty playlist owned by the user.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "201", description = "Playlist created, at the URL in `Location`",
            content = @Content(schema = @Schema(implementation = PlaylistSummary.class)))
    @ApiResponse(responseCode = "400", description = "The name is blank or too long", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
    public Mono<ResponseEntity<PlaylistSummary>> create(@Valid @RequestBody PlaylistInput input) {
        return account().flatMap(account -> playlists.create(account, input))
                .map(created -> ResponseEntity.created(URI.create("/api/playlists/" + created.id())).body(created));
    }

    @GetMapping
    @Operation(summary = "My playlists", description = "Returns the user's playlists, the most recently changed first.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "200", description = "The user's playlists",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PlaylistSummary.class))))
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    public Flux<PlaylistSummary> list() {
        return account().flatMapMany(playlists::list);
    }

    @GetMapping("{id}")
    @Operation(summary = "A playlist", description = "Returns the playlist with its tracks, in playlist order.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "200", description = "The playlist",
            content = @Content(schema = @Schema(implementation = PlaylistDTO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such playlist, or it belongs to someone else", content = @Content)
    public Mono<PlaylistDTO> get(@PathVariable String id) {
        return account().flatMap(account -> playlists.get(account, id));
    }

    @PutMapping(value = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rename a playlist", description = "Replaces the playlist's name and description.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "200", description = "The renamed playlist",
            content = @Content(schema = @Schema(implementation = PlaylistSummary.class)))
    @ApiResponse(responseCode = "400", description = "The name is blank or too long", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such playlist, or it belongs to someone else", content = @Content)
    public Mono<PlaylistSummary> update(@PathVariable String id, @Valid @RequestBody PlaylistInput input) {
        return account().flatMap(account -> playlists.update(account, id, input));
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a playlist", description = "Deletes the playlist, not the tracks it holds.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "204", description = "Playlist deleted")
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such playlist, or it belongs to someone else", content = @Content)
    public Mono<Void> delete(@PathVariable String id) {
        return account().flatMap(account -> playlists.delete(account, id));
    }

    @PutMapping("{id}/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add a track to a playlist",
            description = "Appends the track to the playlist. Idempotent: a track appears at most once.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "204", description = "The playlist holds the track")
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such playlist or track", content = @Content)
    public Mono<Void> addTrack(@PathVariable String id, @PathVariable String trackId) {
        return account().flatMap(account -> playlists.addTrack(account, id, trackId));
    }

    @DeleteMapping("{id}/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a track from a playlist",
            description = "Removes the track from the playlist. Idempotent.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "204", description = "The playlist does not hold the track")
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such playlist", content = @Content)
    public Mono<Void> removeTrack(@PathVariable String id, @PathVariable String trackId) {
        return account().flatMap(account -> playlists.removeTrack(account, id, trackId));
    }

    @PutMapping(value = "{id}/tracks", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reorder a playlist",
            description = "Sets the order of the playlist's tracks, given as the list of their ids: it must hold "
                    + "each track of the playlist exactly once.",
            security = @SecurityRequirement(name = BEARER))
    @ApiResponse(responseCode = "204", description = "The playlist is in the new order")
    @ApiResponse(responseCode = "400", description = "The ids are not exactly the playlist's tracks", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such playlist", content = @Content)
    @ApiResponse(responseCode = "409", description = "Another change to the order won the race: try again", content = @Content)
    public Mono<Void> reorder(@PathVariable String id, @RequestBody List<String> trackIds) {
        return account().flatMap(account -> playlists.reorder(account, id, trackIds));
    }

    /** The current account: security already answered 401 to anonymous requests, so empty is not expected. */
    private Mono<AccountRef> account() {
        return currentAccount.get().switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }
}

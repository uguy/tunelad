package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.tunelad.account.CurrentAccount;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@RequiredArgsConstructor
@RestController
@Tag(name = "Tracks", description = "Tracks API")
@RequestMapping(value = "api/tracks", produces = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json"})
public class TrackController {

    private final TrackService service;

    private final CurrentAccount currentAccount;

    @GetMapping
    @Operation(summary = "Find tracks", description = "Returns a list of tracks")
    public Flux<TrackDTO> list() {
        return service.findAll();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a track", description = "Stores a track with its audio, owned by the authenticated user",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Track stored", content = @Content),
            @ApiResponse(responseCode = "400", description = "A required part is missing", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
    })
    public Mono<Void> create(
            @RequestPart("title") String title,
            @RequestPart("description") String description,
            @RequestPart(name = "artist", required = false) String artist,
            @RequestPart(name = "album", required = false) String album,
            @RequestPart("file") FilePart filePart) {
        return currentAccount.get()
                // Security only lets authenticated requests through: this guards against a misconfiguration
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .map(account -> new AddTrackCommand(title, artist, album, description, filePart.content(),
                        TrackFormat.forFile(filePart.filename()), new TrackOwner(account.id(), account.displayName())))
                .flatMap(service::execute);
    }

    @GetMapping(value = "{id}")
    @Operation(summary = "Find a track by ID", description = "Returns a single track")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "track found", content = @Content(schema = @Schema(implementation = TrackDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID supplied", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Mono<ResponseEntity<TrackDTO>> get(@Parameter(description = "Track ID", required = true) @PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}

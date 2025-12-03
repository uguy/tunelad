package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteAllTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import org.tunelad.track.internal.Tracks;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@Tag(name = "Tracks", description = "Tracks API")
@RequestMapping(value = "api/tracks", produces = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json"})
public class TrackController {

    private final TrackService service;
    private final TrackService trackService;

    @GetMapping
    @Operation(summary = "Find tracks", description = "Returns a list of tracks")
    public Flux<TrackDTO> list() {
        return service.findAll();
    }

    @GetMapping("/search")
    public Flux<TrackDTO> searchTracks(@RequestParam("q") String q) {
        return trackService.searchTracks(q);
    }

    @DeleteMapping
    @Operation(summary = "Delete tracks", description = "Delete all tracks")
    public Mono<Void> clear() {
        return service.execute(new DeleteAllTrackCommand());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Void> create(
            @RequestPart("title") String title,
            @RequestPart("description") String description,
            @RequestPart(name = "artist", required = false) String artist,
            @RequestPart(name = "album", required = false) String album,
            @RequestPart("file") FilePart filePart) {
        var command = new AddTrackCommand(title, artist, album, description, filePart.content(), TrackFormat.forFile(filePart.filename()));
        return service.execute(command);
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

    @GetMapping(value = "{id}/play", produces = {"audio/mpeg", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public Publisher<DataBuffer> play(@PathVariable("id") String id, @RequestHeader("Range") String range) {
        return service.execute(new PlayTrackCommand(id));
    }

}

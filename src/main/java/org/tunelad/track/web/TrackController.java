package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackMedia;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RequiredArgsConstructor
@RestController
@Tag(name = "Tracks", description = "Tracks API")
@RequestMapping(value = "api/tracks", produces = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json"})
public class TrackController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final TrackService service;

    @GetMapping
    @Operation(summary = "Find tracks", description = "Returns a list of tracks")
    public Flux<TrackDTO> list() {
        return service.findAll();
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
    @Operation(summary = "Play a track",
            description = "Streams the track's audio. Honours a single byte range (RFC 9110); "
                    + "malformed or multi-range Range headers are ignored and the whole file is sent.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "whole audio file",
                    headers = @Header(name = HttpHeaders.ACCEPT_RANGES, description = "Always `bytes`"),
                    content = @Content(mediaType = "audio/mpeg")),
            @ApiResponse(responseCode = "206", description = "requested byte range of the audio file",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_RANGE, description = "`bytes <first>-<last>/<size>`"),
                            @Header(name = HttpHeaders.ACCEPT_RANGES, description = "Always `bytes`")
                    },
                    content = @Content(mediaType = "audio/mpeg")),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content),
            @ApiResponse(responseCode = "416", description = "Range starts at or past the end of the file",
                    headers = @Header(name = HttpHeaders.CONTENT_RANGE, description = "`bytes */<size>`"),
                    content = @Content)
    })
    public Mono<ResponseEntity<Flux<DataBuffer>>> play(
            @Parameter(description = "Track ID", required = true) @PathVariable("id") String id,
            @Parameter(in = ParameterIn.HEADER, name = HttpHeaders.RANGE, required = false,
                    description = "Single byte range, as sent by a browser audio element", example = "bytes=0-")
            @RequestHeader(name = HttpHeaders.RANGE, required = false) @Nullable String range) {
        var requested = singleRange(range);
        return service.execute(new PlayTrackCommand(id))
                .map(media -> requested.map(r -> partial(media, r)).orElseGet(() -> wholeFile(media)));
    }

    /** The one byte range of a Range header; empty when absent, malformed or listing several ranges. */
    private static Optional<HttpRange> singleRange(@Nullable String header) {
        try {
            var ranges = HttpRange.parseRanges(header);
            return ranges.size() == 1 ? Optional.of(ranges.getFirst()) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<Flux<DataBuffer>> wholeFile(TrackMedia media) {
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .contentLength(media.length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(media.content(0, media.length() - 1));
    }

    private static ResponseEntity<Flux<DataBuffer>> partial(TrackMedia media, HttpRange range) {
        long length = media.length();
        long first = range.getRangeStart(length);
        long last = range.getRangeEnd(length);
        if (first >= length) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(AUDIO_MPEG)
                .contentLength(last - first + 1)
                .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(first, last, length))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(media.content(first, last));
    }

}

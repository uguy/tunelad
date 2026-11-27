package org.tunelad.media.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.media.internal.ChunkedMediaStore;
import org.tunelad.media.internal.Media;
import org.tunelad.media.internal.RenditionStatus;
import org.tunelad.media.internal.Renditions;
import org.tunelad.media.internal.Waveform;
import org.tunelad.media.internal.WaveformStatus;
import org.tunelad.media.internal.Waveforms;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/** Streams stored audio and its waveform to the browser player, under the track URL they are keyed by. */
@RequiredArgsConstructor
@RestController
@Tag(name = "Media", description = "Stored audio and playback")
@RequestMapping("api/tracks")
public class MediaController {

    private final ChunkedMediaStore store;
    private final Waveforms waveforms;
    private final Renditions renditions;

    @GetMapping(value = "{id}/play",
            produces = {"audio/mpeg", "audio/mp4", "audio/ogg", "audio/flac", "audio/wav",
                    MediaType.APPLICATION_OCTET_STREAM_VALUE})
    @Operation(summary = "Play a track",
            description = "Streams the track's audio, with the content type of its format (`audio/mpeg` for "
                    + "audio stored before formats were recorded). A format browsers cannot play (wma) is streamed "
                    + "as its AAC rendition, `audio/mp4`, once it is made: until then the answer is 503 with "
                    + "`Retry-After`, or 422 when it could not be made (see `GET /api/tracks/{id}/audio`). Honours a "
                    + "single byte range (RFC 9110); malformed or multi-range Range headers are ignored and the "
                    + "whole file is sent.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "whole audio file",
                    headers = @Header(name = HttpHeaders.ACCEPT_RANGES, description = "Always `bytes`"),
                    content = {@Content(mediaType = "audio/mpeg"), @Content(mediaType = "audio/mp4"),
                            @Content(mediaType = "audio/ogg"), @Content(mediaType = "audio/flac"),
                            @Content(mediaType = "audio/wav")}),
            @ApiResponse(responseCode = "206", description = "requested byte range of the audio file",
                    headers = {
                            @Header(name = HttpHeaders.CONTENT_RANGE, description = "`bytes <first>-<last>/<size>`"),
                            @Header(name = HttpHeaders.ACCEPT_RANGES, description = "Always `bytes`")
                    },
                    content = {@Content(mediaType = "audio/mpeg"), @Content(mediaType = "audio/mp4"),
                            @Content(mediaType = "audio/ogg"), @Content(mediaType = "audio/flac"),
                            @Content(mediaType = "audio/wav")}),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content),
            @ApiResponse(responseCode = "422", description = "The audio could not be converted to a playable format",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "The playable audio is still being made: try again",
                    headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
                    content = @Content),
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
        return store.find(id)
                .switchIfEmpty(Mono.error(new NotFoundException("No audio found for id: " + id)))
                .flatMap(original -> playable(id, original, requested));
    }

    /**
     * What is played: the audio itself, or its rendition when browsers cannot play it. The ranges and the size of the
     * response are the ones of what is played.
     */
    private Mono<ResponseEntity<Flux<DataBuffer>>> playable(String id, Media original, Optional<HttpRange> requested) {
        return renditions.findStatus(id).map(Optional::of).defaultIfEmpty(Optional.empty())
                .flatMap(status -> {
                    if (status.isEmpty()) {
                        return Mono.just(serve(original, requested));
                    }
                    if (status.get() != RenditionStatus.READY) {
                        return Mono.just(notPlayable(status.get()));
                    }
                    return store.findRendition(id).map(rendition -> serve(rendition, requested))
                            .switchIfEmpty(Mono.fromSupplier(() -> notPlayable(RenditionStatus.PENDING)));
                });
    }

    private static ResponseEntity<Flux<DataBuffer>> serve(Media media, Optional<HttpRange> requested) {
        return requested.map(range -> partial(media, range)).orElseGet(() -> wholeFile(media));
    }

    /** What is said while the rendition is not there: wait for it, or do not expect it. */
    private static ResponseEntity<Flux<DataBuffer>> notPlayable(RenditionStatus status) {
        return status == RenditionStatus.FAILED
                ? ResponseEntity.unprocessableEntity().build()
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").build();
    }

    @GetMapping(value = "{id}/audio", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the state of a track's playable audio",
            description = "READY when `GET /api/tracks/{id}/play` can stream it: at once for formats browsers play, "
                    + "and once its AAC rendition is made for the others (wma). PENDING while the rendition is "
                    + "being made, FAILED when the audio could not be converted. The change is announced by the "
                    + "live event `audio.prepared`.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "the state of the audio"),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Mono<AudioResponse> audio(
            @Parameter(description = "Track ID", required = true) @PathVariable("id") String id) {
        return store.find(id)
                .switchIfEmpty(Mono.error(new NotFoundException("No audio found for id: " + id)))
                .then(renditions.findStatus(id).defaultIfEmpty(RenditionStatus.READY))
                .map(AudioResponse::new);
    }

    @GetMapping(value = "{id}/waveform", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the waveform of a track",
            description = "The peaks to draw the track's waveform with. They are computed from the audio shortly "
                    + "after it is uploaded: until then the status is PENDING, and FAILED when the audio could not "
                    + "be decoded. A READY waveform may be cached for an hour.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "the waveform, whatever its status",
                    headers = @Header(name = HttpHeaders.CACHE_CONTROL, description = "`max-age=3600` when READY")),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Mono<ResponseEntity<WaveformResponse>> waveform(
            @Parameter(description = "Track ID", required = true) @PathVariable("id") String id) {
        return store.find(id)
                .switchIfEmpty(Mono.error(new NotFoundException("No audio found for id: " + id)))
                // Audio stored a moment ago may not have its waveform saved yet
                .then(waveforms.findById(id).defaultIfEmpty(Waveform.pending(id)))
                .map(waveform -> {
                    var response = ResponseEntity.ok();
                    if (waveform.status() == WaveformStatus.READY) {
                        response.cacheControl(CacheControl.maxAge(Duration.ofHours(1)));
                    }
                    return response.body(WaveformResponse.of(waveform));
                });
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

    private static ResponseEntity<Flux<DataBuffer>> wholeFile(Media media) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(media.length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(media.content(0, media.length() - 1));
    }

    private static ResponseEntity<Flux<DataBuffer>> partial(Media media, HttpRange range) {
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
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(last - first + 1)
                .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(first, last, length))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(media.content(first, last));
    }
}

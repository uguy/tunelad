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
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import io.swagger.v3.oas.annotations.headers.Header;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackOwner;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.AttachAudio;
import org.tunelad.track.command.DeleteTrackCommand;
import org.tunelad.track.command.PerformanceInput;
import org.tunelad.track.command.UpdateTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;


@RequiredArgsConstructor
@RestController
@Tag(name = "Tracks", description = "Tracks API")
@RequestMapping(value = "api/tracks", produces = {MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json"})
public class TrackController {

    private final TrackService service;

    private final CurrentAccount currentAccount;

    private final JsonMapper objectMapper;

    @GetMapping
    @Operation(summary = "Find tracks", description = "Returns a list of tracks")
    public Flux<TrackDTO> list() {
        return service.findAll();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a track",
            description = "Stores a track with its audio, owned by the authenticated user, and returns it. Only `file` "
                    + "is required. The audio format is told by the file name's extension: mp3, m4a, ogg, flac, wav or wma. "
                    + "Files over 100 MB are refused. A track given no `title` is titled with its file name without the extension; "
                    + "shortly after the upload, the file's tags fill the title, description, artist and album that were left "
                    + "blank, announced by the `track.indexed` live event. A blank text part counts as omitted. BREAKING: a track's "
                    + "`description` may now be absent. The optional `setId` makes the track record an existing set, with one "
                    + "performance per tune of that set; without it the track has no set and no performances. The optional `performances` part is a JSON array with one object per tune, in "
                    + "the set's order, each with optional `startOffsetSeconds` (0 to 86400, default 0), `key` (at most 30 "
                    + "characters) and `notes` (at most 2000), e.g. `[{\"startOffsetSeconds\":0,\"key\":\"Em\"},{\"startOffsetSeconds\":95.5,\"key\":\"G\"}]`. "
                    + "The optional `catalogueTrackId` attaches the file to that catalogue track instead of creating a track, exactly like "
                    + "`POST /api/tracks/{id}/audio` (the catalogue track keeps its title, album, performances and set, so the other text parts, "
                    + "`setId` and `performances` are ignored) and answers 201 with that track.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Track stored",
                    headers = @Header(name = HttpHeaders.LOCATION, description = "The track's URL, `/api/tracks/{id}`"),
                    content = @Content(schema = @Schema(implementation = TrackDTO.class))),
            @ApiResponse(responseCode = "400", description = "The file part is missing, the set does not exist, or the performances are given without a set or are not one valid entry per tune of the set", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "`catalogueTrackId` names no track", content = @Content),
            @ApiResponse(responseCode = "409", description = "`catalogueTrackId` names a track that has audio already", content = @Content),
            @ApiResponse(responseCode = "413", description = "The audio file is larger than 100 MB", content = @Content),
            @ApiResponse(responseCode = "415", description = "The file is not an mp3, m4a, ogg, flac, wav or wma file", content = @Content)
    })
    public Mono<ResponseEntity<TrackDTO>> create(
            @Parameter(description = "A catalogue track to attach the file to instead of creating a track")
            @RequestPart(name = "catalogueTrackId", required = false) String catalogueTrackId,
            @RequestPart(name = "title", required = false) String title,
            @RequestPart(name = "description", required = false) String description,
            @RequestPart(name = "setId", required = false) String setId,
            @RequestPart(name = "performances", required = false) String performances,
            @RequestPart(name = "artist", required = false) String artist,
            @RequestPart(name = "album", required = false) String album,
            @RequestPart("file") FilePart filePart) {
        List<PerformanceInput> inputs;
        try {
            inputs = StringUtils.hasText(performances)
                    ? objectMapper.readValue(performances, new TypeReference<List<PerformanceInput>>() {})
                    : null;
        } catch (JacksonException e) {
            return Mono.error(new BadRequestException("The performances are not a JSON array of objects."));
        }
        var format = TrackFormat.forFile(filePart.filename());
        if (format.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Supported audio formats are mp3, m4a, ogg, flac, wav and wma."));
        }
        if (StringUtils.hasText(catalogueTrackId)) {
            return authenticatedAccount()
                    .map(account -> new AttachAudio(catalogueTrackId.trim(), filePart.content(), format.get(),
                            new TrackOwner(account.id(), account.displayName())))
                    .flatMap(service::execute)
                    .map(track -> ResponseEntity.created(URI.create("/api/tracks/" + track.id())).body(track));
        }
        return authenticatedAccount()
                .map(account -> new AddTrackCommand(title, filePart.filename(), artist, album, description,
                        StringUtils.hasText(setId) ? setId.trim() : null, inputs, filePart.content(),
                        format.get(), new TrackOwner(account.id(), account.displayName())))
                .flatMap(service::execute)
                .map(track -> ResponseEntity.created(URI.create("/api/tracks/" + track.id())).body(track));
    }

    @PostMapping(value = "{id}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach audio to a catalogue track",
            description = "Stores the `file` as the audio of a catalogue track (a track on an album that has no audio yet), under the same "
                    + "formats (mp3, m4a, ogg, flac, wav or wma) and 100 MB limit as an upload. The authenticated user becomes its owner and "
                    + "the track becomes an ordinary track that keeps its album, album position, title, performances and set. Its tags are "
                    + "read like an upload's, without replacing its title or artist.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audio attached", content = @Content(schema = @Schema(implementation = TrackDTO.class))),
            @ApiResponse(responseCode = "400", description = "The file part is missing", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "The track has audio already (it is not a catalogue track)", content = @Content),
            @ApiResponse(responseCode = "413", description = "The audio file is larger than 100 MB", content = @Content),
            @ApiResponse(responseCode = "415", description = "The file is not an mp3, m4a, ogg, flac, wav or wma file", content = @Content)
    })
    public Mono<TrackDTO> attachAudio(@Parameter(description = "Catalogue track ID", required = true) @PathVariable String id,
                                      @RequestPart("file") FilePart filePart) {
        var format = TrackFormat.forFile(filePart.filename());
        if (format.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Supported audio formats are mp3, m4a, ogg, flac, wav and wma."));
        }
        return authenticatedAccount()
                .map(account -> new AttachAudio(id, filePart.content(), format.get(),
                        new TrackOwner(account.id(), account.displayName())))
                .flatMap(service::execute);
    }

    @GetMapping("{id}/catalogue-suggestions")
    @Operation(summary = "Suggest catalogue tracks for a track",
            description = "Lists the catalogue tracks of the track's artist whose title matches its own ignoring case, punctuation and "
                    + "surrounding whitespace (for an imported catalogue track the listed tune names, joined, count as its title). "
                    + "Empty when the track has no artist or is a catalogue track itself. Needs no authentication.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The matching catalogue tracks, possibly none",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = TrackDTO.class)))),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Flux<TrackDTO> catalogueSuggestions(@Parameter(description = "Track ID", required = true) @PathVariable String id) {
        return service.catalogueSuggestions(id);
    }

    @PutMapping(value = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit a track",
            description = "Replaces the track's title, description, artist and album; only the title is required, and an omitted description, "
                    + "artist or album is cleared. BREAKING: the description is no longer required. An omitted `performances` leaves the performances unchanged; given, it replaces their start offset, key and "
                    + "notes, one entry per performance in order. The set, audio, format and owner stay unchanged. Only the track's owner or an administrator "
                    + "may edit it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Track updated", content = @Content(schema = @Schema(implementation = TrackDTO.class))),
            @ApiResponse(responseCode = "400", description = "Title missing or blank, or performances that are not one valid entry per performance of the track", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not the track's owner or an administrator, or a browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Mono<TrackDTO> edit(@Parameter(description = "Track ID", required = true) @PathVariable String id,
                               @Valid @RequestBody TrackEdit edit) {
        return authenticatedAccount()
                .map(account -> new UpdateTrackCommand(id, edit.title(), edit.artist(), edit.album(),
                        edit.description(), edit.performances(), account))
                .flatMap(service::execute);
    }

    @DeleteMapping(value = "{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a track",
            description = "Deletes the track and its audio. Only the track's owner or an administrator may delete it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Track deleted", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not the track's owner or an administrator, or a browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
    })
    public Mono<Void> delete(@Parameter(description = "Track ID", required = true) @PathVariable String id) {
        return authenticatedAccount()
                .map(account -> new DeleteTrackCommand(id, account))
                .flatMap(service::execute);
    }

    /** Security only lets authenticated requests through: the error guards against a misconfiguration. */
    private Mono<AccountRef> authenticatedAccount() {
        return currentAccount.get()
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
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

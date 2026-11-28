package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tunelad.track.AlbumDTO;
import org.tunelad.track.internal.AlbumService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@Tag(name = "Albums", description = "Releases by one artist, with their tracks in order")
@RequestMapping(value = "api/albums", produces = MediaType.APPLICATION_JSON_VALUE)
public class AlbumController {

    private final AlbumService service;

    @GetMapping
    @Operation(summary = "Find albums", description = "Returns the albums ordered by title, without their tracks")
    @ApiResponse(responseCode = "200", description = "The albums",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AlbumDTO.class))))
    public Flux<AlbumDTO> list() {
        return service.findAll();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an album",
            description = "Creates an album by an artist, found by name or created unclassified. The title and the artist are required.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Album created",
                    headers = @Header(name = HttpHeaders.LOCATION, description = "The album's URL, `/api/albums/{id}`"),
                    content = @Content(schema = @Schema(implementation = AlbumDTO.class))),
            @ApiResponse(responseCode = "400", description = "The title or the artist is missing or blank", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content)
    })
    public Mono<ResponseEntity<AlbumDTO>> create(@Valid @RequestBody AlbumEdit edit) {
        return service.create(edit.title(), edit.artist(), edit.coverUrl(), edit.releaseDate())
                .map(album -> ResponseEntity.created(URI.create("/api/albums/" + album.id())).body(album));
    }

    @GetMapping("{id}")
    @Operation(summary = "Find an album by ID", description = "Returns the album with its tracks by position, catalogue tracks included")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Album found", content = @Content(schema = @Schema(implementation = AlbumDTO.class))),
            @ApiResponse(responseCode = "404", description = "Album not found", content = @Content)
    })
    public Mono<AlbumDTO> get(@Parameter(description = "Album ID", required = true) @PathVariable String id) {
        return service.find(id);
    }
}

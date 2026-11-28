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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.track.ArtistDTO;
import org.tunelad.track.ArtistKind;
import org.tunelad.track.internal.ArtistService;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
@RestController
@Tag(name = "Artists", description = "Musicians and bands")
@RequestMapping(value = "api/artists", produces = MediaType.APPLICATION_JSON_VALUE)
public class ArtistController {

    private final ArtistService service;

    private final CurrentAccount currentAccount;

    @Schema(description = "An artist's new kind")
    public record KindEdit(
            @Schema(description = "musician, band, or null to leave the artist unclassified", nullable = true, example = "band")
            ArtistKind kind) {
    }

    @Schema(description = "The new members of a band")
    public record MembersEdit(
            @Schema(description = "Names of the musicians; each is matched like an artist name and created on first use", example = "[\"Paul Brock\"]")
            List<String> members) {
    }

    @GetMapping("{id}")
    @Operation(summary = "Find an artist by ID",
            description = "Returns the artist with its kind; a band lists its members and a musician the bands it belongs to")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artist found", content = @Content(schema = @Schema(implementation = ArtistDTO.class))),
            @ApiResponse(responseCode = "404", description = "Artist not found", content = @Content)
    })
    public Mono<ArtistDTO> get(@Parameter(description = "Artist ID", required = true) @PathVariable String id) {
        return service.find(id);
    }

    @PutMapping(value = "{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Classify an artist",
            description = "Sets the artist as a musician or a band, or unclassified with a null kind. Any account may do it. "
                    + "An artist that has members cannot become a musician or unclassified, and a musician that belongs to a band cannot become a band.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artist updated", content = @Content(schema = @Schema(implementation = ArtistDTO.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Artist not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "The artist has members or belongs to a band, which its new kind does not allow", content = @Content)
    })
    public Mono<ArtistDTO> setKind(@Parameter(description = "Artist ID", required = true) @PathVariable String id,
                                   @RequestBody KindEdit edit) {
        return authenticated().then(Mono.defer(() -> service.setKind(id, edit.kind())));
    }

    @PutMapping(value = "{id}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the members of a band",
            description = "Each name is matched ignoring case and surrounding whitespace and created on first use; every member becomes a musician. "
                    + "Blank names are ignored and duplicates count once. Any account may do it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members replaced", content = @Content(schema = @Schema(implementation = ArtistDTO.class))),
            @ApiResponse(responseCode = "400", description = "The band is named among its own members", content = @Content),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Browser session request without a valid CSRF token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Artist not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "The artist is not a band, or a name is that of a band", content = @Content)
    })
    public Mono<ArtistDTO> setMembers(@Parameter(description = "Artist ID of the band", required = true) @PathVariable String id,
                                      @RequestBody MembersEdit edit) {
        return authenticated().then(Mono.defer(() -> service.replaceMembers(id, edit.members())));
    }

    /** Security only lets authenticated requests through: the error guards against a misconfiguration. */
    private Mono<AccountRef> authenticated() {
        return currentAccount.get()
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }
}

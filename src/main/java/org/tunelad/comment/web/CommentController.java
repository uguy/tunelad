package org.tunelad.comment.web;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.comment.CommentDTO;
import org.tunelad.comment.CommentInput;
import org.tunelad.comment.internal.CommentService;
import org.tunelad.comment.internal.SubjectType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Remarks on Tracks, Tunes and Sets: public to read, written by Accounts")
public class CommentController {

    private final CommentService comments;
    private final CurrentAccount currentAccount;

    @GetMapping(value = "api/tracks/{id}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Comments on a track", description = "Returns the track's Comments, oldest first.")
    @ApiResponse(responseCode = "200", description = "The Comments",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CommentDTO.class))))
    @ApiResponse(responseCode = "404", description = "No such track", content = @Content)
    public Flux<CommentDTO> ofTrack(@PathVariable String id) {
        return comments.list(SubjectType.TRACK, id);
    }

    @GetMapping(value = "api/tunes/{id}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Comments on a tune", description = "Returns the tune's Comments, oldest first.")
    @ApiResponse(responseCode = "200", description = "The Comments",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CommentDTO.class))))
    @ApiResponse(responseCode = "404", description = "No such tune", content = @Content)
    public Flux<CommentDTO> ofTune(@PathVariable String id) {
        return comments.list(SubjectType.TUNE, id);
    }

    @GetMapping(value = "api/sets/{id}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Comments on a set", description = "Returns the set's Comments, oldest first.")
    @ApiResponse(responseCode = "200", description = "The Comments",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CommentDTO.class))))
    @ApiResponse(responseCode = "404", description = "No such set", content = @Content)
    public Flux<CommentDTO> ofSet(@PathVariable String id) {
        return comments.list(SubjectType.SET, id);
    }

    @PostMapping("api/tracks/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment on a track", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "The new Comment")
    @ApiResponse(responseCode = "400", description = "Blank or too long text", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such track", content = @Content)
    public Mono<CommentDTO> onTrack(@PathVariable String id, @Valid @RequestBody CommentInput input) {
        return post(SubjectType.TRACK, id, input);
    }

    @PostMapping("api/tunes/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment on a tune", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "The new Comment")
    @ApiResponse(responseCode = "400", description = "Blank or too long text", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such tune", content = @Content)
    public Mono<CommentDTO> onTune(@PathVariable String id, @Valid @RequestBody CommentInput input) {
        return post(SubjectType.TUNE, id, input);
    }

    @PostMapping("api/sets/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment on a set", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "The new Comment")
    @ApiResponse(responseCode = "400", description = "Blank or too long text", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such set", content = @Content)
    public Mono<CommentDTO> onSet(@PathVariable String id, @Valid @RequestBody CommentInput input) {
        return post(SubjectType.SET, id, input);
    }

    @PatchMapping("api/comments/{id}")
    @Operation(summary = "Edit a comment", description = "Only its author or an Administrator may edit it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The edited Comment")
    @ApiResponse(responseCode = "400", description = "Blank or too long text", content = @Content)
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not the author or an Administrator", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such comment", content = @Content)
    public Mono<CommentDTO> edit(@PathVariable String id, @Valid @RequestBody CommentInput input) {
        return account().flatMap(account -> comments.edit(account, id, input.text()));
    }

    @DeleteMapping("api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a comment", description = "Only its author or an Administrator may delete it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "The Comment is gone")
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not the author or an Administrator", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such comment", content = @Content)
    public Mono<Void> delete(@PathVariable String id) {
        return account().flatMap(account -> comments.delete(account, id));
    }

    private Mono<CommentDTO> post(SubjectType type, String subjectId, CommentInput input) {
        return account().flatMap(account -> comments.post(account, type, subjectId, input.text()));
    }

    /** The current account: security already answered 401 to anonymous requests, so empty is not expected. */
    private Mono<AccountRef> account() {
        return currentAccount.get().switchIfEmpty(Mono.error(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }
}

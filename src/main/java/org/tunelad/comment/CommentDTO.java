package org.tunelad.comment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(title = "Comment", description = "A remark an Account left on a Track, a Tune or a Set")
public record CommentDTO(
		@Schema(description = "The Comment's id") String id,
		@Schema(description = "The text, plain: show it verbatim") String text,
		@Schema(description = "The id of the Account that wrote it") String authorId,
		@Schema(description = "The author's name when they wrote it") String authorName,
		@Schema(description = "When it was posted") Instant createdAt,
		@Schema(description = "When it was last edited; null when never", nullable = true) Instant editedAt) {
}

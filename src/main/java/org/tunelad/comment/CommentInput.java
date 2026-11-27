package org.tunelad.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(title = "Comment input", description = "The text of a Comment")
public record CommentInput(

		@Schema(description = "Plain text, trimmed, 1 to 2000 characters", example = "Lovely flute")
		@NotBlank(message = "A comment needs a text.")
		@Size(max = 2000)
		String text) {

	public CommentInput {
		text = text == null ? null : text.strip();
	}
}

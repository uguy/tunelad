package org.tunelad.comment.event;

/** The Comments of a Track, a Tune or a Set changed: one was posted, edited or deleted. */
public record CommentChanged(String subjectId) {
}

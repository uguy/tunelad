package org.tunelad.comment.internal;

import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.comment.CommentDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * The comment table. Subjects and authors belong to other modules and are known by id only, with no foreign key;
 * the author's name is kept as it was when the Comment was written.
 */
@Component
class Comments {

    private static final String COLUMNS = "id, text, author_id, author_name, created_at, edited_at";

    private final DatabaseClient client;

    Comments(DatabaseClient client) {
        this.client = client;
    }

    Mono<CommentDTO> add(SubjectType type, String subjectId, String authorId, String authorName, String text) {
        return client.sql("""
                        INSERT INTO comment_comments (id, subject_type, subject_id, author_id, author_name, text)
                        VALUES (:id, :type, :subject, :author, :name, :text) RETURNING\s""" + COLUMNS)
                .bind("id", UUID.randomUUID().toString()).bind("type", type.name()).bind("subject", subjectId)
                .bind("author", authorId).bind("name", authorName).bind("text", text)
                .map(Comments::map).one();
    }

    Flux<CommentDTO> findBySubject(SubjectType type, String subjectId) {
        return client.sql("SELECT " + COLUMNS + """
                         FROM comment_comments WHERE subject_type = :type AND subject_id = :subject
                        ORDER BY created_at, id""")
                .bind("type", type.name()).bind("subject", subjectId)
                .map(Comments::map).all();
    }

    /** The Comment with this id and the id of its subject; empty when there is none. */
    Mono<Stored> findById(String id) {
        return client.sql("SELECT " + COLUMNS + ", subject_id FROM comment_comments WHERE id = :id")
                .bind("id", id)
                .map(row -> new Stored(map(row), row.get("subject_id", String.class))).one();
    }

    Mono<CommentDTO> updateText(String id, String text) {
        return client.sql("UPDATE comment_comments SET text = :text, edited_at = now() WHERE id = :id RETURNING " + COLUMNS)
                .bind("id", id).bind("text", text)
                .map(Comments::map).one();
    }

    Mono<Boolean> delete(String id) {
        return client.sql("DELETE FROM comment_comments WHERE id = :id")
                .bind("id", id).fetch().rowsUpdated().map(rows -> rows > 0);
    }

    /** Removes every Comment of the subject; true when there were some. */
    Mono<Boolean> deleteBySubject(SubjectType type, String subjectId) {
        return client.sql("DELETE FROM comment_comments WHERE subject_type = :type AND subject_id = :subject")
                .bind("type", type.name()).bind("subject", subjectId)
                .fetch().rowsUpdated().map(rows -> rows > 0);
    }

    private static CommentDTO map(Readable row) {
        return new CommentDTO(row.get("id", String.class), row.get("text", String.class),
                row.get("author_id", String.class), row.get("author_name", String.class),
                row.get("created_at", Instant.class), row.get("edited_at", Instant.class));
    }

    record Stored(CommentDTO comment, String subjectId) {
    }
}

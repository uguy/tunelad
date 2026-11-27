package org.tunelad.comment.internal;

import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.comment.CommentDTO;
import org.tunelad.comment.event.CommentChanged;
import org.tunelad.infrastructure.ForbiddenException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.set.SetCatalog;
import org.tunelad.track.TrackService;
import org.tunelad.tune.TuneCatalog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** Posts, lists, edits and deletes Comments; only their author or an Administrator edits or deletes one. */
@Service
public class CommentService {

    private final Comments comments;
    private final TrackService tracks;
    private final TuneCatalog tunes;
    private final SetCatalog sets;
    private final TransactionalEvents events;

    CommentService(Comments comments, TrackService tracks, TuneCatalog tunes, SetCatalog sets,
                   TransactionalEvents events) {
        this.comments = comments;
        this.tracks = tracks;
        this.tunes = tunes;
        this.sets = sets;
        this.events = events;
    }

    /** The Comments of the subject, oldest first. Fails with {@link NotFoundException} when it does not exist. */
    @ReactiveTransactional(readOnly = true)
    public Flux<CommentDTO> list(SubjectType type, String subjectId) {
        return requireSubject(type, subjectId).thenMany(comments.findBySubject(type, subjectId));
    }

    /** Posts a Comment on the subject for the account, and announces it once committed. */
    @ReactiveTransactional
    public Mono<CommentDTO> post(AccountRef author, SubjectType type, String subjectId, String text) {
        return requireSubject(type, subjectId)
                .then(comments.add(type, subjectId, author.id(), author.displayName(), text))
                .flatMap(comment -> events.publish(new CommentChanged(subjectId)).thenReturn(comment));
    }

    @ReactiveTransactional
    public Mono<CommentDTO> edit(AccountRef account, String id, String text) {
        return requireMayChange(account, id)
                .flatMap(stored -> comments.updateText(id, text)
                        .flatMap(comment -> events.publish(new CommentChanged(stored.subjectId())).thenReturn(comment)));
    }

    @ReactiveTransactional
    public Mono<Void> delete(AccountRef account, String id) {
        return requireMayChange(account, id)
                .flatMap(stored -> comments.delete(id)
                        .then(events.publish(new CommentChanged(stored.subjectId()))));
    }

    /** Takes every Comment of a deleted Track away, and announces it when there were some. */
    @ReactiveTransactional
    public Mono<Void> deleteWithTrack(String trackId) {
        return comments.deleteBySubject(SubjectType.TRACK, trackId)
                .filter(Boolean::booleanValue)
                .flatMap(deleted -> events.publish(new CommentChanged(trackId)));
    }

    private Mono<Comments.Stored> requireMayChange(AccountRef account, String id) {
        return comments.findById(id)
                .switchIfEmpty(Mono.error(() -> new NotFoundException("No comment found for id: " + id)))
                .flatMap(stored -> account.admin() || account.id().equals(stored.comment().authorId())
                        ? Mono.just(stored)
                        : Mono.error(new ForbiddenException("Only the author or an Administrator may change a comment.")));
    }

    private Mono<?> requireSubject(SubjectType type, String id) {
        Mono<?> found = switch (type) {
            case TRACK -> tracks.findById(id);
            case TUNE -> tunes.findAllById(List.of(id)).next();
            case SET -> sets.find(id);
        };
        return found.switchIfEmpty(Mono.error(() -> new NotFoundException("No " + type.name().toLowerCase() + " found for id: " + id)));
    }
}

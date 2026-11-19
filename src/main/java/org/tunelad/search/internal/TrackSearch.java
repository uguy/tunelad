package org.tunelad.search.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** Queries the search read model. */
@Component
@RequiredArgsConstructor
public class TrackSearch {

    private final SearchTracks searchTracks;
    private final ReactiveMongoOperations mongo;

    /**
     * Tracks with at least one word of the query in their title or description, best matches first; every track
     * when the query is blank.
     */
    public Flux<SearchTrack> search(String q) {
        if (q.isBlank()) return searchTracks.findAll();
        var query = TextQuery.queryText(TextCriteria.forDefaultLanguage().caseSensitive(false).matching(q))
                .sortByScore();
        return mongo.find(query, SearchTrack.class);
    }
}

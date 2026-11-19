package org.tunelad.search.internal;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

interface SearchTracks extends ReactiveMongoRepository<SearchTrack, String> {
}

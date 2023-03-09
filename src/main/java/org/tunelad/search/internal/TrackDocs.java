package org.tunelad.search.internal;

import org.tunelad.search.IndexedTrack;

import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface TrackDocs extends ReactiveElasticsearchRepository<IndexedTrack, String> {
}

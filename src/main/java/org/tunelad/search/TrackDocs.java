package org.tunelad.search;

import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface TrackDocs extends ReactiveElasticsearchRepository<TrackDoc, String> {
}

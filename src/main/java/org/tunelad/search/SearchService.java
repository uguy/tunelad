package org.tunelad.search;

import reactor.core.publisher.Flux;


public interface SearchService {
	Flux<IndexedTrack> searchTracks(String q);
}

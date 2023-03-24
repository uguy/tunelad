package org.tunelad.search.internal;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tunelad.search.Artist;
import org.tunelad.search.IndexedTrack;
import org.tunelad.search.SearchService;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.event.AllTracksDeleted;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class EsSearchService implements SearchService, TrackEventHandler {

	private final TrackDocs trackDocs;

	private final ReactiveElasticsearchOperations esClient;

	private final TrackService trackService;

	//	// TransactionalEventListener do not work in reactive env (maybe due to my code) ! Flux is blocked somewhere and the app must be restarted for the events to be consumed.
//	@Transactional
//	@TransactionalEventListener
	@Override
	public Mono<Void> on(NewTrackSaved event) {
		log.info("New track saved, indexing it: " + event.track());
		return trackService.findById(event.track().id())
				.flatMap(trackDTO -> {
					var trackDoc = toDoc(trackDTO);
					log.info("indexing {}", trackDoc);
					return trackDocs.save(trackDoc);
				})
				.flatMap(indexedTrack -> {
					// Enrich with data from elsewhere
					if (indexedTrack.getArtists().stream().anyMatch(artist -> "Breabach".equalsIgnoreCase(artist.name()))) {
						var tags = List.of(
								"Scotland",
								"traditionnal music",
								"Megan Henderson", "Calum MacCrimmon", "James Lindsay", "Ewan Robertson", "Conal McDonagh",
								"Fiddle", "Double Bass", "Moog", "Highland Bagpipe", "Whistle", "Bouzouki", "Uilleann pipes", "Guitar", "Cajon"
						);
						indexedTrack.setTags(tags);
					}
					if (indexedTrack.getArtists().stream().anyMatch(artist -> "Stephen Doherty".equalsIgnoreCase(artist.name()))) {
						var tags = List.of(
								"Ireland",
								"Eire",
								"traditionnal music",
								"Stephen Doherty",
								"Flute", "Accordion", "Whistle", "Bouzouki", "Guitar"
						);
						indexedTrack.setTags(tags);
					}
					return trackDocs.save(indexedTrack);
				})
				.then().onErrorComplete();
	}

	@Override
	public Mono<Void> on(AllTracksDeleted event) {
		return trackDocs.deleteAll();
	}

	IndexedTrack toDoc(TrackDTO trackDTO) {
		var trackDoc = new IndexedTrack();
		trackDoc.setId(trackDTO.id());
		trackDoc.setTitle(trackDTO.title());
		trackDoc.setAlbum(trackDTO.album());
		trackDoc.setArtists(List.of(new Artist(trackDTO.artist())));
		trackDoc.setDescription(trackDTO.description());
		trackDoc.setFormat(trackDTO.format());
		return trackDoc;
	}

	@Override
	public Flux<IndexedTrack> searchTracks(String q) {
		Criteria criteria = new Criteria("title").contains(q)
				.or("description").contains(q)
				.or("album").contains(q)
				.or("artist").contains(q)
				.or("tags").contains(q);
		return esClient.search(new CriteriaQuery(criteria), IndexedTrack.class).map(SearchHit::getContent);
	}
}

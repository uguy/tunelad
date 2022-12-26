package org.tunelad.search.internal;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tunelad.search.Artist;
import org.tunelad.search.SearchService;
import org.tunelad.search.TrackDoc;
import org.tunelad.search.TrackDocs;
import org.tunelad.search.TrackEventHandler;
import org.tunelad.track.spi.AllTracksDeleted;
import org.tunelad.track.spi.NewTrackSaved;
import org.tunelad.track.spi.TrackDTO;
import org.tunelad.track.spi.TrackService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.context.event.EventListener;
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
	@EventListener
	public Mono<Void> on(NewTrackSaved event) {
		log.info("New track saved, indexing it: " + event.track());
		return trackService.findById(event.track().id())
				.flatMap(trackDTO -> {
					var trackDoc = toDoc(trackDTO);
					log.info("indexing {}", trackDoc);
					return trackDocs.save(trackDoc);
				})
				.flatMap(trackDoc -> {
					// Enrich with data from elsewhere
					if (trackDoc.getArtists().stream().anyMatch(artist -> "Breabach".equalsIgnoreCase(artist.name()))) {
						var tags = List.of(
								"Scotland",
								"traditionnal music",
								"Megan Henderson", "Calum MacCrimmon", "James Lindsay", "Ewan Robertson", "Conal McDonagh",
								"Fiddle", "Double Bass", "Moog", "Highland Bagpipe", "Whistle", "Bouzouki", "Uilleann pipes", "Guitar", "Cajon"
						);
						trackDoc.setTags(tags);
					}
					if (trackDoc.getArtists().stream().anyMatch(artist -> "Stephen Doherty".equalsIgnoreCase(artist.name()))) {
						var tags = List.of(
								"Ireland",
								"Eire",
								"traditionnal music",
								"Stephen Doherty",
								"Flute", "Accordion", "Whistle", "Bouzouki", "Guitar"
						);
						trackDoc.setTags(tags);
					}
					return trackDocs.save(trackDoc);
				})
				.then();
	}

	@Override
	@EventListener
	public Mono<Void> on(AllTracksDeleted event) {
		return trackDocs.deleteAll();
	}

	TrackDoc toDoc(TrackDTO trackDTO) {
		var trackDoc = new TrackDoc();
		trackDoc.setId(trackDTO.id());
		trackDoc.setTitle(trackDTO.title());
		if (Objects.nonNull(trackDTO.album())) {
			trackDoc.setAlbum(trackDTO.album());
		}
		if (Objects.nonNull(trackDTO.artist())) {
			trackDoc.setArtists(List.of(new Artist(trackDTO.artist())));
		}
		trackDoc.setDescription(trackDTO.description());
		trackDoc.setFormat(trackDTO.format());
		return trackDoc;
	}

	@Override
	public Flux<TrackDoc> searchTracks(String q) {
		Criteria criteria = new Criteria("title").contains(q)
				.or("description").contains(q)
				.or("album").contains(q)
				.or("artist").contains(q)
				.or("tags").contains(q);
		return esClient.search(new CriteriaQuery(criteria), TrackDoc.class).map(SearchHit::getContent);
	}
}

package org.tunelad.search;

import org.tunelad.track.spi.AllTracksDeleted;
import org.tunelad.track.spi.NewTrackSaved;
import reactor.core.publisher.Mono;

import org.springframework.context.event.EventListener;

public interface TrackEventHandler {

	@EventListener
	Mono<Void> on(NewTrackSaved event);

	@EventListener
	Mono<Void> on(AllTracksDeleted event);
}

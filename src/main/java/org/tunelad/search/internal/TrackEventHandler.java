package org.tunelad.search.internal;

import org.jmolecules.event.annotation.DomainEventHandler;
import org.tunelad.track.event.AllTracksDeleted;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Mono;

import org.springframework.context.event.EventListener;

public interface TrackEventHandler {
	@DomainEventHandler
	@EventListener
	Mono<Void> on(NewTrackSaved event);
	@DomainEventHandler
	@EventListener
	Mono<Void> on(AllTracksDeleted event);
}

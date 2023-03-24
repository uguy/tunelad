
package org.tunelad.search.internal;


import org.tunelad.track.event.AllTracksDeleted;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Mono;

import org.springframework.context.event.EventListener;

public interface TrackEventHandler {
	@EventListener
	Mono<Void> on(NewTrackSaved event);

	@EventListener
	Mono<Void> on(AllTracksDeleted event);
}

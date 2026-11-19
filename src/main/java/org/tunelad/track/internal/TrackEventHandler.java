
package org.tunelad.track.internal;


import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Mono;

import org.springframework.context.event.EventListener;

public interface TrackEventHandler {
	@EventListener
	Mono<Void> on(NewTrackSaved event);
}

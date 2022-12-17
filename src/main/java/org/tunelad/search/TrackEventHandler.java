package org.tunelad.search;

import org.tunelad.track.spi.AllTracksDeleted;
import org.tunelad.track.spi.NewTrackSaved;
import reactor.core.publisher.Mono;

public interface TrackEventHandler {
	Mono<Void> on(NewTrackSaved event);
	Mono<Void> on(AllTracksDeleted event);
}

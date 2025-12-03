package org.tunelad.track.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tunelad.track.event.AllTracksDeleted;
import org.tunelad.track.event.NewTrackSaved;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
class DefaultTrackEventHandler implements TrackEventHandler {


    //	// TransactionalEventListener do not work in reactive env (maybe due to my code) ! Flux is blocked somewhere and the app must be restarted for the events to be consumed.
//	@Transactional
//	@TransactionalEventListener
    @Override
    public Mono<Void> on(NewTrackSaved event) {
        log.info("New track saved: " + event.track());
        return Mono.empty();
    }

    @Override
    public Mono<Void> on(AllTracksDeleted event) {
        log.info("All tracks deleted");
        return Mono.empty();
    }
}

package org.tunelad.track.event;

import org.jmolecules.event.annotation.DomainEvent;
import org.tunelad.track.TrackDTO;

@DomainEvent
public record NewTrackSaved(TrackDTO track){}

package org.tunelad.track.internal;

import org.mapstruct.Mapper;
import org.tunelad.infrastructure.ModelMapper;
import org.tunelad.track.Track;
import org.tunelad.track.spi.TrackDTO;

@Mapper(componentModel = "spring")
public interface TrackMapper extends ModelMapper<TrackDTO, Track> {}

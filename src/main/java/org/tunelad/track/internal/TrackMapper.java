package org.tunelad.track.internal;

import org.mapstruct.Mapper;
import org.tunelad.infrastructure.ModelMapper;
import org.tunelad.track.TrackDTO;

@Mapper(componentModel = "spring")
public interface TrackMapper extends ModelMapper<TrackDTO, Track> {

    default Artist map(String value){
        return new Artist(value);
    }
    default String map(Artist value){
        return value.name();
    }
}

package org.tunelad.track.internal;

import org.mapstruct.Mapper;
import org.tunelad.infrastructure.ModelMapper;
import org.tunelad.track.TrackDTO;

@Mapper(componentModel = "spring")
public interface TrackMapper extends ModelMapper<TrackDTO, Track> {

    default Artist map(String value){
        return new Artist(null, value);
    }
    default String map(Artist value){
        // A track without an artist name has no artist: its columns are all null
        return value == null ? null : value.name();
    }
}

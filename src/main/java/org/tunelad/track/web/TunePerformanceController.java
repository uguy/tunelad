package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tunelad.track.AlbumDTO;
import org.tunelad.track.TrackService;
import org.tunelad.track.internal.AlbumService;
import org.tunelad.track.TunePerformanceDTO;
import reactor.core.publisher.Flux;

/** Served by the track module, which knows the Performances: the tune module cannot depend on it. */
@RequiredArgsConstructor
@RestController
@Tag(name = "Tunes")
@RequestMapping(value = "api/tunes", produces = MediaType.APPLICATION_JSON_VALUE)
public class TunePerformanceController {

    private final TrackService service;

    private final AlbumService albums;

    @GetMapping("{id}/performances")
    @Operation(summary = "Find the performances of a tune",
            description = "Every performance of the tune across tracks, ordered by track title; empty when it was never recorded")
    public Flux<TunePerformanceDTO> performances(@Parameter(description = "Tune ID", required = true) @PathVariable String id) {
        return service.findPerformancesByTune(id);
    }

    @GetMapping("{id}/albums")
    @Operation(summary = "Find the albums a tune appears on",
            description = "The albums with a track, catalogue tracks included, that has a resolved performance of the tune, ordered by title and without their tracks; empty when there are none")
    public Flux<AlbumDTO> albums(@Parameter(description = "Tune ID", required = true) @PathVariable String id) {
        return albums.findByTune(id);
    }
}

package org.tunelad.search.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.tunelad.search.SearchService;
import org.tunelad.search.TrackDoc;
import reactor.core.publisher.Flux;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "Search API")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "api/search", produces = { MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json" })
public class SearchController {

	private final SearchService searchService;

	@GetMapping("/tracks")
	public Flux<TrackDoc> searchTracks(@RequestParam("q") String q) {
		return searchService.searchTracks(q);
	}
}

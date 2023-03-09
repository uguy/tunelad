package org.tunelad.track.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.tunelad.track.TrackDTO;
import org.tunelad.track.TrackFormat;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddTrackCommand;
import org.tunelad.track.command.DeleteAllTrackCommand;
import org.tunelad.track.command.PlayTrackCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@Tag(name = "Tracks", description = "Tracks API")
@RequestMapping(value = "api/tracks", produces = { MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json" })
public class TrackController {
	private final TrackService service;

	@GetMapping
	@Operation(summary = "Find tracks", description = "Returns a list of tracks")
	public Flux<TrackDTO> list() {
		return service.findAll();
	}

	@DeleteMapping
	@Operation(summary = "Delete tracks", description = "Delete all tracks")
	public Mono<Void> clear() {
		return service.execute(new DeleteAllTrackCommand());
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<Void> create(
			@RequestPart("title") String title,
			@RequestPart("description") String description,
			@RequestPart(name = "artist", required = false) String artist,
			@RequestPart(name = "album", required = false) String album,
			@RequestPart("file") FilePart filePart) {

		return DataBufferUtils.join(filePart.content())
				.map(dataBuffer -> {
					byte[] bytes = new byte[dataBuffer.readableByteCount()];
					dataBuffer.read(bytes); DataBufferUtils.release(dataBuffer);
					return bytes;
				}).flatMap(data -> {
					var command = new AddTrackCommand(title, artist, album, description, data, TrackFormat.forFile(filePart.filename()));
					return service.execute(command);
				});
	}

	@GetMapping(value = "{id}")
	@Operation(summary = "Find a track by ID", description = "Returns a single track")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "track found", content = @Content(schema = @Schema(implementation = TrackDTO.class))),
			@ApiResponse(responseCode = "400", description = "Invalid ID supplied", content = @Content),
			@ApiResponse(responseCode = "404", description = "Track not found", content = @Content)
	})
	public Mono<ResponseEntity<TrackDTO>> get(@Parameter(description = "Track ID", required = true) @PathVariable String id) {
		return service.findById(id)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	@GetMapping(value = "{id}/play", produces = { "audio/mpeg", MediaType.APPLICATION_OCTET_STREAM_VALUE })
	public Mono<Resource> play(@PathVariable("id") String id, @RequestHeader("Range") String range) {
		return service.execute(new PlayTrackCommand(id))
				.map(ByteArrayResource::new);
	}

}

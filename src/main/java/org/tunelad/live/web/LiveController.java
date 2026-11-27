package org.tunelad.live.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import org.tunelad.live.internal.LiveMetrics;
import org.tunelad.live.internal.LiveProperties;
import org.tunelad.live.internal.LiveRouter;
import org.tunelad.live.internal.LiveSubjects;
import reactor.core.publisher.Flux;

import java.time.Duration;

/** Streams live updates to the browser. */
@RequiredArgsConstructor
@RestController
@Tag(name = "Live", description = "Live updates for the UI")
@RequestMapping("api/live")
public class LiveController {

    private final LiveRouter router;
    private final CurrentAccount currentAccount;
    private final LiveProperties properties;
    private final LiveMetrics metrics;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Follow live updates",
            description = "A stream of Server-Sent Events that stays open until the client disconnects. Each event is "
                    + "named after its type and its data is a JSON object with the `type` and, when the event concerns "
                    + "one entity, its `id`; the content is never sent, the client refetches it through the API. "
                    + "Types: `track.indexed` and `track.unindexed` (a track became or stopped being searchable) and "
                    + "`waveform.computed` (a track's waveform is ready or failed) and `audio.prepared` (a track's audio, in a "
                    + "format browsers cannot play, was converted or could not be), for everyone; and `favourite.changed` "
                    + "(its id is a track's) and `playlist.changed` (its id is a playlist's), which only the connections "
                    + "of the account they concern receive. Which events a connection receives depends only on who "
                    + "makes the request. A comment line is sent regularly to keep idle connections open. Events can "
                    + "be missed, such as while disconnected: refetch what depends on them when reconnecting.")
    @ApiResponse(responseCode = "200", description = "the event stream",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    public Flux<ServerSentEvent<String>> live() {
        return currentAccount.get()
                .map(AccountRef::id)
                .map(LiveSubjects::accountKey)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMapMany(viewer -> Flux.merge(
                        router.stream(viewer.orElse(null))
                                .map(routed -> {
                                    metrics.delivered(routed.type());
                                    return ServerSentEvent.builder(routed.body()).event(routed.type()).build();
                                }),
                        heartbeat()))
                .doOnSubscribe(subscription -> metrics.connectionOpened())
                .doFinally(signal -> metrics.connectionClosed());
    }

    /**
     * A comment line, the first one at once: the response only starts, for the browser, with its first line, so a
     * stream with no event yet would not be known to be open.
     */
    private Flux<ServerSentEvent<String>> heartbeat() {
        return Flux.interval(Duration.ZERO, properties.heartbeat())
                .map(tick -> ServerSentEvent.<String>builder().comment("keepalive").build());
    }
}

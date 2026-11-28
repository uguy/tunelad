package org.tunelad.track.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.account.AccountRef;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.track.TrackService;
import org.tunelad.track.command.AddCatalogueTrack;
import org.tunelad.tune.TuneCatalog;
import org.tunelad.tune.TuneDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fills the Albums from the thesession.org recordings dump: one Album per recording with a Catalogue Track per
 * track. Tunes are looked up in the catalogue and never created, so a tune it lacks stays unresolved. What exists
 * already is left alone (Albums by Artist and title ignoring case, Catalogue Tracks by position), so a run that
 * stopped early is resumed by running it again. Each Album is written in its own transaction.
 */
@Slf4j
@Service
public class AlbumDumpImport {

	public enum State { idle, running, done, failed }

	/** The last run: its state, what it created, the rows it skipped, and why it failed. */
	public record Run(State state, int albumsCreated, int tracksCreated, int skipped, String reason) {}

	private static final int TITLE_MAX = 80;

	/** Recordings whose Tunes are looked up with one query. */
	private static final int BATCH = 50;

	private final AlbumDump dump;
	private final Albums albums;
	private final Artists artists;
	private final Tracks tracks;
	private final TrackService trackService;
	private final TuneCatalog tunes;
	private final TransactionalOperator transactionalOperator;
	private final AtomicReference<Run> run = new AtomicReference<>(new Run(State.idle, 0, 0, 0, null));

	AlbumDumpImport(AlbumDump dump, Albums albums, Artists artists, Tracks tracks, TrackService trackService, TuneCatalog tunes,
			@Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
		this.dump = dump;
		this.albums = albums;
		this.artists = artists;
		this.tracks = tracks;
		this.trackService = trackService;
		this.tunes = tunes;
		this.transactionalOperator = TransactionalOperator.create(transactionManager);
	}

	public Run last() {
		return run.get();
	}

	/** Starts a run in the background; false when one is running already. */
	public boolean start(AccountRef by) {
		var running = new Run(State.running, 0, 0, 0, null);
		var previous = run.get();
		if (previous.state() == State.running || !run.compareAndSet(previous, running)) {
			return false;
		}
		execute(by).subscribe(
				result -> {},
				error -> {
					log.warn("The thesession.org recordings import failed: {}", error.toString());
					run.updateAndGet(c -> new Run(State.failed, c.albumsCreated(), c.tracksCreated(), c.skipped(), String.valueOf(error.getMessage())));
				},
				() -> run.updateAndGet(c -> new Run(State.done, c.albumsCreated(), c.tracksCreated(), c.skipped(), null)));
		return true;
	}

	/** One tune of a track, as the dump lists it. */
	private record Entry(int number, String name, Integer tuneId) {}

	/** What one recording came to: its Album and its Tracks by track number, each with its tunes in order. */
	private record Recording(String artist, String title, TreeMap<Integer, List<Entry>> tracks) {}

	private record Created(int albums, int tracks) {}

	/** The import itself, completing when the whole dump has been read. Recordings are written as they are read. */
	Mono<Void> execute(AccountRef by) {
		return dump.recordings()
				.bufferUntilChanged(row -> String.valueOf(row.get("id"))) // the dump lists a recording's rows together
				.concatMapIterable(rows -> {
					var recordings = new LinkedHashMap<String, Recording>();
					rows.forEach(row -> group(recordings, row));
					return recordings.values();
				})
				.buffer(BATCH) // a batch cut short by a failure is lost: running again resumes
				.concatMapDelayError(batch -> tunes.findBySourceIds(tuneIds(batch))
						.flatMapMany(known -> Flux.fromIterable(batch)
								.concatMap(recording -> transactionalOperator.transactional(importRecording(recording, known, by))
										.doOnNext(created -> add(created.albums(), created.tracks(), 0)))))
				.then();
	}

	private static Set<Integer> tuneIds(List<Recording> recordings) {
		var ids = new HashSet<Integer>();
		recordings.forEach(r -> r.tracks().values().forEach(list -> list.forEach(e -> {
			if (e.tuneId() != null) {
				ids.add(e.tuneId());
			}
		})));
		return ids;
	}

	private void group(Map<String, Recording> recordings, Map<String, String> row) {
		var id = text(row, "id");
		var artist = text(row, "artist");
		var title = text(row, "recording");
		var track = number(row, "track");
		var name = text(row, "tune");
		var position = number(row, "number");
		if (id == null || artist == null || title == null || track == null || track < 1 || name == null) {
			add(0, 0, 1);
			return;
		}
		var recording = recordings.computeIfAbsent(id, key -> new Recording(artist, title, new TreeMap<>()));
		var entries = recording.tracks().computeIfAbsent(track, key -> new ArrayList<>());
		entries.add(new Entry(position == null ? Integer.MAX_VALUE : position, name, number(row, "tune_id")));
		entries.sort(Comparator.comparingInt(Entry::number)); // stable: rows with the same number keep the dump's order
	}

	private Mono<Created> importRecording(Recording recording, Map<Integer, TuneDTO> known, AccountRef by) {
		return artists.findOrCreate(recording.artist()).flatMap(artist -> albums.findByArtistAndTitle(artist.id(), recording.title())
				.map(album -> new Found(album, false))
				.switchIfEmpty(Mono.defer(() -> {
					var album = new Album();
					album.setTitle(recording.title());
					album.setArtist(artist);
					return albums.save(album).map(saved -> new Found(saved, true));
				})))
				.flatMap(found -> tracks.findByAlbumId(found.album().getId()).map(Track::getAlbumPosition).collect(HashSet<Integer>::new, Set::add)
						.flatMap(taken -> Flux.fromIterable(recording.tracks().entrySet())
								.filter(track -> !taken.contains(track.getKey()))
								.concatMap(track -> trackService.execute(command(found.album(), track.getKey(), track.getValue(), known, by)))
								.count()
								.map(count -> new Created(found.created() ? 1 : 0, count.intValue()))));
	}

	private record Found(Album album, boolean created) {}

	private static AddCatalogueTrack command(Album album, int position, List<Entry> entries, Map<Integer, TuneDTO> known, AccountRef by) {
		var listed = entries.stream().map(entry -> {
			var tune = entry.tuneId() == null ? null : known.get(entry.tuneId());
			return new AddCatalogueTrack.Listed(entry.name(), tune == null ? null : tune.id());
		}).toList();
		var title = String.join(", ", entries.stream().map(Entry::name).toList());
		return new AddCatalogueTrack(album.getId(), title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX).strip() : title, position, listed, by);
	}

	private void add(int albumsCreated, int tracksCreated, int skipped) {
		run.updateAndGet(c -> new Run(c.state(), c.albumsCreated() + albumsCreated, c.tracksCreated() + tracksCreated,
				c.skipped() + skipped, c.reason()));
	}

	private static String text(Map<String, String> row, String column) {
		var value = row.get(column);
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static Integer number(Map<String, String> row, String column) {
		try {
			return Integer.valueOf(row.getOrDefault(column, "").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

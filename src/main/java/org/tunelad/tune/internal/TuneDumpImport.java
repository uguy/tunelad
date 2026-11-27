package org.tunelad.tune.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.tune.DanceType;
import org.tunelad.tune.TuneDTO;
import org.tunelad.tune.event.TuneChanged;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fills the catalogue from the thesession.org dump. Only the first setting of each Tune is read. Tunes already
 * in the catalogue are left alone, so a run that stopped early is resumed by running it again.
 */
@Slf4j
@Service
public class TuneDumpImport {

	public enum State { idle, running, done, failed }

	/** The last run: its state, the Tunes it created, the rows it skipped, and why it failed. */
	public record Run(State state, int created, int skipped, String reason) {}

	private static final int CONCURRENCY = 8;

	private final TuneDump dump;
	private final Tunes tunes;
	private final TransactionalEvents events;
	private final TransactionalOperator transactionalOperator;
	private final AtomicReference<Run> run = new AtomicReference<>(new Run(State.idle, 0, 0, null));

	TuneDumpImport(TuneDump dump, Tunes tunes, TransactionalEvents events,
			@Qualifier(ReactiveTransactional.MANAGER) ReactiveTransactionManager transactionManager) {
		this.dump = dump;
		this.tunes = tunes;
		this.events = events;
		this.transactionalOperator = TransactionalOperator.create(transactionManager);
	}

	public Run last() {
		return run.get();
	}

	/** Starts a run in the background; false when one is running already. */
	public boolean start(String accountId) {
		var running = new Run(State.running, 0, 0, null);
		var previous = run.get();
		if (previous.state() == State.running || !run.compareAndSet(previous, running)) {
			return false;
		}
		execute(accountId).subscribe(
				result -> {},
				error -> {
					log.warn("The thesession.org import failed: {}", error.toString());
					run.updateAndGet(current -> new Run(State.failed, current.created(), current.skipped(), String.valueOf(error.getMessage())));
				},
				() -> run.updateAndGet(current -> new Run(State.done, current.created(), current.skipped(), null)));
		return true;
	}

	/** The import itself, completing when the whole dump has been read. */
	Mono<Void> execute(String accountId) {
		return dump.aliases().collect(HashMap<Integer, List<String>>::new, TuneDumpImport::addAlias)
				.flatMapMany(aliases -> {
					var seen = new HashSet<Integer>();
					return dump.tunes().concatMap(row -> {
						var tune = toTune(row, aliases);
						if (tune == null) {
							skip();
							return Flux.<TuneDTO>empty();
						}
						return seen.add(tune.sourceId()) ? Flux.just(tune) : Flux.<TuneDTO>empty(); // a later setting
					});
				})
				.flatMap(tune -> tunes.insert(tune, accountId)
								.doOnNext(inserted -> add(1, 0))
								.switchIfEmpty(Mono.fromRunnable(this::skip)),
						CONCURRENCY)
				.then(Mono.defer(this::announceCreated));
	}

	/** One event for the whole run, not one per Tune: a browser refetches its list once. None when nothing was created. */
	private Mono<Void> announceCreated() {
		return run.get().created() == 0 ? Mono.empty()
				: transactionalOperator.transactional(events.publish(new TuneChanged(null)));
	}

	private void skip() {
		add(0, 1);
	}

	private void add(int created, int skipped) {
		run.updateAndGet(current -> new Run(current.state(), current.created() + created, current.skipped() + skipped, current.reason()));
	}

	private static void addAlias(Map<Integer, List<String>> aliases, Map<String, String> row) {
		var id = id(row);
		var alias = row.getOrDefault("alias", "").trim();
		if (id != null && !alias.isEmpty()) {
			aliases.computeIfAbsent(id, key -> new ArrayList<>()).add(alias);
		}
	}

	private static Integer id(Map<String, String> row) {
		try {
			return Integer.valueOf(row.getOrDefault("tune_id", "").trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The Tune a row describes; null when the row has no id, no name or an unknown Dance type. */
	static TuneDTO toTune(Map<String, String> row, Map<Integer, List<String>> aliases) {
		var id = id(row);
		var name = row.getOrDefault("name", "").trim();
		var type = DanceType.fromValue(row.get("type"));
		if (id == null || name.isEmpty() || type.isEmpty()) {
			return null;
		}
		var key = blankToNull(row.get("mode"));
		var body = blankToNull(row.get("abc"));
		var names = aliases.getOrDefault(id, List.of()).stream()
				.filter(alias -> !alias.equalsIgnoreCase(name)).distinct().toList();
		return new TuneDTO(null, name, names, blankToNull(row.get("composer")), type.get(), key, id,
				body == null ? null : ThesessionTuneSource.abc(name, type.get(), key, body));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

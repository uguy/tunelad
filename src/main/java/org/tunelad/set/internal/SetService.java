package org.tunelad.set.internal;

import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.infrastructure.ReactiveTransactional;
import org.tunelad.infrastructure.TransactionalEvents;
import org.tunelad.set.SetCatalog;
import org.tunelad.set.SetDTO;
import org.tunelad.set.event.SetChanged;
import org.tunelad.tune.TuneCatalog;
import org.tunelad.tune.TuneDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creates, finds and lists Sets. */
@Service
public class SetService implements SetCatalog {

	public static final int MAX_TUNES = 20;

	/** A Set and whether this call created it. */
	public record Created(SetDTO set, boolean created) {}

	private final Sets sets;
	private final TuneCatalog tunes;
	private final TransactionalEvents events;

	SetService(Sets sets, TuneCatalog tunes, TransactionalEvents events) {
		this.sets = sets;
		this.tunes = tunes;
		this.events = events;
	}

	/**
	 * The Set of this ordered list of Tunes: the existing one, or a new one. Fails with {@link BadRequestException}
	 * for an empty or too long list, or a Tune that does not exist.
	 */
	@ReactiveTransactional
	public Mono<Created> create(AccountRef account, List<String> tuneIds) {
		if (tuneIds == null || tuneIds.isEmpty() || tuneIds.size() > MAX_TUNES || tuneIds.stream().anyMatch(id -> id == null || id.isBlank())) {
			return Mono.error(new BadRequestException("A set needs one to " + MAX_TUNES + " tunes."));
		}
		var list = List.copyOf(tuneIds);
		return tunes.findAllById(list.stream().distinct().toList()).count()
				.filter(found -> found == list.stream().distinct().count())
				.switchIfEmpty(Mono.error(() -> new BadRequestException("Unknown tune in the list.")))
				.then(sets.insert(list, account.id()))
				// a racing identical create makes the loser's insert do nothing: it reads the winner's Set
				.flatMap(created -> sets.findByList(list).flatMap(this::toDto)
						.flatMap(set -> (created ? events.publish(new SetChanged(set.id())) : Mono.<Void>empty())
								.thenReturn(new Created(set, created))));
	}

	/** Transactional here too: called through the catalog, {@link #create} is not reached through its proxy. */
	@Override
	@ReactiveTransactional
	public Mono<SetDTO> findOrCreate(AccountRef creator, List<String> tuneIds) {
		return create(creator, tuneIds).map(Created::set);
	}

	public Mono<SetDTO> findById(String id) {
		return sets.findById(id).flatMap(this::toDto)
				.switchIfEmpty(Mono.error(() -> new NotFoundException("No set found for id: " + id)));
	}

	@Override
	public Mono<SetDTO> find(String id) {
		return sets.findById(id).flatMap(this::toDto);
	}

	@Override
	public Flux<SetDTO> findAllById(java.util.Collection<String> ids) {
		return sets.findAllById(ids).collectList().flatMapMany(rows -> {
			var tuneIds = rows.stream().flatMap(row -> row.tuneIds().stream()).distinct().toList();
			return tunes.findAllById(tuneIds).collect(Collectors.toMap(TuneDTO::id, Function.identity()))
					.flatMapMany(byId -> Flux.fromIterable(rows.stream().map(row -> dto(row, byId)).toList()));
		});
	}

	/** The Sets, by the names of their Tunes, optionally only those with the Tune. */
	public Flux<SetDTO> list(String tuneId) {
		return sets.findAll(tuneId).collectList().flatMapMany(rows -> {
			var ids = rows.stream().flatMap(row -> row.tuneIds().stream()).distinct().toList();
			return tunes.findAllById(ids).collect(Collectors.toMap(TuneDTO::id, Function.identity()))
					.flatMapMany(byId -> Flux.fromIterable(rows.stream().map(row -> dto(row, byId))
							.sorted(Comparator.comparing(SetService::sortKey)).toList()));
		});
	}

	private Mono<SetDTO> toDto(Sets.Row row) {
		return tunes.findAllById(row.tuneIds().stream().distinct().toList())
				.collect(Collectors.toMap(TuneDTO::id, Function.identity()))
				.map(byId -> dto(row, byId));
	}

	private static SetDTO dto(Sets.Row row, Map<String, TuneDTO> byId) {
		return new SetDTO(row.id(), row.tuneIds().stream().map(byId::get)
				.map(tune -> new SetDTO.Entry(tune.id(), tune.name(), tune.danceType())).toList());
	}

	private static String sortKey(SetDTO set) {
		return set.tunes().stream().map(entry -> entry.name().toLowerCase()).collect(Collectors.joining("\u0000"));
	}
}

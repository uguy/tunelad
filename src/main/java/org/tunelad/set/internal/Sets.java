package org.tunelad.set.internal;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * The set_sets and set_set_tunes tables. Tunes and accounts belong to other modules and are known by id only. The
 * unique {@code tunes_key}, the ordered tune ids joined, is what makes a list resolve to one Set.
 */
@Component
class Sets {

	/** A stored Set: its id and the ids of its Tunes, in order. */
	record Row(String id, List<String> tuneIds) {}

	private final DatabaseClient client;

	Sets(DatabaseClient client) {
		this.client = client;
	}

	static String keyOf(List<String> tuneIds) {
		return String.join(",", tuneIds);
	}

	/** Stores the Set; true when it was new, false when another Set had the same list already. */
	Mono<Boolean> insert(List<String> tuneIds, String createdBy) {
		var id = UUID.randomUUID().toString();
		return client.sql("INSERT INTO set_sets (id, tunes_key, created_by) VALUES (:id, :key, :by) ON CONFLICT (tunes_key) DO NOTHING")
				.bind("id", id).bind("key", keyOf(tuneIds)).bind("by", createdBy)
				.fetch().rowsUpdated()
				.flatMap(rows -> {
					if (rows == 0) {
						return Mono.just(false);
					}
					return Flux.range(0, tuneIds.size())
							.concatMap(position -> client.sql("INSERT INTO set_set_tunes (set_id, position, tune_id) VALUES (:set, :position, :tune)")
									.bind("set", id).bind("position", position).bind("tune", tuneIds.get(position))
									.fetch().rowsUpdated())
							.then(Mono.just(true));
				});
	}

	Mono<Row> findByList(List<String> tuneIds) {
		return rows("WHERE s.tunes_key = :key", spec -> spec.bind("key", keyOf(tuneIds))).next();
	}

	Mono<Row> findById(String id) {
		return rows("WHERE s.id = :id", spec -> spec.bind("id", id)).next();
	}

	/** The Sets with these ids; ids that match none are ignored. */
	Flux<Row> findAllById(java.util.Collection<String> ids) {
		return ids.isEmpty() ? Flux.empty() : rows("WHERE s.id IN (:ids)", spec -> spec.bind("ids", ids));
	}

	/** Every Set, or those with the Tune. */
	Flux<Row> findAll(String tuneId) {
		return tuneId == null || tuneId.isBlank()
				? rows("", spec -> spec)
				: rows("WHERE s.id IN (SELECT set_id FROM set_set_tunes WHERE tune_id = :tune)", spec -> spec.bind("tune", tuneId));
	}

	private Flux<Row> rows(String where, java.util.function.UnaryOperator<DatabaseClient.GenericExecuteSpec> binder) {
		var spec = binder.apply(client.sql("""
				SELECT s.id, i.tune_id FROM set_sets s JOIN set_set_tunes i ON i.set_id = s.id
				%s ORDER BY s.created_at, s.id, i.position""".formatted(where)));
		return spec.map(row -> new String[]{row.get("id", String.class), row.get("tune_id", String.class)}).all()
				.collectList()
				.flatMapMany(pairs -> {
					var bySet = new LinkedHashMap<String, List<String>>();
					pairs.forEach(pair -> bySet.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair[1]));
					return Flux.fromIterable(bySet.entrySet()).map(e -> new Row(e.getKey(), e.getValue()));
				});
	}
}

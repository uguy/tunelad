package org.tunelad.tune.internal;

import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.tunelad.tune.DanceType;
import org.tunelad.tune.TuneDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The tune_tunes table. Accounts are known by id only. */
@Component
class Tunes {

	private static final String COLUMNS = "id, name, aliases, author, dance_type, usual_key, source_id, reference_abc";

	private final DatabaseClient client;

	Tunes(DatabaseClient client) {
		this.client = client;
	}

	/** Stores the tune; empty when a tune with the same thesession.org id exists already. */
	Mono<TuneDTO> insert(TuneDTO tune, String createdBy) {
		var id = UUID.randomUUID().toString();
		var spec = client.sql("""
						INSERT INTO tune_tunes (id, name, aliases, author, dance_type, usual_key, source_id, reference_abc, created_by)
						VALUES (:id, :name, :aliases, :author, :type, :key, :source, :abc, :by)
						ON CONFLICT (source_id) DO NOTHING""")
				.bind("id", id).bind("name", tune.name())
				.bind("aliases", tune.aliases().toArray(String[]::new))
				.bind("type", tune.danceType().value())
				.bind("by", createdBy);
		spec = bindNullable(spec, "author", tune.author());
		spec = bindNullable(spec, "key", tune.key());
		spec = bindNullable(spec, "abc", tune.abc());
		spec = tune.sourceId() == null ? spec.bindNull("source", Integer.class) : spec.bind("source", tune.sourceId());
		return spec.fetch().rowsUpdated().filter(rows -> rows > 0).flatMap(rows -> findById(id));
	}

	private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, String value) {
		return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
	}

	Mono<TuneDTO> findById(String id) {
		return client.sql("SELECT " + COLUMNS + " FROM tune_tunes WHERE id = :id").bind("id", id).map(Tunes::map).one();
	}

	Flux<TuneDTO> findAllById(java.util.Collection<String> ids) {
		if (ids.isEmpty()) {
			return Flux.empty();
		}
		return client.sql("SELECT " + COLUMNS + " FROM tune_tunes WHERE id IN (:ids)").bind("ids", ids).map(Tunes::map).all();
	}

	Mono<TuneDTO> findBySourceId(int sourceId) {
		return client.sql("SELECT " + COLUMNS + " FROM tune_tunes WHERE source_id = :source").bind("source", sourceId).map(Tunes::map).one();
	}

	/** Tunes by name, optionally of a Dance type and with the text in the name or an alias, ignoring case. */
	Flux<TuneDTO> search(DanceType type, String text) {
		var sql = new StringBuilder("SELECT " + COLUMNS + " FROM tune_tunes WHERE true");
		if (type != null) {
			sql.append(" AND dance_type = :type");
		}
		if (text != null && !text.isBlank()) {
			sql.append(" AND (name ILIKE :q OR EXISTS (SELECT 1 FROM unnest(aliases) alias WHERE alias ILIKE :q))");
		}
		sql.append(" ORDER BY lower(name), id");
		var spec = client.sql(sql.toString());
		if (type != null) {
			spec = spec.bind("type", type.value());
		}
		if (text != null && !text.isBlank()) {
			spec = spec.bind("q", "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
		return spec.map(Tunes::map).all();
	}

	private static TuneDTO map(Readable row) {
		var aliases = row.get("aliases", String[].class);
		return new TuneDTO(row.get("id", String.class), row.get("name", String.class),
				aliases == null ? List.of() : new ArrayList<>(List.of(aliases)),
				row.get("author", String.class), DanceType.parse(row.get("dance_type", String.class)),
				row.get("usual_key", String.class), row.get("source_id", Integer.class), row.get("reference_abc", String.class));
	}
}

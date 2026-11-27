package org.tunelad.tune.internal;

import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.infrastructure.BadRequestException;
import org.tunelad.infrastructure.NotFoundException;
import org.tunelad.tune.DanceType;
import org.tunelad.tune.TuneDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** Creates, imports and lists Tunes. */
@Service
public class TuneService {

	/** A Tune and whether this call created it. */
	public record Imported(TuneDTO tune, boolean created) {}

	private final Tunes tunes;
	private final TuneSource source;

	TuneService(Tunes tunes, TuneSource source) {
		this.tunes = tunes;
		this.source = source;
	}

	/** Creates a Tune by hand; the name is required and blanks are dropped from the aliases. */
	public Mono<TuneDTO> create(AccountRef account, TuneDTO input) {
		if (input.name() == null || input.name().isBlank() || input.danceType() == null) {
			return Mono.error(new BadRequestException("A tune needs a name and a dance type."));
		}
		var aliases = input.aliases() == null ? List.<String>of()
				: input.aliases().stream().filter(a -> a != null && !a.isBlank()).map(String::trim).distinct().toList();
		var tune = new TuneDTO(null, input.name().trim(), aliases, blankToNull(input.author()), input.danceType(),
				blankToNull(input.key()), null, blankToNull(input.abc()));
		return tunes.insert(tune, account.id());
	}

	/**
	 * The Tune of this thesession.org id: the existing one, or a new one read from thesession.org. Fails with
	 * {@link NotFoundException} when thesession.org does not know the id.
	 */
	public Mono<Imported> importTune(AccountRef account, int sourceId) {
		return tunes.findBySourceId(sourceId).map(tune -> new Imported(tune, false))
				.switchIfEmpty(Mono.defer(() -> source.fetch(sourceId)
						.switchIfEmpty(Mono.error(() -> new NotFoundException("thesession.org has no tune " + sourceId)))
						.flatMap(found -> tunes.insert(new TuneDTO(null, found.name(), found.aliases(), found.author(),
								found.danceType(), found.key(), found.sourceId(), found.abc()), account.id()))
						.map(tune -> new Imported(tune, true))
						// two imports of one id at once: the loser's insert did nothing, so read the winner's
						.switchIfEmpty(Mono.defer(() -> tunes.findBySourceId(sourceId).map(tune -> new Imported(tune, false))))));
	}

	public Flux<TuneDTO> list(DanceType type, String text) {
		return tunes.search(type, text);
	}

	public Mono<TuneDTO> findById(String id) {
		return tunes.findById(id).switchIfEmpty(Mono.error(() -> new NotFoundException("No tune found for id: " + id)));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

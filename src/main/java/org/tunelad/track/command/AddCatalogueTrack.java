package org.tunelad.track.command;

import org.tunelad.account.AccountRef;

import java.util.List;

/**
 * Puts a Catalogue Track (a Track with no audio yet) at a position of an Album, with the tunes listed for it in
 * order. A listed tune with no {@code tuneId} stays unresolved. The Track gets a Set only when every listed tune
 * resolves; {@code by} is the creator of that Set.
 */
public record AddCatalogueTrack(String albumId, String title, int position, List<Listed> tunes, AccountRef by) implements TrackCommand {

	/** A tune as listed on the Album: its name, and its Tune id when known. */
	public record Listed(String name, String tuneId) {
	}
}

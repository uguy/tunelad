package org.tunelad.track.internal;

import reactor.core.publisher.Flux;

import java.util.Map;

/** The thesession.org data dump as the recordings import reads it: one map per CSV row, keyed by the column names. */
interface AlbumDump {

	/** recordings.csv: one row per tune on a track of a recording (id, artist, recording, track, number, tune, tune_id). */
	Flux<Map<String, String>> recordings();
}

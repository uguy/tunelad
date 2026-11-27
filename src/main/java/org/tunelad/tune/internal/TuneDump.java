package org.tunelad.tune.internal;

import reactor.core.publisher.Flux;

import java.util.Map;

/** The thesession.org data dump: one map per CSV row, keyed by the column names. */
interface TuneDump {

	/** tunes.csv: one row per setting. */
	Flux<Map<String, String>> tunes();

	/** aliases.csv: one row per alias. */
	Flux<Map<String, String>> aliases();
}

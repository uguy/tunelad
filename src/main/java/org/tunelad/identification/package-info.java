/**
 * Identification: after a Track is saved, hears which of the Tunes of its Set are played in its audio and proposes
 * where each starts; a Track without a Set is listened to for any Tune, and given the Set of those it is confident about. The analysis itself is done by a sidecar service, behind {@code IdentificationSource}.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure", "account", "media", "tune", "set", "track", "track::event", "track::command" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.identification;

/**
 * Identification: after a Track is saved, hears which of the Tunes of its Set are played in its audio and proposes
 * where each starts. The analysis itself is done by a sidecar service, behind {@code IdentificationSource}.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure", "account", "media", "tune", "track", "track::event", "track::command" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.identification;

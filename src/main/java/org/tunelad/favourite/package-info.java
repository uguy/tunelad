/**
 * A user's favourite tracks: marking a track, unmarking it and listing the tracks a user marked, most recent first.
 * Tracks are known by id only; a deleted track leaves every list.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure", "account", "track", "track::event" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.favourite;

/**
 * A user's playlists: private, named, ordered lists of tracks. Tracks are known by id only, and a deleted track
 * leaves every playlist that held it.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure", "account", "track", "track::event" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.playlist;

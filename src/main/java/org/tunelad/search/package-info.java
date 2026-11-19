/**
 * Track search, as a read model: its own collection, kept up to date from track events.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "track", "track::event" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.search;

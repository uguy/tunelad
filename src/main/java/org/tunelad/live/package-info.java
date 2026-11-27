/**
 * Live updates for the UI: tells browsers, as it happens, that something they display has changed. Events of the
 * modules that own the state ({@code search}, {@code media}) are published on NATS, so that every instance of the
 * application hears them, and each instance streams them to its connected browsers as Server-Sent Events.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "search::event", "media::event", "favourite::event", "playlist::event", "account" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.live;

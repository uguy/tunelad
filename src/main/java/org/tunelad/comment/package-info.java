/**
 * Comments: remarks an Account leaves on a Track, a Tune or a Set. Flat plain text, readable by anyone, edited and
 * deleted by their author or an Administrator. Subjects are known by id only; a deleted Track takes its Comments.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure", "account", "track", "track::event", "tune", "set" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.comment;

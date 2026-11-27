/**
 * Sets: ordered lists of Tunes, one Set per distinct list, shared by all accounts. A Set exists on its own, with or
 * without recordings. Tunes are known by id and read through {@link org.tunelad.tune.TuneCatalog}.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure", "account", "tune" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.set;

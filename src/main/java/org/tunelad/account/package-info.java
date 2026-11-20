/**
 * Accounts of the people using Tune Lad: one account per identity (OpenID Connect issuer and subject), created the
 * first time that identity authenticates, by browser login or bearer token. Other modules resolve the current user
 * through {@link org.tunelad.account.CurrentAccount}.
 */
@org.springframework.modulith.ApplicationModule(
		allowedDependencies = { "infrastructure" }
)
@org.springframework.lang.NonNullApi
package org.tunelad.account;

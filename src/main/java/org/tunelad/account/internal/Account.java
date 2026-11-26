package org.tunelad.account.internal;

import java.time.Instant;

/** An account, identified by the identity provider's issuer and subject. */
record Account(
        String id,
        String issuer,
        String subject,
        String displayName,
        String email,
        Instant createdAt,
        Instant lastLoginAt) {
}

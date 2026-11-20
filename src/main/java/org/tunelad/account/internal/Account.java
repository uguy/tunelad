package org.tunelad.account.internal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** An account, identified by the identity provider's issuer and subject. */
@Document(collection = "accounts")
@CompoundIndex(name = "identity", def = "{'issuer': 1, 'subject': 1}", unique = true)
record Account(
        @Id String id,
        String issuer,
        String subject,
        String displayName,
        String email,
        Instant createdAt,
        Instant lastLoginAt) {
}

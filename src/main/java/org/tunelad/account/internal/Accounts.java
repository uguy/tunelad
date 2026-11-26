package org.tunelad.account.internal;

import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

/**
 * Stores accounts. Both writes are single atomic upserts on the unique (issuer, subject) constraint, so concurrent
 * first requests of the same identity end up with one account.
 */
@Component
@RequiredArgsConstructor
class Accounts {

    private static final String RETURNING =
            " RETURNING id, issuer, subject, display_name, email, created_at, last_login_at";

    private final DatabaseClient client;

    private final Clock clock = Clock.systemUTC();

    /** The account of this identity, created from its claims if it has none yet; an existing one is left as is. */
    Mono<Account> findOrRegister(Identity identity) {
        // The update changes nothing: it only makes the statement return the existing row
        return upsert(identity, null, "issuer = account_accounts.issuer");
    }

    /** Creates or refreshes the account of an identity that just logged in, from its current claims. */
    Mono<Account> registerLogin(Identity identity) {
        return upsert(identity, clock.instant(),
                "display_name = EXCLUDED.display_name, email = EXCLUDED.email, last_login_at = EXCLUDED.last_login_at");
    }

    private Mono<Account> upsert(Identity identity, Instant lastLoginAt, String onConflict) {
        var now = clock.instant();
        var spec = client.sql("""
                INSERT INTO account_accounts (issuer, subject, display_name, email, created_at, last_login_at)
                VALUES (:issuer, :subject, :displayName, :email, :createdAt, :lastLoginAt)
                ON CONFLICT (issuer, subject) DO UPDATE SET\s""" + onConflict + RETURNING)
                .bind("issuer", identity.issuer())
                .bind("subject", identity.subject())
                .bind("createdAt", now);
        spec = identity.displayName() == null ? spec.bindNull("displayName", String.class)
                : spec.bind("displayName", identity.displayName());
        spec = identity.email() == null ? spec.bindNull("email", String.class) : spec.bind("email", identity.email());
        spec = lastLoginAt == null ? spec.bindNull("lastLoginAt", Instant.class) : spec.bind("lastLoginAt", lastLoginAt);
        return spec.map(Accounts::toAccount).one();
    }

    private static Account toAccount(Readable row) {
        return new Account(row.get("id", String.class), row.get("issuer", String.class),
                row.get("subject", String.class), row.get("display_name", String.class),
                row.get("email", String.class), row.get("created_at", Instant.class),
                row.get("last_login_at", Instant.class));
    }
}

package org.tunelad.account.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Stores accounts. Both writes are single atomic upserts on the unique (issuer, subject) index, so concurrent
 * first requests of the same identity end up with one account.
 */
@Component
@RequiredArgsConstructor
class Accounts {

    private final ReactiveMongoTemplate mongo;

    private final Clock clock = Clock.systemUTC();

    /** The account of this identity, created from its claims if it has none yet; an existing one is left as is. */
    Mono<Account> findOrRegister(Identity identity) {
        var update = new Update()
                .setOnInsert("displayName", identity.displayName())
                .setOnInsert("email", identity.email())
                .setOnInsert("createdAt", clock.instant());
        return upsert(identity, update);
    }

    /** Creates or refreshes the account of an identity that just logged in, from its current claims. */
    Mono<Account> registerLogin(Identity identity) {
        var now = clock.instant();
        var update = new Update()
                .set("displayName", identity.displayName())
                .set("email", identity.email())
                .set("lastLoginAt", now)
                .setOnInsert("createdAt", now);
        return upsert(identity, update);
    }

    private Mono<Account> upsert(Identity identity, Update update) {
        var query = Query.query(where("issuer").is(identity.issuer()).and("subject").is(identity.subject()));
        var options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongo.findAndModify(query, update, options, Account.class)
                // Two upserts racing to insert the same identity: the loser finds the winner's account
                .onErrorResume(DuplicateKeyException.class, e -> mongo.findAndModify(query, update, options, Account.class));
    }
}

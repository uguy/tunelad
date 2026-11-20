package org.tunelad.account;

import reactor.core.publisher.Mono;

/** The account of the user making the current request. */
public interface CurrentAccount {

    /**
     * Resolves the authenticated user of the current request into their account, registering it if this identity
     * has never been seen. Empty when the request is not authenticated.
     */
    Mono<AccountRef> get();
}

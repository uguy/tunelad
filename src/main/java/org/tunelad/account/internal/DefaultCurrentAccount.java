package org.tunelad.account.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
class DefaultCurrentAccount implements CurrentAccount {

    private final Accounts accounts;

    @Override
    public Mono<AccountRef> get() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .mapNotNull(authentication -> Identity.of(authentication).orElse(null))
                .flatMap(accounts::findOrRegister)
                .map(account -> new AccountRef(account.id(), account.displayName()));
    }
}

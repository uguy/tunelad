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

    /** The identity provider's "admin" role, as the security configuration maps it for tokens and logins. */
    static final String ADMIN_AUTHORITY = "ROLE_admin";

    private final Accounts accounts;

    @Override
    public Mono<AccountRef> get() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> Mono.justOrEmpty(Identity.of(authentication))
                        .flatMap(accounts::findOrRegister)
                        .map(account -> new AccountRef(account.id(), account.displayName(), isAdmin(authentication))));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }
}

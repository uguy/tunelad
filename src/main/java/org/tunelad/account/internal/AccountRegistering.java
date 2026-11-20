package org.tunelad.account.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Loads the user of a browser login as Spring Security usually does, then creates or refreshes their account from
 * the provider's current claims. Spring Security's OAuth2 login picks this bean up as its OIDC user service.
 */
@Component
class AccountRegistering implements ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {

    private final ReactiveOAuth2UserService<OidcUserRequest, OidcUser> delegate;

    private final Accounts accounts;

    @Autowired
    AccountRegistering(Accounts accounts) {
        this(new OidcReactiveOAuth2UserService(), accounts);
    }

    AccountRegistering(ReactiveOAuth2UserService<OidcUserRequest, OidcUser> delegate, Accounts accounts) {
        this.delegate = delegate;
        this.accounts = accounts;
    }

    @Override
    public Mono<OidcUser> loadUser(OidcUserRequest request) {
        return delegate.loadUser(request)
                .flatMap(user -> Identity.of(user.getIdToken().getIssuer(), user.getSubject(), user.getClaims())
                        .map(identity -> accounts.registerLogin(identity).thenReturn(user))
                        .orElseGet(() -> Mono.just(user)));
    }
}

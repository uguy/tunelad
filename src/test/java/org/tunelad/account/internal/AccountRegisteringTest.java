package org.tunelad.account.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.TestSecurityConfiguration;
import org.tunelad.TestTokens;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The account side of a browser login, with the provider's user loading stubbed. */
@ApplicationModuleTest
@Import(TestContainerDefinitions.class)
class AccountRegisteringTest {

    @Autowired
    Accounts accounts;

    @Autowired
    ReactiveMongoTemplate mongo;

    @BeforeEach
    void emptyAccounts() {
        mongo.remove(new Query(), Account.class).block();
    }

    @Test
    void first_login_creates_the_account() {
        login("Alice Liddell");

        var account = accounts.findOrRegister(alice("ignored")).block();
        assertThat(account.displayName()).isEqualTo("Alice Liddell");
        assertThat(account.lastLoginAt()).isNotNull();
    }

    // Spec: Account display name from the identity / Name changed at the provider
    @Test
    void name_changed_at_the_provider() {
        var before = accounts.findOrRegister(alice("Alice Liddell")).block();

        login("Alice L.");

        var after = accounts.findOrRegister(alice("Alice Liddell")).block();
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.displayName()).isEqualTo("Alice L.");
    }

    private void login(String name) {
        var now = Instant.now();
        var idToken = OidcIdToken.withTokenValue("id-token")
                .issuer(TestTokens.ISSUER)
                .subject("sub-alice")
                .claim("name", name)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        var user = new DefaultOidcUser(List.of(), idToken);
        var request = new OidcUserRequest(TestSecurityConfiguration.KEYCLOAK,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60)),
                idToken);

        new AccountRegistering(r -> Mono.just(user), accounts).loadUser(request).block();
    }

    private static Identity alice(String name) {
        return new Identity(TestTokens.ISSUER, "sub-alice", Map.of("name", name));
    }
}

package org.tunelad.account;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.tunelad.AbstractApiTest;
import org.tunelad.TestTokens;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for openspec/specs/account, for API clients authenticating with bearer tokens: one test per spec scenario,
 * named after it. Browser sessions are covered by BrowserSessionSecurityTest.
 */
class AccountApiTest extends AbstractApiTest {

    private static final String ALICE = TestTokens.bearer("alice", "Alice Liddell");
    private static final String BOB = TestTokens.bearer("bob", "Bob Smith");

    // --- Requirement: Authenticate API calls with a bearer token -----------------------------

    @Test
    void valid_token() {
        assertThat(me(ALICE).displayName()).isEqualTo("Alice Liddell");
    }

    @Test
    void invalid_token() {
        meRequest(TestTokens.foreignSignature()).expectStatus().isUnauthorized();
    }

    @Test
    void token_for_another_audience_is_rejected() {
        meRequest(TestTokens.otherAudience()).expectStatus().isUnauthorized();
    }

    @Test
    void malformed_token_is_rejected() {
        meRequest("not-a-jwt").expectStatus().isUnauthorized();
    }

    // --- Requirement: One account per identity -------------------------------------------------

    @Test
    void first_authentication_creates_an_account() {
        assertThat(me(ALICE).id()).isNotBlank();
    }

    @Test
    void same_identity_same_account() {
        var first = me(ALICE);

        // A new token for the same identity
        var second = me(TestTokens.bearer("alice", "Alice Liddell"));

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void different_identities_different_accounts() {
        assertThat(me(ALICE).id()).isNotEqualTo(me(BOB).id());
    }

    // --- Requirement: Account display name from the identity ---------------------------------

    @Test
    void name_claim_present() {
        assertThat(me(ALICE).displayName()).isEqualTo("Alice Liddell");
    }

    @Test
    void only_a_username() {
        assertThat(me(TestTokens.bearer("carol", null)).displayName()).isEqualTo("carol");
    }

    @Test
    void only_an_email() {
        var token = TestTokens.withClaims("sub-dave", Map.of("email", "dave@tunelad.test"));

        assertThat(me(token).displayName()).isEqualTo("dave@tunelad.test");
    }

    // --- Requirement: Current account endpoint -------------------------------------------------

    @Test
    void authenticated() {
        var account = me(ALICE);

        assertThat(account.id()).isNotBlank();
        assertThat(account.displayName()).isEqualTo("Alice Liddell");
    }

    @Test
    void anonymous() {
        client.get().uri("/api/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION);
    }

    private AccountRef me(String token) {
        return meRequest(token)
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();
    }

    private WebTestClient.ResponseSpec meRequest(String token) {
        return client.get().uri("/api/me").headers(headers -> headers.setBearerAuth(token)).exchange();
    }
}

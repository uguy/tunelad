package org.tunelad.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.OidcLoginMutator;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.TestSecurityConfiguration;
import org.tunelad.TestTokens;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Tests for openspec/specs/account, for browser users: login, the session, CSRF protection and logout. The provider
 * itself is mocked: the login is simulated with {@code mockOidcLogin()}, and KeycloakIntegrationTest covers a real
 * one.
 */
@SpringBootTest
@Import(TestContainerDefinitions.class)
class BrowserSessionSecurityTest {

    private static final String KNEES_UP_MP3 = "mp3/Knees_Up.mp3";

    @Autowired
    ApplicationContext context;

    @Autowired
    ReactiveMongoTemplate mongo;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        mongo.remove(new Query(), "tracks").block();
    }

    // --- Requirement: Log in through an OpenID Connect provider ------------------------------

    @Test
    void start_a_login() {
        client.get().uri("/oauth2/authorization/keycloak").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value(HttpHeaders.LOCATION, location ->
                        assertThat(location).startsWith(TestSecurityConfiguration.KEYCLOAK.getProviderDetails().getAuthorizationUri()));
    }

    @Test
    void provider_list() {
        client.get().uri("/login").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(page -> assertThat(page)
                        .contains("/oauth2/authorization/keycloak")
                        .contains("Keycloak"));
    }

    @Test
    void successful_login() {
        var account = client.mutateWith(alice()).get().uri("/api/me").exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();

        assertThat(account.displayName()).isEqualTo("Alice Liddell");
    }

    // --- Requirement: Log out ------------------------------------------------------------------

    @Test
    void log_out_from_the_browser() {
        client.mutateWith(alice()).mutateWith(csrf()).post().uri("/logout").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location)
                        .startsWith(TestTokens.ISSUER + "/protocol/openid-connect/logout")
                        .contains("id_token_hint=")
                        .contains("post_logout_redirect_uri="));
    }

    // --- Requirement: CSRF protection for browser sessions -------------------------------------

    @Test
    void session_request_without_a_token() {
        client.mutateWith(alice()).post().uri("/api/tracks")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(upload())
                .exchange()
                .expectStatus().isForbidden();

        assertThat(mongo.count(new Query(), "tracks").block()).isZero();
    }

    @Test
    void session_request_with_a_token() {
        var token = csrfCookie();

        client.mutateWith(alice()).post().uri("/api/tracks")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(upload())
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void bearer_request() {
        client.post().uri("/api/tracks")
                .headers(headers -> headers.setBearerAuth(TestTokens.bearer("alice", "Alice Liddell")))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(upload())
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void csrf_token_cookie_is_readable_by_the_spa() {
        client.get().uri("/api/tracks").exchange()
                .expectCookie().exists("XSRF-TOKEN")
                .expectCookie().httpOnly("XSRF-TOKEN", false);
    }

    private static OidcLoginMutator alice() {
        return mockOidcLogin()
                .clientRegistration(TestSecurityConfiguration.KEYCLOAK)
                .idToken(token -> token
                        .issuer(TestTokens.ISSUER)
                        .subject("sub-alice")
                        .claim("name", "Alice Liddell")
                        .claim("preferred_username", "alice"));
    }

    private String csrfCookie() {
        var cookie = client.get().uri("/api/tracks").exchange()
                .returnResult(String.class).getResponseCookies().getFirst("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private static BodyInserters.MultipartInserter upload() {
        var builder = new MultipartBodyBuilder();
        builder.part("title", "Knees Up");
        builder.part("description", "A reel and a song");
        builder.part("file", new ClassPathResource(KNEES_UP_MP3));
        return BodyInserters.fromMultipartData(builder.build());
    }
}

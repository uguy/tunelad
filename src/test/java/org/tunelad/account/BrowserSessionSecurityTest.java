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
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;
import org.tunelad.TestContainerDefinitions;
import org.tunelad.TestSecurityConfiguration;
import org.tunelad.TestTokens;
import org.tunelad.track.TrackDTO;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Tests for openspec/specs/account, for browser users: login, the session, CSRF protection and logout.
 * <p>
 * The provider's side of a login is not run: each test stores a logged-in session, as a successful login leaves
 * it, in the application's session store, and sends its cookie. KeycloakIntegrationTest runs a real login.
 */
@SpringBootTest
@Import(TestContainerDefinitions.class)
class BrowserSessionSecurityTest {

    private static final String KNEES_UP_MP3 = "mp3/Knees_Up.mp3";

    @Autowired
    ApplicationContext context;

    @Autowired
    WebSessionManager sessionManager;

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
        var session = aliceSession();

        var account = client.get().uri("/api/me").cookie("SESSION", session).exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();

        assertThat(account.displayName()).isEqualTo("Alice Liddell");
    }

    // --- Requirement: Log out ------------------------------------------------------------------

    @Test
    void log_out_from_the_browser() {
        var session = aliceSession();
        var csrf = csrfToken();

        client.post().uri("/logout")
                .cookie("SESSION", session)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location)
                        .startsWith(TestTokens.ISSUER + "/protocol/openid-connect/logout")
                        .contains("id_token_hint=")
                        .contains("post_logout_redirect_uri="));

        client.get().uri("/api/me").cookie("SESSION", session).exchange()
                .expectStatus().isUnauthorized();
    }

    // --- Requirement: CSRF protection for browser sessions -------------------------------------

    @Test
    void session_request_without_a_token() {
        client.post().uri("/api/tracks")
                .cookie("SESSION", aliceSession())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(upload())
                .exchange()
                .expectStatus().isForbidden();

        assertThat(mongo.count(new Query(), "tracks").block()).isZero();
    }

    @Test
    void session_request_with_a_token() {
        var csrf = csrfToken();

        client.post().uri("/api/tracks")
                .cookie("SESSION", aliceSession())
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(upload())
                .exchange()
                .expectStatus().is2xxSuccessful();

        var tracks = client.get().uri("/api/tracks").exchange()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
        assertThat(tracks).singleElement()
                .satisfies(track -> assertThat(track.owner().displayName()).isEqualTo("Alice Liddell"));
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

    /** Stores a session in which alice has logged in through Keycloak, and returns its id (the SESSION cookie). */
    private String aliceSession() {
        var now = Instant.now();
        var idToken = OidcIdToken.withTokenValue("id-token")
                .issuer(TestTokens.ISSUER)
                .subject("sub-alice")
                .claim("name", "Alice Liddell")
                .claim("preferred_username", "alice")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        var user = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
        var login = new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak");

        var store = ((DefaultWebSessionManager) sessionManager).getSessionStore();
        var session = store.createWebSession().block();
        session.getAttributes().put(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME,
                new SecurityContextImpl(login));
        session.start();
        session.save().block();
        return session.getId();
    }

    private String csrfToken() {
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

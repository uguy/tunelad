package org.tunelad.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
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
import java.util.Map;

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
    DatabaseClient db;

    @Autowired
    GrantedAuthoritiesMapper authoritiesMapper;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        db.sql("DELETE FROM track_tracks").then().block();
    }

    // --- Requirement: Log in through an OpenID Connect provider ------------------------------

    @Test
    void start_a_login() {
        client.get().uri("/oauth2/authorization/keycloak").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value(HttpHeaders.LOCATION, location ->
                        assertThat(location).startsWith(TestSecurityConfiguration.KEYCLOAK.getProviderDetails().getAuthorizationUri()));
    }

    /** The buttons themselves are checked in the browser, by app/e2e/account.test.js: here, the SPA serves the page. */
    @Test
    void provider_list() {
        client.get().uri("/login").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class).value(page -> assertThat(page)
                        .contains("data-sveltekit-preload-data")
                        .doesNotContain("Please sign in"));
    }

    /** A reload on a page of the SPA must reach the SPA, not a 404. */
    @Test
    void reloading_a_page_of_the_app_serves_the_app() {
        for (var path : List.of("/account", "/tracks/favourites", "/tracks/playlists", "/tracks/playlists/some-id")) {
            client.get().uri(path).accept(MediaType.TEXT_HTML).exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).value(page -> assertThat(page).as(path).contains("data-sveltekit-preload-data"));
        }
    }

    @Test
    void provider_callback_is_not_the_login_page() {
        client.get().uri("/login/oauth2/code/keycloak?code=x&state=y").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location).contains("/login?error"));
    }

    @Test
    void successful_login() {
        var session = aliceSession();

        var account = client.get().uri("/api/me").cookie("SESSION", session).exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();

        assertThat(account.displayName()).isEqualTo("Alice Liddell");
    }

    // --- Requirement: Administrators come from the identity provider's admin role -------------

    @Test
    void browser_login_with_the_admin_role() {
        var session = loginSession("sub-admin", Map.of("preferred_username", "admin", "roles", List.of("admin")));

        var account = client.get().uri("/api/me").cookie("SESSION", session).exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();

        assertThat(account.admin()).isTrue();
    }

    @Test
    void browser_login_without_the_admin_role() {
        var account = client.get().uri("/api/me").cookie("SESSION", aliceSession()).exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();

        assertThat(account.admin()).isFalse();
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

        assertThat(db.sql("SELECT count(*) AS n FROM track_tracks").map(row -> row.get("n", Long.class)).one().block())
                .isZero();
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
                .expectStatus().isCreated();

        var tracks = client.get().uri("/api/tracks").exchange()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
        assertThat(tracks).singleElement()
                .satisfies(track -> assertThat(track.owner().displayName()).isEqualTo("Alice Liddell"));
    }

    /** The favourite and playlist routes are state-changing routes like the others: a session needs the token. */
    @Test
    void favourite_and_playlist_changes_from_a_session_need_the_token() {
        var session = aliceSession();

        client.put().uri("/api/tracks/any/favourite").cookie("SESSION", session).exchange()
                .expectStatus().isForbidden();
        client.delete().uri("/api/tracks/any/favourite").cookie("SESSION", session).exchange()
                .expectStatus().isForbidden();
        client.post().uri("/api/playlists").cookie("SESSION", session)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"name\":\"x\"}").exchange()
                .expectStatus().isForbidden();
        client.put().uri("/api/playlists/any/tracks/any").cookie("SESSION", session).exchange()
                .expectStatus().isForbidden();
        client.delete().uri("/api/playlists/any").cookie("SESSION", session).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void favourite_change_from_a_session_with_the_token_gets_past_csrf() {
        var csrf = csrfToken();

        var session = aliceSession();

        // 404 and not 403: the track does not exist, but the request was let through
        client.put().uri("/api/tracks/unknown/favourite")
                .cookie("SESSION", session)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .exchange()
                .expectStatus().isNotFound();
        client.post().uri("/api/playlists")
                .cookie("SESSION", session)
                .cookie("XSRF-TOKEN", csrf)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"name\":\"Session tunes\"}")
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void bearer_request() {
        client.post().uri("/api/tracks")
                .headers(headers -> headers.setBearerAuth(TestTokens.bearer("alice", "Alice Liddell")))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(upload())
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void csrf_token_cookie_is_readable_by_the_spa() {
        client.get().uri("/api/tracks").exchange()
                .expectCookie().exists("XSRF-TOKEN")
                .expectCookie().httpOnly("XSRF-TOKEN", false);
    }

    /** Stores a session in which alice has logged in through Keycloak, and returns its id (the SESSION cookie). */
    private String aliceSession() {
        return loginSession("sub-alice", Map.of("name", "Alice Liddell", "preferred_username", "alice"));
    }

    /**
     * Stores a session as a successful login leaves it, for an identity with the given claims. Its authorities go
     * through the application's mapper, as OAuth2 login does.
     */
    private String loginSession(String subject, Map<String, Object> claims) {
        var now = Instant.now();
        var builder = OidcIdToken.withTokenValue("id-token")
                .issuer(TestTokens.ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        claims.forEach(builder::claim);
        var idToken = builder.build();
        var authorities = authoritiesMapper.mapAuthorities(List.of(new OidcUserAuthority(idToken)));
        var user = new DefaultOidcUser(authorities, idToken);
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

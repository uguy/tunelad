package org.tunelad.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import org.tunelad.MongoContainerDefinition;
import org.tunelad.track.TrackDTO;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application against a real Keycloak, with the realm local development uses (docker/keycloak): the one test
 * that checks the provider configuration end to end, where the other tests stand in for the provider.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MongoContainerDefinition.class)
class KeycloakIntegrationTest {

    /** The image docker-compose.yaml runs, so tests and local development use the same Keycloak. */
    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.4.7")
            .withCommand("start-dev", "--import-realm")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/keycloak/tunelad-realm.json"),
                    "/opt/keycloak/data/import/tunelad-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/tunelad").forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", KeycloakIntegrationTest::issuer);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakIntegrationTest::issuer);
    }

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    void token_from_the_realm_authenticates_api_calls() {
        var account = client.get().uri("/api/me")
                .headers(headers -> headers.setBearerAuth(aliceToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountRef.class).returnResult().getResponseBody();

        assertThat(account.id()).isNotBlank();
        assertThat(account.displayName()).isEqualTo("Alice Liddell");
    }

    @Test
    void upload_with_a_token_from_the_realm_records_the_owner() {
        var builder = new MultipartBodyBuilder();
        builder.part("title", "Knees Up");
        builder.part("description", "A reel and a song");
        builder.part("file", new ClassPathResource("mp3/Knees_Up.mp3"));

        client.post().uri("/api/tracks")
                .headers(headers -> headers.setBearerAuth(aliceToken()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().is2xxSuccessful();

        var tracks = client.get().uri("/api/tracks").exchange()
                .expectBodyList(TrackDTO.class).returnResult().getResponseBody();
        assertThat(tracks).filteredOn(track -> track.title().equals("Knees Up"))
                .singleElement()
                .satisfies(track -> assertThat(track.owner().displayName()).isEqualTo("Alice Liddell"));
    }

    @Test
    void login_starts_at_the_realm() {
        client.get().uri("/oauth2/authorization/keycloak").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value(HttpHeaders.LOCATION, location ->
                        assertThat(location).startsWith(issuer() + "/protocol/openid-connect/auth"));
    }

    private static String issuer() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/tunelad";
    }

    /** An access token for alice, with the dev realm's password grant. */
    private static String aliceToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "tunelad");
        form.add("client_secret", "tunelad-dev-secret");
        form.add("username", "alice");
        form.add("password", "alice");
        var response = WebClient.create(issuer() + "/protocol/openid-connect/token").post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));
        return (String) response.get("access_token");
    }
}

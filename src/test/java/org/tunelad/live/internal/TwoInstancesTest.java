package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.tunelad.TestSecurityConfiguration;
import org.tunelad.TuneladApplication;
import org.tunelad.track.TrackDTO;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Two instances of the application on one database and one NATS: what the second one raises reaches a browser that is
 * connected to the first. The test's own application is the first instance.
 */
class TwoInstancesTest extends AbstractLiveTest {

    @Autowired PostgreSQLContainer postgres;

    @Test
    void a_waveform_computed_on_another_instance_reaches_a_client_of_this_one() {
        var stream = follow(null);

        try (var second = secondInstance()) {
            var other = WebTestClient.bindToServer()
                    .baseUrl("http://localhost:" + second.getEnvironment().getProperty("local.server.port"))
                    .responseTimeout(Duration.ofSeconds(30))
                    .build();
            var builder = new MultipartBodyBuilder();
            builder.part("title", "Rainfall");
            builder.part("description", "Rain on tin");
            builder.part("setId", defaultSetId());
            builder.part("file", new ClassPathResource(KNEES_UP_MP3));

            var id = other.post().uri("/api/tracks")
                    .headers(headers -> headers.setBearerAuth(UPLOADER_TOKEN))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(TrackDTO.class).returnResult().getResponseBody().id();

            await().atMost(Duration.ofSeconds(30)).until(() ->
                    stream.dataOf("waveform.computed").contains("{\"type\":\"waveform.computed\",\"id\":\"" + id + "\"}")
                            && stream.dataOf("track.indexed").contains("{\"type\":\"track.indexed\",\"id\":\"" + id + "\"}"));
            assertThat(second.isActive()).isTrue();
        }
    }

    /**
     * Another instance of the application, on a port of its own, sharing this one's PostgreSQL and NATS. The settings
     * are command line arguments: as defaults of the builder, they would lose to the ones in application.yml.
     */
    private ConfigurableApplicationContext secondInstance() {
        return new SpringApplicationBuilder(TuneladApplication.class, TestSecurityConfiguration.class)
                .run(
                        "--server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.r2dbc.url=r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432)
                                + "/" + postgres.getDatabaseName(),
                        "--spring.r2dbc.username=" + postgres.getUsername(),
                        "--spring.r2dbc.password=" + postgres.getPassword(),
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--tunelad.live.nats.url=" + natsUrl);
    }
}

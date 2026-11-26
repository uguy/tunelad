package org.tunelad.live.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** An idle stream is kept open by a comment line. The interval is shortened here; by default it is 15 seconds. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tunelad.live.heartbeat=1s")
class LiveHeartbeatTest extends AbstractLiveTest {

    @Test
    void a_quiet_stream_receives_comment_lines_and_no_events() {
        var stream = follow(null);

        await().atMost(Duration.ofSeconds(8)).until(() -> stream.comments() >= 3);

        assertThat(stream.events()).isEmpty();
    }
}

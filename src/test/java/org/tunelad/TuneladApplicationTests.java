package org.tunelad;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@Slf4j
@SpringBootTest
@ImportTestcontainers(TestContainerDefinitions.class)
class TuneladApplicationTests {

    @Test
    void context_load() {

    }

}

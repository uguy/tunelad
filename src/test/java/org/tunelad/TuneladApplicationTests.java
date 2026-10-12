package org.tunelad;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootTest
@Import(TestContainerDefinitions.class)
class TuneladApplicationTests {

    @Test
    void context_load() {

    }

}

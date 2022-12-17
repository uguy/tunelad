package org.tunelad;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.tunelad.config.EmbeddedElasticsearch;

import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
@EmbeddedElasticsearch
class TuneladApplicationTests {

	@Test
	void context_load() {

	}

}

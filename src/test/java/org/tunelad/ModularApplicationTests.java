package org.tunelad;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;



@Slf4j
@SpringBootTest
class ModularApplicationTests {

			;
	@Test
	void generate_documentation() {
		var modules = ApplicationModules.of(TuneladApplication.class).verify();
		new Documenter(modules)
				.writeModulesAsPlantUml().writeModuleCanvases();
	}

}

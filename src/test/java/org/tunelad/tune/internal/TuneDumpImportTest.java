package org.tunelad.tune.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TuneDumpImportTest {

	@Test
	void maps_a_row_to_a_tune_with_its_composer_and_aliases() {
		var tune = TuneDumpImport.toTune(Map.of("tune_id", "27", "name", "Cooley's", "type", "reel", "mode", "Edorian",
				"abc", "EBBA B2 EB|", "composer", "Unknown"), Map.of(27, List.of("Cooley's Reel", "cooley's")));

		assertThat(tune.sourceId()).isEqualTo(27);
		assertThat(tune.author()).isEqualTo("Unknown");
		assertThat(tune.aliases()).containsExactly("Cooley's Reel");
		assertThat(tune.abc()).contains("K:Edor").contains("EBBA B2 EB|");
	}

	@Test
	void skips_an_unknown_dance_type_and_an_empty_name() {
		assertThat(TuneDumpImport.toTune(Map.of("tune_id", "1", "name", "X", "type", "foxtrot"), Map.of())).isNull();
		assertThat(TuneDumpImport.toTune(Map.of("tune_id", "1", "name", " ", "type", "jig"), Map.of())).isNull();
	}
}

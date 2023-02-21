package org.tunelad.infrastructure.web;

import java.util.Map;

import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class CustomOsInfoContributor implements InfoContributor {
	@Override
	public void contribute(Builder builder) {
		builder.withDetail("runtime", Map.of(
				"availableProcessors", Runtime.getRuntime().availableProcessors(),
				"maxMemory", Runtime.getRuntime().maxMemory()
		));
	}
}

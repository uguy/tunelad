package org.tunelad.infrastructure.web.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@EnableAspectJAutoProxy(proxyTargetClass = true)
@Configuration
public class WebConfig implements WebFluxConfigurer {

	@Bean
	CorsWebFilter corsFilter() {

		CorsConfiguration config = new CorsConfiguration();

		config.setMaxAge(3600L);
		config.addAllowedOrigin("*");
		config.addAllowedHeader("*");
		config.setAllowedMethods(Arrays.asList("HEAD", "OPTIONS", "GET", "POST", "PUT", "PATCH", "DELETE"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		return new CorsWebFilter(source);
	}

}

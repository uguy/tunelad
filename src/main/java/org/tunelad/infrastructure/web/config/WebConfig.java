package org.tunelad.infrastructure.web.config;

import java.util.Arrays;

import reactor.core.publisher.Mono;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

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

	// Refreshing browser while on url /tracks or about end up with 404 if we don"t redirect to root
	@Component
	public class SpaWebFilter implements WebFilter {
		@Override
		public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
			var path = exchange.getRequest().getURI().getPath();
			if (shouldRedirectToRoot(path)) {
				return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path("/").build()).build());
			}
			return chain.filter(exchange);
		}

		boolean shouldRedirectToRoot(String path) {
			return isNotRoot(path) && isNotStaticResourceCall(path) && isNotApiCall(path);
		}

		private boolean isNotRoot(String path) {
			return !path.equals("/");
		}

		boolean isNotStaticResourceCall(String path) {
			return !path.contains("_app") && !path.contains("images")
					&& !path.contains("json") && !path.contains("html") && !path.contains("txt") && !path.contains("png");
		}

		boolean isNotApiCall(String path) {
			return !path.contains("/api") && !path.contains("/actuator");
		}
	}

}

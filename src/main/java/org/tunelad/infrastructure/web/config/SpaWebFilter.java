package org.tunelad.infrastructure.web.config;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

// Refreshing browser while on url /tracks or /about end up with 404 if we don"t redirect to root
@Slf4j
@Component
public class SpaWebFilter implements WebFilter {
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		var path = exchange.getRequest().getURI().getPath();
		if (shouldRedirectToRoot(path)) {
			log.info("Redirect to root from path: {}", path);
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
				&& !path.contains(".json") && !path.contains(".js") && !path.contains(".css") && !path.contains("html") && !path.contains(".txt")
				&& !path.contains(".png") && !path.contains(".webp") && !path.contains(".webmanifest");
	}

	boolean isNotApiCall(String path) {
		return !path.contains("/api") && !path.contains("/actuator");
	}
}

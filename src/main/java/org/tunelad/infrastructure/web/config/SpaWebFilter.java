package org.tunelad.infrastructure.web.config;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.List;

// Refreshing browser while on url /tracks or /about end up with 404 if we don"t redirect to root
@Slf4j
@Component
public class SpaWebFilter implements WebFilter {

    private static final List<String> UI_PATH_PREFIXES =  List.of("/tracks", "/about");

    @Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		var path = exchange.getRequest().getURI().getPath();
        // Redirect requests of SPA internal navication to root
        if (UI_PATH_PREFIXES.stream().anyMatch(path::equalsIgnoreCase)) {
            log.debug("Redirect to root from path: {}", path);
            return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path("/").build()).build());
        }
		return chain.filter(exchange);
	}
}

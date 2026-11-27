package org.tunelad.infrastructure.web.config;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.List;

// Refreshing browser while on url /tracks or /account end up with 404 if we don"t redirect to root;
// /login is the SPA's login page (the OAuth callback below it, /login/oauth2/code/..., is not matched: exact paths only)
@Slf4j
@Component
public class SpaWebFilter implements WebFilter {

    /** Pages of the SPA, and everything below them (a playlist is at /tracks/playlists/{id}). */
    private static final List<String> UI_PATH_PREFIXES = List.of("/tracks", "/account");
    /** Pages of the SPA with exact paths only: the OAuth callback below /login is not one. */
    private static final List<String> UI_EXACT_PATHS = List.of("/login");

    @Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		var path = exchange.getRequest().getURI().getPath();
        // Redirect requests of SPA internal navigation to root
        if (isUiPath(path)) {
            log.debug("Redirect to root from path: {}", path);
            return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path("/").build()).build());
        }
		return chain.filter(exchange);
	}

    private static boolean isUiPath(String path) {
        var lower = path.toLowerCase(java.util.Locale.ROOT);
        return UI_EXACT_PATHS.contains(lower)
                || UI_PATH_PREFIXES.stream().anyMatch(prefix -> lower.equals(prefix) || lower.startsWith(prefix + "/"));
    }
}

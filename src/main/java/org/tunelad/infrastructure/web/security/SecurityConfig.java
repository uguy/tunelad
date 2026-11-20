package org.tunelad.infrastructure.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Who may call what. API clients authenticate with a bearer access token from the configured OpenID Connect
 * issuer; reading the library stays open to anonymous visitors.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .anyExchange().permitAll())
                // Answers 401 with WWW-Authenticate: Bearer to unauthenticated requests on protected endpoints
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                // Nothing relies on a browser session yet: CSRF protection comes with browser login
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}

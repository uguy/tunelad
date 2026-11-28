package org.tunelad.infrastructure.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationConverterServerWebExchangeMatcher;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

/**
 * Who may call what, and how they authenticate:
 * <ul>
 *     <li>browser users log in through an OpenID Connect provider and keep a server-side session; their
 *     state-changing requests need the CSRF token the SPA reads from the {@code XSRF-TOKEN} cookie;</li>
 *     <li>API clients send a bearer access token from the configured issuer, which needs no CSRF token since
 *     browsers never send a bearer token on their own.</li>
 * </ul>
 * Reading the library stays open to anonymous visitors.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  ReactiveClientRegistrationRepository clientRegistrations) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/me", "/api/me/favourites").authenticated()
                        .pathMatchers(HttpMethod.PUT, "/api/tracks/*/favourite").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/tracks/*/favourite").authenticated()
                        .pathMatchers("/api/playlists", "/api/playlists/**").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/tracks", "/api/albums", "/api/tunes", "/api/tunes/import", "/api/sets").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/tracks/*/comments", "/api/tunes/*/comments", "/api/sets/*/comments").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/comments/*").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/comments/*").authenticated()
                        .pathMatchers(HttpMethod.PUT, "/api/artists/*", "/api/artists/*/members").authenticated()
                        .pathMatchers(HttpMethod.PUT, "/api/tracks/*").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/tracks/*").authenticated()
                        .anyExchange().permitAll())
                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        // The SPA sends back the raw cookie value, not a masked one
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(sessionAuthenticatedChange()))
                .addFilterAfter(csrfCookieWriter(), SecurityWebFiltersOrder.CSRF)
                .logout(logout -> logout.logoutSuccessHandler(providerLogout(clientRegistrations)))
                .build();
    }

    /**
     * Browser logins get the same {@code ROLE_<role>} authorities as bearer tokens (see the resource server's
     * {@code authorities-claim-name} in application.yml). OAuth2 login picks this bean up by itself.
     */
    @Bean
    GrantedAuthoritiesMapper rolesClaimAuthoritiesMapper() {
        return new RolesClaimAuthoritiesMapper();
    }

    /** The API answers 401 without redirecting; browser pages go to the login page, which the SPA serves. */
    private static ServerAuthenticationEntryPoint entryPoint() {
        var entryPoint = new DelegatingServerAuthenticationEntryPoint(
                new DelegateEntry(pathMatchers("/api/**"), new BearerTokenServerAuthenticationEntryPoint()));
        entryPoint.setDefaultEntryPoint(new RedirectServerAuthenticationEntryPoint("/login"));
        return entryPoint;
    }

    /**
     * CSRF only threatens requests the browser authenticates on its own, with the session cookie: protect the
     * state-changing requests whose session holds a login. Bearer requests are exempt, as Spring Security does by
     * default for resource servers; and requests with neither go on to authorization, which answers 401.
     */
    private static ServerWebExchangeMatcher sessionAuthenticatedChange() {
        var sessions = new WebSessionServerSecurityContextRepository();
        ServerWebExchangeMatcher sessionHoldsLogin = exchange -> sessions.load(exchange)
                .flatMap(context -> MatchResult.match())
                .switchIfEmpty(MatchResult.notMatch());
        var bearer = new AuthenticationConverterServerWebExchangeMatcher(new ServerBearerTokenAuthenticationConverter());
        return new AndServerWebExchangeMatcher(
                CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                new NegatedServerWebExchangeMatcher(bearer),
                sessionHoldsLogin);
    }

    /**
     * The CSRF token is only generated, and its cookie written, when something asks for it. A server-rendered form
     * would; the SPA never does, so ask for it on every request.
     */
    private static WebFilter csrfCookieWriter() {
        return (exchange, chain) -> exchange.<Mono<CsrfToken>>getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
                .then(chain.filter(exchange));
    }

    /** Also ends the session at the identity provider, when it supports it, then comes back to the application. */
    private static ServerLogoutSuccessHandler providerLogout(ReactiveClientRegistrationRepository clientRegistrations) {
        var handler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}

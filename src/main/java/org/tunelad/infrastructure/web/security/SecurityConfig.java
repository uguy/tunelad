package org.tunelad.infrastructure.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.ui.LoginPageGeneratingWebFilter;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

/**
 * Who may call what, and how they authenticate:
 * <ul>
 *     <li>browser users log in through an OpenID Connect provider and keep a server-side session; their
 *     state-changing requests need the CSRF token the SPA reads from the {@code XSRF-TOKEN} cookie;</li>
 *     <li>API clients send a bearer access token from the configured issuer; the resource server support exempts
 *     those requests from CSRF, since browsers never send a bearer token on their own.</li>
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
                        .pathMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .anyExchange().permitAll())
                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        // The SPA sends back the raw cookie value, not a masked one
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .addFilterAfter(csrfCookieWriter(), SecurityWebFiltersOrder.CSRF)
                .addFilterAt(loginPage(clientRegistrations), SecurityWebFiltersOrder.LOGIN_PAGE_GENERATING)
                .logout(logout -> logout.logoutSuccessHandler(providerLogout(clientRegistrations)))
                .build();
    }

    /** The API answers 401 without redirecting; browser pages go to the login page, which lists the providers. */
    private static ServerAuthenticationEntryPoint entryPoint() {
        var entryPoint = new DelegatingServerAuthenticationEntryPoint(
                new DelegateEntry(pathMatchers("/api/**"), new BearerTokenServerAuthenticationEntryPoint()));
        entryPoint.setDefaultEntryPoint(new RedirectServerAuthenticationEntryPoint("/login"));
        return entryPoint;
    }

    /**
     * Spring Security's generated {@code /login} page, listing a link per configured provider. It only generates it
     * by itself when the default entry points are kept, and those would redirect anonymous API calls to it.
     */
    private static LoginPageGeneratingWebFilter loginPage(ReactiveClientRegistrationRepository clientRegistrations) {
        var links = new LinkedHashMap<String, String>();
        if (clientRegistrations instanceof Iterable<?> registrations) {
            registrations.forEach(registration -> {
                var client = (ClientRegistration) registration;
                links.put("/oauth2/authorization/" + client.getRegistrationId(), client.getClientName());
            });
        }
        var loginPage = new LoginPageGeneratingWebFilter();
        loginPage.setOauth2AuthenticationUrlToClientName(links);
        return loginPage;
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

package org.tunelad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Map;

/**
 * Stands in for the identity provider, so that tests never need a running Keycloak: bearer tokens are checked
 * against the key {@link TestTokens} signs with, with the same issuer and audience rules as the application, and the
 * browser login registration is declared with explicit endpoints instead of being discovered from the issuer at
 * startup.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    public static final ClientRegistration KEYCLOAK = ClientRegistration.withRegistrationId("keycloak")
            .clientName("Keycloak")
            .clientId("tunelad")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .issuerUri(TestTokens.ISSUER)
            .authorizationUri(TestTokens.ISSUER + "/protocol/openid-connect/auth")
            .tokenUri(TestTokens.ISSUER + "/protocol/openid-connect/token")
            .jwkSetUri(TestTokens.ISSUER + "/protocol/openid-connect/certs")
            .userInfoUri(TestTokens.ISSUER + "/protocol/openid-connect/userinfo")
            .userNameAttributeName("sub")
            .providerConfigurationMetadata(Map.of("end_session_endpoint", TestTokens.ISSUER + "/protocol/openid-connect/logout"))
            .build();

    @Bean
    ReactiveClientRegistrationRepository testClientRegistrations() {
        return new InMemoryReactiveClientRegistrationRepository(KEYCLOAK);
    }

    @Bean
    ReactiveJwtDecoder testJwtDecoder() {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) TestTokens.KEYS.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER),
                new JwtClaimValidator<Collection<String>>("aud", aud -> aud != null && aud.contains(TestTokens.AUDIENCE))));
        return decoder;
    }
}

package org.tunelad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;

/**
 * Stands in for the identity provider, so that tests never need a running Keycloak: bearer tokens are checked
 * against the key {@link TestTokens} signs with, with the same issuer and audience rules as the application.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    @Bean
    ReactiveJwtDecoder testJwtDecoder() {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) TestTokens.KEYS.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER),
                new JwtClaimValidator<Collection<String>>("aud", aud -> aud != null && aud.contains(TestTokens.AUDIENCE))));
        return decoder;
    }
}

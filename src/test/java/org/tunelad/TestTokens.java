package org.tunelad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mints access tokens the way the identity provider would, for tests (see {@link TestSecurityConfiguration}). */
public final class TestTokens {

    public static final String ISSUER = "https://idp.test/realms/tunelad";
    public static final String AUDIENCE = "tunelad";

    static final KeyPair KEYS = generateKeys();
    private static final KeyPair FOREIGN_KEYS = generateKeys();

    private TestTokens() {
    }

    /** A token for a user with the given username and full name (null for no name claim). */
    public static String bearer(String username, String name) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("preferred_username", username);
        if (name != null) claims.put("name", name);
        return withClaims("sub-" + username, claims);
    }

    /** A token for the given subject with exactly the given extra claims, besides the standard ones. */
    public static String withClaims(String subject, Map<String, Object> claims) {
        return sign(KEYS.getPrivate(), standardClaims(subject, ISSUER, AUDIENCE).build(), claims);
    }

    /** A well-formed token for alice that the application must not trust: signed by another key. */
    public static String foreignSignature() {
        return sign(FOREIGN_KEYS.getPrivate(), standardClaims("sub-alice", ISSUER, AUDIENCE).build(), Map.of());
    }

    /** A token signed by the right key but issued for another audience. */
    public static String otherAudience() {
        return sign(KEYS.getPrivate(), standardClaims("sub-alice", ISSUER, "another-app").build(), Map.of());
    }

    private static JWTClaimsSet.Builder standardClaims(String subject, String issuer, String audience) {
        var now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(5, ChronoUnit.MINUTES)));
    }

    private static String sign(PrivateKey key, JWTClaimsSet standard, Map<String, Object> claims) {
        var builder = new JWTClaimsSet.Builder(standard);
        claims.forEach(builder::claim);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static KeyPair generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

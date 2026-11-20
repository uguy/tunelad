package org.tunelad.account.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.URL;
import java.util.Map;
import java.util.Optional;

/** Who authenticated, as the identity provider says: its issuer, the subject and the subject's claims. */
record Identity(String issuer, String subject, Map<String, Object> claims) {

    /** The identity behind a browser login (OIDC user) or a bearer token (JWT); empty for anything else. */
    static Optional<Identity> of(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser user) {
            return of(user.getIdToken().getIssuer(), user.getSubject(), user.getClaims());
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            var jwt = token.getToken();
            return of(jwt.getIssuer(), jwt.getSubject(), jwt.getClaims());
        }
        return Optional.empty();
    }

    static Optional<Identity> of(URL issuer, String subject, Map<String, Object> claims) {
        if (issuer == null || subject == null) return Optional.empty();
        return Optional.of(new Identity(issuer.toExternalForm(), subject, claims));
    }

    String displayName() {
        return DisplayNames.of(claims, subject);
    }

    String email() {
        return claims.get("email") instanceof String email && !email.isBlank() ? email : null;
    }
}

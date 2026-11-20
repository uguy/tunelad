package org.tunelad.infrastructure.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RolesClaimAuthoritiesMapperTest {

    private final RolesClaimAuthoritiesMapper mapper = new RolesClaimAuthoritiesMapper();

    @Test
    void adds_a_role_authority_per_roles_claim_entry() {
        var oidc = new OidcUserAuthority(idToken(Map.of("roles", List.of("admin", "offline_access"))));

        assertThat(mapper.mapAuthorities(List.of(oidc)))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("OIDC_USER", "ROLE_admin", "ROLE_offline_access");
    }

    @Test
    void reads_roles_from_userinfo_too() {
        var oidc = new OidcUserAuthority(idToken(Map.of()), new OidcUserInfo(Map.of("sub", "sub-alice", "roles", List.of("admin"))));

        assertThat(mapper.mapAuthorities(List.of(oidc)))
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_admin");
    }

    @Test
    void no_roles_claim_adds_nothing() {
        var oidc = new OidcUserAuthority(idToken(Map.of()));

        assertThat(mapper.mapAuthorities(List.of(oidc)))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("OIDC_USER");
    }

    @Test
    void passes_other_authorities_through() {
        var scope = new SimpleGrantedAuthority("SCOPE_openid");

        assertThat(mapper.mapAuthorities(List.of(scope))).extracting(GrantedAuthority::getAuthority).containsExactly("SCOPE_openid");
    }

    private static OidcIdToken idToken(Map<String, Object> claims) {
        var builder = OidcIdToken.withTokenValue("id-token")
                .subject("sub-alice")
                .issuer("https://idp.test/realms/tunelad")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}

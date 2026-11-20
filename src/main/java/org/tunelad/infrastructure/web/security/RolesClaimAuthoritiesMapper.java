package org.tunelad.infrastructure.web.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the authorities of an OIDC login and adds {@code ROLE_<role>} for each entry of the {@code roles} claim, in
 * the ID token or the userinfo response.
 */
class RolesClaimAuthoritiesMapper implements GrantedAuthoritiesMapper {

    static final String ROLES_CLAIM = "roles";
    static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
        for (var authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                addRoles(oidc.getIdToken().getClaims(), mapped);
                if (oidc.getUserInfo() != null) addRoles(oidc.getUserInfo().getClaims(), mapped);
            }
        }
        return mapped;
    }

    private static void addRoles(Map<String, Object> claims, Set<GrantedAuthority> authorities) {
        if (claims.get(ROLES_CLAIM) instanceof Collection<?> roles) {
            roles.stream()
                    .filter(String.class::isInstance)
                    .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                    .forEach(authorities::add);
        }
    }
}

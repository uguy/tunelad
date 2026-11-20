package org.tunelad.account;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An account as other modules see it, for the current request: {@code admin} comes from the roles the identity
 * provider granted in this token or login, not from the stored account.
 */
@Schema(description = "The account of the authenticated user")
public record AccountRef(
        String id,
        String displayName,
        @Schema(description = "Whether the user has the identity provider's admin role", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean admin) {
}

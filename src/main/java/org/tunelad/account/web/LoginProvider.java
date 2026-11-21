package org.tunelad.account.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An identity provider a browser user can log in through")
public record LoginProvider(
        @Schema(description = "The provider's registration id, as in /oauth2/authorization/{id}", example = "keycloak")
        String id,
        @Schema(description = "The name to show on the login button", example = "Keycloak")
        String name) {
}

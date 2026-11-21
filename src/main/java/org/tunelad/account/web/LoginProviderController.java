package org.tunelad.account.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@RestController
@Tag(name = "Account", description = "The account of the authenticated user")
@RequestMapping(value = "api/login-providers", produces = MediaType.APPLICATION_JSON_VALUE)
public class LoginProviderController {

    private final ReactiveClientRegistrationRepository clientRegistrations;

    @GetMapping
    @Operation(summary = "Login providers",
            description = "Lists the identity providers a browser user can log in through. Needs no authentication.")
    @ApiResponse(responseCode = "200", description = "The configured providers",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LoginProvider.class))))
    public Flux<LoginProvider> loginProviders() {
        // Only the in-memory repository, which Spring Boot configures from the properties, can be listed
        if (clientRegistrations instanceof Iterable<?> registrations) {
            return Flux.fromIterable(registrations)
                    .cast(ClientRegistration.class)
                    .map(client -> new LoginProvider(client.getRegistrationId(), client.getClientName()));
        }
        return Flux.empty();
    }
}

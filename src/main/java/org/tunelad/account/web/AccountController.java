package org.tunelad.account.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tunelad.account.AccountRef;
import org.tunelad.account.CurrentAccount;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@Tag(name = "Account", description = "The account of the authenticated user")
@RequestMapping(value = "api/me", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountController {

    private final CurrentAccount currentAccount;

    @GetMapping
    @Operation(summary = "Current account", description = "Returns the account of the authenticated user",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The current account",
            content = @Content(schema = @Schema(implementation = AccountRef.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    public Mono<ResponseEntity<AccountRef>> me() {
        return currentAccount.get()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}

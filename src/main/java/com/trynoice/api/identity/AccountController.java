package com.trynoice.api.identity;

import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.SignInTokenDispatchException;
import com.trynoice.api.identity.models.AuthCredentials;
import com.trynoice.api.identity.models.SignInRequest;
import com.trynoice.api.identity.models.SignUpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * REST controller for identity and auth related '{@code /v1/accounts}' routes.
 */
@RestController
@RequestMapping("/v1/accounts")
@Validated
@Slf4j
class AccountController {

    private final AccountService accountService;

    @Autowired
    AccountController(@NonNull AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * <p>
     * Creates a new user account if one didn't already exist for the provided email. If the account
     * creation is successful or the account already existed, it sends a sign-in link the provided
     * email.</p>
     * <p>
     * The sign-in link contains a short-lived refresh token and thus, it must be exchanged for
     * proper auth credentials using the '{@code /credentials}' endpoint before its expiry.</p>
     *
     * @param request The following validation checks are applied on the request body. <ul> <li>
     *                {@code email}: it must be a non-blank well-formed email of at most 64
     *                characters.</li> <li> {@code name}: it must be a non-blank string of at most
     *                64 characters.</li> </ul>
     */
    @Operation(summary = "Create a new account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "sign-in link sent to the provided email"),
        @ApiResponse(responseCode = "400", description = "failed to read request"),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })

    @NonNull
    @PostMapping("/signUp")
    ResponseEntity<Void> signUp(@NonNull @Valid @RequestBody SignUpRequest request) throws SignInTokenDispatchException {
        accountService.signUp(request.getEmail(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * <p>
     * Sends the sign-in link the provided email. Returns HTTP 404 if an account with the provided
     * email doesn't exist.</p>
     * <p>
     * The sign-in link contains a short-lived refresh token and thus, it must be exchanged for
     * proper auth credentials using the '{@code /credentials}' endpoint before its expiry.</p>
     *
     * @param request The following validation checks are applied on the request body. <ul> <li>
     *                `email` : it must be a non-blank well-formed email address. </li> </ul>
     */
    @Operation(summary = "Sign-in to an existing account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "sign-in link sent to the provided email"),
        @ApiResponse(responseCode = "400", description = "failed to read request"),
        @ApiResponse(responseCode = "404", description = "account does not exist"),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })

    @NonNull
    @PostMapping("/signIn")
    ResponseEntity<Void> signIn(@NonNull @Valid @RequestBody SignInRequest request) throws SignInTokenDispatchException {
        try {
            accountService.signIn(request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (AccountNotFoundException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * <p>
     * Issues fresh credentials (refresh and access tokens) in exchange for a valid refresh token.
     * If the refresh token is invalid, expired or re-used, it returns HTTP 401.</p>
     *
     * @param refreshToken it must be a non-blank string.
     * @param userAgent    it must be non-blank string of at most 128 characters.
     * @return fresh credentials (refresh and access tokens)
     */
    @Operation(summary = "Issue new credentials using a valid refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "failed to read request", content = @Content),
        @ApiResponse(responseCode = "401", description = "refresh token is invalid, expired or re-used", content = @Content),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })

    @NonNull
    @GetMapping(value = "/credentials")
    ResponseEntity<AuthCredentials> issueCredentials(
        @NonNull @NotBlank @Valid @RequestHeader("X-Refresh-Token") String refreshToken,
        @NonNull @NotBlank @Size(max = 128) @Valid @RequestHeader("User-Agent") String userAgent
    ) {
        try {
            val credentials = accountService.issueAuthCredentials(refreshToken, userAgent);
            return ResponseEntity.ok(credentials);
        } catch (RefreshTokenVerificationException e) {
            log.trace("failed to issue fresh auth credentials", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}

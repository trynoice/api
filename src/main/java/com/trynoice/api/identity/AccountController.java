package com.trynoice.api.identity;

import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.models.AuthCredentials;
import com.trynoice.api.identity.models.Profile;
import com.trynoice.api.identity.models.SignInParams;
import com.trynoice.api.identity.models.SignUpParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static java.util.Objects.requireNonNullElse;

/**
 * REST controller for identity and auth related '{@code /v1/accounts}' routes.
 */
@Validated
@RestController
@RequestMapping("/v1/accounts")
@Slf4j
@Tag(name = "account")
class AccountController {

    static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";
    static final String USER_AGENT_HEADER = "User-Agent";
    static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final String REFRESH_TOKEN_COOKIE = CookieAuthFilter.REFRESH_TOKEN_COOKIE;

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
     * <p>
     * If the request returns HTTP 429, then the clients must consider the {@code Retry-After} HTTP
     * header as the API server will refuse all further sign-up attempts until the given duration
     * has elapsed.</p>
     */
    @Operation(summary = "Create a new account")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "sign-in link sent to the provided email"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(
            responseCode = "429",
            description = "the account is temporarily blocked from attempting sign-up",
            headers = @Header(
                name = RETRY_AFTER_HEADER,
                description = "duration (in seconds) after which the account should be able to reattempt sign-up",
                required = true,
                schema = @Schema(type = "integer", format = "int64"))),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @PostMapping("/signUp")
    ResponseEntity<Void> signUp(@Valid @NotNull @RequestBody SignUpParams params) {
        try {
            accountService.signUp(params);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (TooManySignInAttemptsException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(RETRY_AFTER_HEADER, String.valueOf(e.getRetryAfter().toSeconds()))
                .build();
        }
    }

    /**
     * <p>Sends the sign-in link the provided email.</p>
     * <p>
     * The sign-in link contains a short-lived refresh token and thus, it must be exchanged for
     * proper auth credentials using the '{@code /credentials}' endpoint before its expiry.</p>
     * <p>
     * If the request returns HTTP 429, then the clients must consider the {@code Retry-After} HTTP
     * header as the API server will refuse all further sign-in attempts until the given duration
     * has elapsed.</p>
     */
    @Operation(summary = "Sign-in to an existing account")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "sent sign-in link to the given email if such an account exists"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(
            responseCode = "429",
            description = "the account is temporarily blocked from attempting sign-in",
            headers = @Header(
                name = RETRY_AFTER_HEADER,
                description = "duration (in seconds) after which the account should be able to reattempt sign-in",
                required = true,
                schema = @Schema(type = "integer", format = "int64"))),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @PostMapping("/signIn")
    ResponseEntity<Void> signIn(@Valid @NotNull @RequestBody SignInParams params) {
        try {
            accountService.signIn(params);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (AccountNotFoundException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (TooManySignInAttemptsException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(RETRY_AFTER_HEADER, String.valueOf(e.getRetryAfter().toSeconds()))
                .build();
        }
    }

    /**
     * <p>
     * Revokes a valid refresh token. If the refresh token is invalid, expired or re-used, it
     * returns HTTP 401.</p>
     * <p>
     * <b>Refresh token must be provided</b>, either as a header or a cookie.</p>
     *
     * @param refreshTokenHeader if present, it must be a non-blank string.
     * @param refreshTokenCookie if present, it must be a non-blank string.
     */
    @Operation(summary = "Revokes a valid refresh token")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(responseCode = "401", description = "refresh token is invalid, expired or re-used"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @GetMapping(value = "/signOut")
    ResponseEntity<Void> signOut(
        @Valid @Size(min = 1) @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshTokenHeader,
        @Valid @Size(min = 1) @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie
    ) {
        if (refreshTokenHeader == null && refreshTokenCookie == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            val refreshToken = requireNonNullElse(refreshTokenCookie, refreshTokenHeader);
            accountService.signOut(refreshToken);
            return ResponseEntity.ok(null);
        } catch (RefreshTokenVerificationException e) {
            log.trace("failed to revoke refresh token", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * <p>
     * Issues fresh credentials (refresh and access tokens) in exchange for a valid refresh token.
     * If the refresh token is invalid, expired or re-used, it returns HTTP 401.</p>
     *
     * @param refreshToken it must be a non-blank string.
     * @param userAgent    it can a string of at most 128 characters.
     * @return fresh credentials (refresh and access tokens)
     */
    @Operation(summary = "Issue new credentials using a valid refresh token")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "refresh token is invalid, expired or re-used", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping(value = "/credentials")
    ResponseEntity<AuthCredentials> issueCredentials(
        @Valid @NotBlank @RequestHeader(REFRESH_TOKEN_HEADER) String refreshToken,
        @Valid @Size(min = 1, max = 128) @RequestHeader(value = USER_AGENT_HEADER, required = false) String userAgent
    ) {
        try {
            val credentials = accountService.issueAuthCredentials(refreshToken, userAgent);
            return ResponseEntity.ok(credentials);
        } catch (RefreshTokenVerificationException e) {
            log.trace("failed to issue fresh auth credentials", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * @return profile of the authenticated user.
     */
    @Operation(summary = "Get profile of the auth user")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "failed to read request", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping(value = "/profile")
    ResponseEntity<Profile> getProfile(@NonNull @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.ok(accountService.getProfile(principal));
    }
}

package com.trynoice.api.identity;

import com.trynoice.api.identity.exceptions.AccountNotFoundException;
import com.trynoice.api.identity.exceptions.RefreshTokenRevokeException;
import com.trynoice.api.identity.exceptions.RefreshTokenVerificationException;
import com.trynoice.api.identity.exceptions.TooManySignInAttemptsException;
import com.trynoice.api.identity.models.AuthUser;
import com.trynoice.api.identity.viewmodels.AuthCredentialsResponse;
import com.trynoice.api.identity.viewmodels.ProfileResponse;
import com.trynoice.api.identity.viewmodels.SignInRequest;
import com.trynoice.api.identity.viewmodels.SignUpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Min;
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
class AccountController {

    static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";
    static final String USER_AGENT_HEADER = "User-Agent";

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
     * If the request returns HTTP 403, then the clients must consider this account as blacklisted.
     * All further attempts using this email address will result in HTTP 403. At this point, the
     * clients may advise their user to contact the support.</p>
     *
     * @param request The following validation checks are applied on the request body. <ul> <li>
     *                {@code email}: it must be a non-blank well-formed email of at most 64
     *                characters.</li> <li> {@code name}: it must be a non-blank string of at most
     *                64 characters.</li> </ul>
     */
    @Operation(summary = "Create a new account")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "sign-in link sent to the provided email"),
        @ApiResponse(responseCode = "400", description = "failed to read request"),
        @ApiResponse(responseCode = "403", description = "account with the given email is blacklisted"),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @PostMapping("/signUp")
    ResponseEntity<Void> signUp(@NonNull @Valid @RequestBody SignUpRequest request) {
        try {
            accountService.signUp(request.getEmail(), request.getName());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (TooManySignInAttemptsException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * <p>
     * Sends the sign-in link the provided email. Returns HTTP 404 if an account with the provided
     * email doesn't exist.</p>
     * <p>
     * The sign-in link contains a short-lived refresh token and thus, it must be exchanged for
     * proper auth credentials using the '{@code /credentials}' endpoint before its expiry.</p>
     * <p>
     * If the request returns HTTP 403, then the clients must consider this account as blacklisted.
     * All further attempts using this email address will result in HTTP 403. At this point, the
     * clients may advise their user to contact the support.</p>
     *
     * @param request The following validation checks are applied on the request body. <ul> <li>
     *                `email` : it must be a non-blank well-formed email address. </li> </ul>
     */
    @Operation(summary = "Sign-in to an existing account")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "sign-in link sent to the provided email"),
        @ApiResponse(responseCode = "400", description = "failed to read request"),
        @ApiResponse(responseCode = "403", description = "account with the given email is blacklisted"),
        @ApiResponse(responseCode = "404", description = "account does not exist"),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @PostMapping("/signIn")
    ResponseEntity<Void> signIn(@NonNull @Valid @RequestBody SignInRequest request) {
        try {
            accountService.signIn(request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (AccountNotFoundException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (TooManySignInAttemptsException e) {
            log.trace("sign-in request failed", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
        @ApiResponse(responseCode = "400", description = "failed to read request"),
        @ApiResponse(responseCode = "401", description = "refresh token is invalid, expired or re-used"),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @GetMapping(value = "/signOut")
    ResponseEntity<Void> signOut(
        @Size(min = 1) @Valid @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshTokenHeader,
        @Size(min = 1) @Valid @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie
    ) {
        if (refreshTokenHeader == null && refreshTokenCookie == null) {
            return ResponseEntity.unprocessableEntity().build();
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
        @ApiResponse(responseCode = "400", description = "failed to read request", content = @Content),
        @ApiResponse(responseCode = "401", description = "refresh token is invalid, expired or re-used", content = @Content),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping(value = "/credentials")
    ResponseEntity<AuthCredentialsResponse> issueCredentials(
        @NonNull @NotBlank @Valid @RequestHeader(REFRESH_TOKEN_HEADER) String refreshToken,
        @Size(min = 1, max = 128) @Valid @RequestHeader(value = USER_AGENT_HEADER, required = false) String userAgent
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
     * <p>
     * Revokes a refresh token with the given {@code tokenId} provided that it is owned by the
     * authenticated user.</p>
     *
     * @param tokenId id of the refresh token.
     */
    @Operation(summary = "Issue new credentials using a valid refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "failed to read request", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "404", description = "auth user doesn't own a refresh token with given id", content = @Content),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @DeleteMapping(value = "/refreshTokens/{tokenId}")
    ResponseEntity<Void> revokeRefreshToken(
        @NonNull @AuthenticationPrincipal AuthUser principal,
        @NotNull @Min(1) @Valid @PathVariable Long tokenId
    ) {
        try {
            accountService.revokeRefreshToken(principal, tokenId);
            return ResponseEntity.ok(null);
        } catch (RefreshTokenRevokeException e) {
            log.trace("failed to revoke refresh token", e);
            return ResponseEntity.notFound().build();
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
    ResponseEntity<ProfileResponse> getProfile(@NonNull @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.ok(accountService.getProfile(principal));
    }
}

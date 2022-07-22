package com.trynoice.api.subscription;

import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.payload.SubscriptionFlowResponseV2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Validated
@RestController
@RequestMapping("/v2/subscriptions")
@Slf4j
@Tag(name = "subscription")
public class SubscriptionControllerV2 {

    private final SubscriptionService subscriptionService;

    @Autowired
    SubscriptionControllerV2(@NonNull SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * <p>
     * Initiates the subscription flow for the authenticated user. The flow might vary with payment
     * providers. It creates a new {@code incomplete} subscription entity and returns it with the
     * response on success.
     *
     * <p>To conclude this flow and transition the subscription entity to {@code active} state:</p>
     *
     * <ul>
     *     <li>for Google Play plans, the clients must link {@code subscriptionId} with the
     *     subscription purchase by specifying it as {@code obfuscatedAccountId} in Google Play
     *     billing flow params.</li>
     *     <li>for Stripe plans, the clients must redirect the user to the provided url to make the
     *     payment and complete the checkout session.</li>
     * </ul>
     *
     * <p>
     * Since clients may desire to use created subscription's id in {@code successUrl} and {@code
     * cancelUrl} callbacks, the server makes it available through {@code {subscriptionId}} template
     * string in their values, e.g. {@code https://api.test/success?id={subscriptionId}}. The server
     * will replace the template with the created subscription's id before creating a Stripe
     * checkout session, i.e. it will transform the previous url to {@code
     * https://api.test/success?id=1}, assuming the created subscription's id is {@literal 1}.</p>
     *
     * @param params {@code successUrl} and {@code cancelUrl} are only required for Stripe plans.
     */
    @Operation(summary = "Initiate the subscription flow")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "subscription flow successfully initiated"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "409", description = "user already has an active subscription", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @PostMapping
    ResponseEntity<SubscriptionFlowResponseV2> createSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @RequestBody SubscriptionFlowParams params
    ) {
        try {
            val response = subscriptionService.createSubscription(principalId, params);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SubscriptionPlanNotFoundException e) {
            return ResponseEntity.badRequest().build();
        } catch (DuplicateSubscriptionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}

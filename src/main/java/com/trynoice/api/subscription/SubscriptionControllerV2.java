package com.trynoice.api.subscription;

import com.trynoice.api.platform.validation.annotations.NullOrNotBlank;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.GiftCardExpiredException;
import com.trynoice.api.subscription.exceptions.GiftCardNotFoundException;
import com.trynoice.api.subscription.exceptions.GiftCardRedeemedException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.payload.SubscriptionFlowResponseV2;
import com.trynoice.api.subscription.payload.SubscriptionResponseV2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * Lists a {@code page} of subscriptions purchased by the authenticated user. Each {@code page}
     * contains at-most 20 entries. If {@code onlyActive} is {@literal true}, it lists the currently
     * active subscription purchase (at most one). It doesn't return subscription entities that were
     * initiated, but were never started.
     *
     * @param onlyActive return only the active subscription (single instance).
     * @param currency   an optional ISO 4217 currency code for including converted prices with the
     *                   subscription's plan.
     * @param page       0-indexed page number.
     * @return a list of subscription purchases; empty list if the page number is higher than
     * available data.
     */
    @Operation(summary = "List subscriptions")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping
    ResponseEntity<List<SubscriptionResponseV2>> listSubscriptions(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotNull @RequestParam(required = false, defaultValue = "false") Boolean onlyActive,
        @NullOrNotBlank @Size(min = 3, max = 3) @RequestParam(value = "currency", required = false) String currency,
        @NotNull @Min(0) @RequestParam(required = false, defaultValue = "0") Integer page
    ) {
        return ResponseEntity.ok(subscriptionService.listSubscriptions(principalId, onlyActive, currency, page));
    }

    /**
     * Get a subscription purchased by the authenticated user by its {@code subscriptionId}. It
     * doesn't return subscription entities that were initiated, but were never started.
     *
     * @param currency an optional ISO 4217 currency code for including converted prices with the
     *                 subscription's plan.
     * @return the requested subscription.
     */
    @Operation(summary = "Get subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "404", description = "subscription with given id doesn't exist", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping("/{subscriptionId}")
    ResponseEntity<SubscriptionResponseV2> getSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotNull @Min(1) @PathVariable Long subscriptionId,
        @NullOrNotBlank @Size(min = 3, max = 3) @RequestParam(value = "currency", required = false) String currency
    ) {
        try {
            return ResponseEntity.ok(subscriptionService.getSubscription(principalId, subscriptionId, currency));
        } catch (SubscriptionNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Redeems an issued gift card and creates a subscription for the authenticated user.
     *
     * @param giftCardCode must not be blank.
     * @return the newly activated subscription on successful redemption of the gift card.
     */
    @Operation(summary = "Redeem a gift card")
    @ApiResponses({
        @ApiResponse(responseCode = "201"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "404", description = "gift card doesn't exist", content = @Content),
        @ApiResponse(responseCode = "409", description = "user already has an active subscription", content = @Content),
        @ApiResponse(responseCode = "410", description = "gift card has expired", content = @Content),
        @ApiResponse(responseCode = "422", description = "gift card has already been redeemed", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @PostMapping("/giftCards/{giftCardCode}/redeem")
    ResponseEntity<Void> redeemGiftCard(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotBlank @Size(min = 1, max = 32) @PathVariable String giftCardCode
    ) {
        try {
            subscriptionService.redeemGiftCard(principalId, giftCardCode);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (GiftCardNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (DuplicateSubscriptionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (GiftCardExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        } catch (GiftCardRedeemedException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }
}

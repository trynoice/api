package com.trynoice.api.subscription;

import com.trynoice.api.platform.validation.annotations.HttpUrl;
import com.trynoice.api.platform.validation.annotations.NullOrNotBlank;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.GiftCardExpiredException;
import com.trynoice.api.subscription.exceptions.GiftCardNotFoundException;
import com.trynoice.api.subscription.exceptions.GiftCardRedeemedException;
import com.trynoice.api.subscription.exceptions.StripeCustomerPortalUrlException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.exceptions.WebhookEventException;
import com.trynoice.api.subscription.exceptions.WebhookPayloadException;
import com.trynoice.api.subscription.payload.GiftCardResponse;
import com.trynoice.api.subscription.payload.StripeCustomerPortalUrlResponse;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import com.trynoice.api.subscription.payload.SubscriptionFlowResponse;
import com.trynoice.api.subscription.payload.SubscriptionPlanResponse;
import com.trynoice.api.subscription.payload.SubscriptionResponse;
import com.trynoice.api.subscription.payload.SubscriptionResponseV2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for subscription related '{@code /v1/subscriptions}' routes.
 */
@Validated
@RestController
@RequestMapping("/v1/subscriptions")
@Slf4j
@Tag(name = "subscription")
class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Autowired
    SubscriptionController(@NonNull SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * <p>
     * Lists all available subscription plans that an auth user can subscribe.</p>
     *
     * <p>
     * The {@code provider} parameter (case-insensitive) can have the following values.</p>
     *
     * <ul>
     *     <li>empty or {@code null}: the response contains all available plans.</li>
     *     <li>{@code google_play}: the response contains all available plans supported by Google Play.</li>
     *     <li>{@code stripe}: the response contains all available plans supported by Stripe.</li>
     * </ul>
     *
     * @param provider filter listed plans by the given provider.
     * @param currency an optional ISO 4217 currency code for including converted prices with plans.
     * @return a list of available subscription plans.
     */
    @Operation(summary = "List available plans")
    @SecurityRequirements
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "422", description = "the requested provider is not supported", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping("/plans")
    ResponseEntity<List<SubscriptionPlanResponse>> listPlans(
        @Schema(allowableValues = {"google_play", "stripe"}) @RequestParam(value = "provider", required = false) String provider,
        @NullOrNotBlank @Size(min = 3, max = 3) @RequestParam(value = "currency", required = false) String currency
    ) {
        try {
            return ResponseEntity.ok(subscriptionService.listPlans(provider, currency));
        } catch (UnsupportedSubscriptionPlanProviderException e) {
            log.trace("unsupported subscription plan provider: {}", provider);
            return ResponseEntity.unprocessableEntity().build();
        }
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
    @Deprecated
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
    ResponseEntity<SubscriptionFlowResponse> createSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @RequestBody SubscriptionFlowParams params
    ) {
        try {
            val responseV2 = subscriptionService.createSubscription(principalId, params);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(SubscriptionFlowResponse.builder()
                    .subscription(buildSubscriptionResponseV1(responseV2.getSubscription()))
                    .stripeCheckoutSessionUrl(responseV2.getStripeCheckoutSessionUrl())
                    .build());
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
     * @param onlyActive      return only the active subscription (single instance).
     * @param stripeReturnUrl optional redirect URL for exiting Stripe customer portal.
     * @param currency        an optional ISO 4217 currency code for including converted prices with
     *                        the subscription's plan.
     * @param page            0-indexed page number.
     * @return a list of subscription purchases; empty list if the page number is higher than
     * available data.
     */
    @Deprecated
    @Operation(summary = "List subscriptions")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping
    ResponseEntity<List<SubscriptionResponse>> listSubscriptions(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotNull @RequestParam(required = false, defaultValue = "false") Boolean onlyActive,
        @HttpUrl @RequestParam(required = false) String stripeReturnUrl,
        @NullOrNotBlank @Size(min = 3, max = 3) @RequestParam(value = "currency", required = false) String currency,
        @NotNull @Min(0) @RequestParam(required = false, defaultValue = "0") Integer page
    ) {
        return ResponseEntity.ok(
            subscriptionService.listSubscriptions(principalId, onlyActive, currency, page)
                .stream()
                .map(responseV2 -> {
                    val response = buildSubscriptionResponseV1(responseV2);
                    if (response.getIsActive() && response.getPlan().getProvider().equalsIgnoreCase("stripe")) {
                        response.setStripeCustomerPortalUrl(requireStripeCustomerPortalUrl(principalId, stripeReturnUrl));
                    }
                    return response;
                })
                .toList());
    }

    /**
     * Get a subscription purchased by the authenticated user by its {@code subscriptionId}. It
     * doesn't return subscription entities that were initiated, but were never started.
     *
     * @param stripeReturnUrl optional redirect URL for exiting Stripe customer portal.
     * @param currency        an optional ISO 4217 currency code for including converted prices with
     *                        the subscription's plan.
     * @return the requested subscription.
     */
    @Deprecated
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
    ResponseEntity<SubscriptionResponse> getSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotNull @Min(1) @PathVariable Long subscriptionId,
        @HttpUrl @RequestParam(required = false) String stripeReturnUrl,
        @NullOrNotBlank @Size(min = 3, max = 3) @RequestParam(value = "currency", required = false) String currency
    ) {
        try {
            val responseV2 = subscriptionService.getSubscription(principalId, subscriptionId, currency);
            val response = buildSubscriptionResponseV1(responseV2);
            if (response.getIsActive() && response.getPlan().getProvider().equalsIgnoreCase("stripe")) {
                response.setStripeCustomerPortalUrl(requireStripeCustomerPortalUrl(principalId, stripeReturnUrl));
            }

            return ResponseEntity.ok(response);
        } catch (SubscriptionNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Requests the cancellation of the given subscription from its provider if it is currently
     * active. All providers are configured to cancel subscriptions at the end of their billing
     * cycles.
     */
    @Operation(summary = "Cancel subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "subscription cancelled"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(responseCode = "401", description = "access token is invalid"),
        @ApiResponse(responseCode = "404", description = "no such active subscription exists that is owned by the auth user"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @DeleteMapping("/{subscriptionId}")
    ResponseEntity<Void> cancelSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotNull @Min(1) @PathVariable Long subscriptionId
    ) {
        try {
            subscriptionService.cancelSubscription(principalId, subscriptionId);
            return ResponseEntity.noContent().build();
        } catch (SubscriptionNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * <p>
     * On receiving events, it finds the mutated subscription entity and reconciles its state in the
     * app by processing the event payload.</p>
     *
     * <p><b>See also:</b></p>
     * <ul>
     *     <li><a href="https://stripe.com/docs/billing/subscriptions/overview">How subscriptions
     *     work</a></li>
     *     <li><a
     *     href="https://stripe.com/docs/billing/subscriptions/build-subscription?ui=checkout#provision-and-monitor">
     *     Provision and monitor subscriptions</a></li>
     *     <li><a href="https://stripe.com/docs/billing/subscriptions/webhooks">Subscription
     *     webhooks</a></li>
     *     <li><a href="https://stripe.com/docs/api/checkout/sessions/object">Checkout Session
     *     object</a></li>
     *     <li><a href="https://stripe.com/docs/api/subscriptions/object">Subscription object
     *     </a></li>
     * </ul>
     *
     * @return <ul>
     * <li>{@code HTTP 200} on successfully processing the event.</li>
     * <li>{@code HTTP 400} if the server was unable to parse the event payload.</li>
     * <li>{@code HTTP 422} if the server was unable to process the event.</li>
     * <li>{@code HTTP 500} on internal server errors.</li>
     * </ul>
     */
    @Operation(hidden = true)
    @NonNull
    @PostMapping("/stripe/webhook")
    ResponseEntity<Void> stripeWebhook(
        @NotBlank @RequestHeader("Stripe-Signature") String payloadSignature,
        @NotBlank @RequestBody String body
    ) {
        try {
            subscriptionService.handleStripeWebhookEvent(body, payloadSignature);
            return ResponseEntity.ok(null);
        } catch (WebhookPayloadException e) {
            log.info("failed to parse the event payload", e);
            return ResponseEntity.badRequest().build();
        } catch (WebhookEventException e) {
            log.info("failed to process the event payload", e);
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    /**
     * Get the Stripe Customer Portal URL to allow customer to manage their subscription purchases.
     *
     * @param returnUrl a not blank redirect URL for exiting the Stripe customer portal.
     * @return a response containing the Stripe Customer Portal URL.
     */
    @Operation(summary = "Get the Stripe Customer Portal URL")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "404", description = "customer isn't associated with Stripe", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping("/stripe/customerPortalUrl")
    ResponseEntity<StripeCustomerPortalUrlResponse> stripeCustomerPortalUrl(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotBlank @HttpUrl @RequestParam String returnUrl
    ) {
        try {
            val response = subscriptionService.getStripeCustomerPortalUrl(principalId, returnUrl);
            return ResponseEntity.ok(response);
        } catch (StripeCustomerPortalUrlException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Gets an issued gift card.
     *
     * @param giftCardCode must not be blank.
     * @return the requested gift card.
     */
    @Operation(summary = "Get gift card")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "404", description = "gift card doesn't exist", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping("giftCards/{giftCardCode}")
    ResponseEntity<GiftCardResponse> getGiftCard(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotBlank @Size(min = 1, max = 32) @PathVariable String giftCardCode
    ) {
        try {
            val response = subscriptionService.getGiftCard(principalId, giftCardCode);
            return ResponseEntity.ok(response);
        } catch (GiftCardNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Redeems an issued gift card and creates a subscription for the authenticated user.
     *
     * @param giftCardCode must not be blank.
     * @return the newly activated subscription on successful redemption of the gift card.
     */
    @Deprecated
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
    ResponseEntity<SubscriptionResponse> redeemGiftCard(
        @NonNull @AuthenticationPrincipal Long principalId,
        @NotBlank @Size(min = 1, max = 32) @PathVariable String giftCardCode
    ) {
        try {
            val responseV2 = subscriptionService.redeemGiftCard(principalId, giftCardCode);
            val response = buildSubscriptionResponseV1(responseV2);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    @NonNull
    private String requireStripeCustomerPortalUrl(@NonNull Long principalId, String returnUrl) {
        try {
            return subscriptionService.getStripeCustomerPortalUrl(principalId, returnUrl).getUrl();
        } catch (StripeCustomerPortalUrlException e) {
            throw new RuntimeException(e);
        }
    }

    private SubscriptionResponse buildSubscriptionResponseV1(@NonNull SubscriptionResponseV2 responseV2) {
        return SubscriptionResponse.builder()
            .id(responseV2.getId())
            .plan(responseV2.getPlan())
            .isActive(responseV2.getIsActive())
            .isPaymentPending(responseV2.getIsPaymentPending())
            .startedAt(responseV2.getStartedAt())
            .endedAt(responseV2.getEndedAt())
            .isAutoRenewing(responseV2.getIsAutoRenewing())
            .isRefunded(responseV2.getIsRefunded())
            .renewsAt(responseV2.getRenewsAt())
            .googlePlayPurchaseToken(responseV2.getGooglePlayPurchaseToken())
            .giftCardCode(responseV2.getGiftCardCode())
            .build();
    }
}

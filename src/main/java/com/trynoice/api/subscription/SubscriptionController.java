package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.util.List;

/**
 * REST controller for subscription related '{@code /v1/subscriptions}' routes.
 */
@Validated
@RestController
@RequestMapping("/v1/subscriptions")
@Slf4j
class SubscriptionController {

    static final String SUBSCRIPTION_ID_HEADER = "X-Subscription-Id";
    static final String STRIPE_CHECKOUT_SESSION_URL_HEADER = "X-Stripe-Checkout-Session-Url";

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
     * The {@code provider} parameter can have the following values.</p>
     *
     * <ul>
     *     <li>empty or {@code null}: the response contains all available plans.</li>
     *     <li>{@code GOOGLE_PLAY}: the response contains all available plans supported by Google Play.</li>
     *     <li>{@code RAZORPAY}: the response contains all available plans supported by Razorpay.</li>
     * </ul>
     *
     * @param provider filter listed plans by the given provider.
     * @return a non-null list of available subscription plans.
     */
    @Operation(summary = "List available plans")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "422", description = "the requested provider is not supported", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping("/plans")
    ResponseEntity<List<SubscriptionPlanView>> getPlans(@RequestParam(value = "provider", required = false) String provider) {
        try {
            return ResponseEntity.ok(subscriptionService.getPlans(provider));
        } catch (UnsupportedSubscriptionPlanProviderException e) {
            log.trace("unsupported subscription plan provider: {}", provider);
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    /**
     * <p>
     * Initiates the subscription flow for the authenticated user. The flow might vary with payment
     * providers. It creates a new subscription entity. On success, it returns HTTP 201 with the
     * following headers.</p>
     *
     * <ul>
     *     <li>{@code Location}: the url of the new subscription entity.</li>
     *     <li>{@code X-Subscription-Id}: id of the new subscription entity.</li>
     *     <li>{@code X-Stripe-Checkout-Session-HttpUrl}: payment url for Stripe plans.</li>
     * </ul>
     *
     * <p>To conclude the subscription flow and make this subscription active</p>
     *
     * <ul>
     *     <li>for Google Play plans, the clients must attach {@code subscriptionId} as {@code
     *     obfuscatedExternalProfileId} to the subscription purchase.</li>
     *     <li>for Stripe plans, the clients must redirect the user to the provided url to make the
     *     payment.</li>
     * </ul>
     *
     * @param params {@code successUrl} and {@code cancelUrl} are only required for Stripe plans.
     */
    @Operation(summary = "Initiate the subscription flow")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "subscription flow successfully initiated",
            headers = {
                @Header(name = "Location", description = "url of the created subscription", required = true),
                @Header(name = SUBSCRIPTION_ID_HEADER, description = "id of the created subscription", required = true),
                @Header(name = STRIPE_CHECKOUT_SESSION_URL_HEADER, description = "checkout session url if the plan is provided by Stripe")
            }),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(responseCode = "409", description = "user has already began the subscription flow or has an active subscription"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @PostMapping
    ResponseEntity<Void> createSubscription(
        @NonNull HttpServletRequest request,
        @NonNull @AuthenticationPrincipal AuthUser principal,
        @Valid @NonNull @RequestBody SubscriptionFlowParams params
    ) {
        try {
            val result = subscriptionService.createSubscription(principal, params);
            return ResponseEntity.created(
                    URI.create(String.format("%s/%d", request.getRequestURL(), result.getSubscriptionId())))
                .header(SUBSCRIPTION_ID_HEADER, result.getSubscriptionId().toString())
                .header(STRIPE_CHECKOUT_SESSION_URL_HEADER, result.getStripeCheckoutSessionUrl())
                .build();
        } catch (SubscriptionPlanNotFoundException e) {
            return ResponseEntity.badRequest().build();
        } catch (DuplicateSubscriptionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * <p>
     * On receiving an event, it processes the mutated subscription entity and reconciles the
     * internal state of the app by re-requesting subscription entity from the Google Play API.</p>
     *
     * <p><b>See also:</b></p>
     * <ul>
     *     <li><a href="https://developer.android.com/google/play/billing/rtdn-reference#sub">Google
     *     Play real-time developer notifications reference</a></li>
     *     <li><a href="https://developer.android.com/google/play/billing/subscriptions">Google Play
     *     subscription documentation</a></li>
     *     <li><a href="https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions">
     *     Android Publisher REST API reference</a></li>
     * </ul>
     *
     * @param requestBody event payload (JSON).
     */
    @Operation(summary = "Webhook for listening to Google Play Billing subscription events")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "event successfully processed"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @PostMapping("/googlePlay/webhook")
    ResponseEntity<Void> googlePlayWebhook(@RequestBody JsonNode requestBody) {
        try {
            subscriptionService.handleGooglePlayWebhookEvent(requestBody);
        } catch (SubscriptionWebhookEventException e) {
            log.info("failed to process the google play webhook event", e);
        }

        // always return 200 because if we return an HTTP error response code on not being able to
        // correctly process the event payload, then Google Cloud Pub/Sub will retry its delivery.
        // Since we couldn't correctly process the payload the first-time, it's highly unlikely that
        // we'd be able to process it correctly on redelivery.
        return ResponseEntity.ok(null);
    }
}

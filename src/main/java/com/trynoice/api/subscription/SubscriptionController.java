package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.stripe.exception.SignatureVerificationException;
import com.trynoice.api.platform.validation.annotations.HttpUrl;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionStateException;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionFlowResult;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import com.trynoice.api.subscription.models.SubscriptionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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
     * providers. It creates a new subscription entity. On success, it returns HTTP 201 with a
     * response body.</p>
     *
     * <p>To conclude the subscription flow and make this subscription active</p>
     *
     * <ul>
     *     <li>for Google Play plans, the clients must link {@code subscriptionId} with the
     *     subscription purchase by specifying it as {@code obfuscatedProfileId} in Google Play
     *     billing flow params.</li>
     *     <li>for Stripe plans, the clients must redirect the user to the provided url to make the
     *     payment and complete the checkout session.</li>
     * </ul>
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
    ResponseEntity<SubscriptionFlowResult> createSubscription(
        @NonNull HttpServletRequest request,
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @RequestBody SubscriptionFlowParams params
    ) {
        try {
            val result = subscriptionService.createSubscription(principalId, params);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (SubscriptionPlanNotFoundException e) {
            return ResponseEntity.badRequest().build();
        } catch (DuplicateSubscriptionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Lists all subscriptions (active/inactive) ever purchased by the authenticated user.
     *
     * @param onlyActive      return only the active subscription (single instance).
     * @param stripeReturnUrl redirect URL for exiting Stripe customer portal.
     * @return a list of subscription purchases.
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
    ResponseEntity<List<SubscriptionView>> getSubscriptions(
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @RequestParam(required = false, defaultValue = "false") Boolean onlyActive,
        @Valid @HttpUrl @RequestParam(required = false) String stripeReturnUrl
    ) {
        return ResponseEntity.ok(subscriptionService.getSubscriptions(principalId, onlyActive, stripeReturnUrl));
    }

    /**
     * Cancels the given subscription if it is currently ongoing (active).
     */
    @Operation(summary = "Cancel subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "subscription cancelled"),
        @ApiResponse(responseCode = "400", description = "request is not valid"),
        @ApiResponse(responseCode = "401", description = "access token is invalid"),
        @ApiResponse(responseCode = "409", description = "referenced subscription is not ongoing (active)"),
        @ApiResponse(responseCode = "500", description = "internal server error"),
    })
    @NonNull
    @DeleteMapping("/{subscriptionId}")
    ResponseEntity<Void> cancelSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @Min(1) @PathVariable Long subscriptionId
    ) {
        try {
            subscriptionService.cancelSubscription(principalId, subscriptionId);
            return ResponseEntity.noContent().build();
        } catch (SubscriptionNotFoundException e) {
            return ResponseEntity.badRequest().build();
        } catch (SubscriptionStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * <p>
     * On receiving an event, it finds the mutated subscription entity and reconciles its state in
     * the app by re-requesting referenced subscription purchase from the Google Play API.</p>
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
     * @return <ul>
     * <li>{@code HTTP 200} on successfully processing the event.</li>
     * <li>{@code HTTP 400} if the request is not valid.</li>
     * <li>{@code HTTP 500} on internal server errors.</li>
     * </ul>
     */
    @Operation(hidden = true)
    @NonNull
    @PostMapping("/googlePlay/webhook")
    ResponseEntity<Void> googlePlayWebhook(@Valid @NotNull @RequestBody JsonNode requestBody) {
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
     * <li>{@code HTTP 400} if the request is not valid.</li>
     * <li>{@code HTTP 500} on internal server errors.</li>
     * </ul>
     */
    @Operation(hidden = true)
    @NonNull
    @PostMapping("/stripe/webhook")
    ResponseEntity<Void> stripeWebhook(
        @Valid @NotBlank @RequestHeader("Stripe-Signature") String payloadSignature,
        @Valid @NotBlank @RequestBody String body
    ) {
        try {
            subscriptionService.handleStripeWebhookEvent(body, payloadSignature);
        } catch (SubscriptionWebhookEventException e) {
            log.info("failed to process the stripe webhook event", e);
        } catch (SignatureVerificationException e) {
            log.info("failed to verify payload signature of stripe webhook event", e);
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(null);
    }
}

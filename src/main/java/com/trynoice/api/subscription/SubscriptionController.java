package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.trynoice.api.subscription.exceptions.SubscriptionWebhookEventException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
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

package com.trynoice.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.trynoice.api.platform.validation.annotations.HttpUrl;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.exceptions.WebhookEventException;
import com.trynoice.api.subscription.exceptions.WebhookPayloadException;
import com.trynoice.api.subscription.models.SubscriptionFlowParams;
import com.trynoice.api.subscription.models.SubscriptionFlowResult;
import com.trynoice.api.subscription.models.SubscriptionPlanView;
import com.trynoice.api.subscription.models.SubscriptionView;
import io.swagger.v3.oas.annotations.Operation;
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
    ResponseEntity<List<SubscriptionPlanView>> getPlans(
        @Schema(allowableValues = {"google_play", "stripe"}) @RequestParam(value = "provider", required = false) String provider
    ) {
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
     * Lists a {@code page} of subscriptions purchased by the authenticated user. Each {@code page}
     * contains at-most 20 entries. If {@code onlyActive} is {@literal true}, it lists the currently
     * active subscription purchase (at most one). It doesn't return subscription entities that were
     * initiated, but were never started.
     *
     * @param onlyActive      return only the active subscription (single instance).
     * @param stripeReturnUrl redirect URL for exiting Stripe customer portal.
     * @param page            0-indexed page number. Causes {@literal HTTP 404} on exceeding the
     *                        available limit.
     * @return a page of subscription purchases.
     */
    @Operation(summary = "List subscriptions")
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "request is not valid", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "404", description = "the requested page number is higher than available", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping
    ResponseEntity<List<SubscriptionView>> listSubscriptions(
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @RequestParam(required = false, defaultValue = "false") Boolean onlyActive,
        @Valid @HttpUrl @RequestParam(required = false) String stripeReturnUrl,
        @Valid @NotNull @Min(0) @RequestParam(required = false, defaultValue = "0") Integer page
    ) {
        val subscriptions = subscriptionService.listSubscriptions(principalId, onlyActive, stripeReturnUrl, page);
        return page > 0 && subscriptions.isEmpty()
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(subscriptions);
    }

    /**
     * Get a subscription purchased by the authenticated user by its {@code subscriptionId}. It
     * doesn't return subscription entities that were initiated, but were never started.
     *
     * @param stripeReturnUrl optional redirect URL for exiting Stripe customer portal.
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
    ResponseEntity<SubscriptionView> getSubscription(
        @NonNull @AuthenticationPrincipal Long principalId,
        @Valid @NotNull @Min(1) @PathVariable Long subscriptionId,
        @Valid @HttpUrl @RequestParam(required = false) String stripeReturnUrl
    ) {
        try {
            val subscription = subscriptionService.getSubscription(principalId, subscriptionId, stripeReturnUrl);
            return ResponseEntity.ok(subscription);
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
        @Valid @NotNull @Min(1) @PathVariable Long subscriptionId
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
     * <li>{@code HTTP 400} if the server was unable to parse the event payload.</li>
     * <li>{@code HTTP 422} if the server was unable to process the event.</li>
     * <li>{@code HTTP 500} on internal server errors.</li>
     * </ul>
     */
    @Operation(hidden = true)
    @NonNull
    @PostMapping("/googlePlay/webhook")
    ResponseEntity<Void> googlePlayWebhook(@Valid @NotNull @RequestBody JsonNode requestBody) {
        try {
            subscriptionService.handleGooglePlayWebhookEvent(requestBody);
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
        @Valid @NotBlank @RequestHeader("Stripe-Signature") String payloadSignature,
        @Valid @NotBlank @RequestBody String body
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
}

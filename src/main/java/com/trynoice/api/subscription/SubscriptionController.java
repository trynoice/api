package com.trynoice.api.subscription;

import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.viewmodels.SubscriptionPlanResponse;
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
        @ApiResponse(responseCode = "400", description = "failed to read request", content = @Content),
        @ApiResponse(responseCode = "401", description = "access token is invalid", content = @Content),
        @ApiResponse(responseCode = "422", description = "request parameters have validation errors", content = @Content),
        @ApiResponse(responseCode = "500", description = "internal server error", content = @Content),
    })
    @NonNull
    @GetMapping("/plans")
    ResponseEntity<List<SubscriptionPlanResponse>> getPlans(@RequestParam(value = "provider", required = false) String provider) {
        try {
            return ResponseEntity.ok(subscriptionService.getPlans(provider));
        } catch (UnsupportedSubscriptionPlanProviderException e) {
            log.trace("unsupported subscription plan provider: {}", provider);
            return ResponseEntity.unprocessableEntity().build();
        }
    }
}

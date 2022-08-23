package com.trynoice.api.subscription;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.trynoice.api.contracts.AccountServiceContract;
import com.trynoice.api.subscription.ecb.ForeignExchangeRatesProvider;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.CustomerRepository;
import com.trynoice.api.subscription.entities.GiftCard;
import com.trynoice.api.subscription.entities.GiftCardRepository;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.entities.SubscriptionPlanRepository;
import com.trynoice.api.subscription.entities.SubscriptionRepository;
import com.trynoice.api.subscription.exceptions.DuplicateSubscriptionException;
import com.trynoice.api.subscription.exceptions.GiftCardExpiredException;
import com.trynoice.api.subscription.exceptions.GiftCardNotFoundException;
import com.trynoice.api.subscription.exceptions.GiftCardRedeemedException;
import com.trynoice.api.subscription.exceptions.StripeCustomerPortalUrlException;
import com.trynoice.api.subscription.exceptions.SubscriptionNotFoundException;
import com.trynoice.api.subscription.exceptions.SubscriptionPlanNotFoundException;
import com.trynoice.api.subscription.exceptions.UnsupportedSubscriptionPlanProviderException;
import com.trynoice.api.subscription.payload.SubscriptionFlowParams;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.cache.Cache;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SubscriptionServiceTest {

    @Mock
    private SubscriptionConfiguration subscriptionConfiguration;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private AccountServiceContract accountServiceContract;

    @Mock
    private AndroidPublisherApi androidPublisherApi;

    @Mock
    private StripeApi stripeApi;

    @Mock
    private Cache cache;

    @Mock
    private ForeignExchangeRatesProvider exchangeRatesProvider;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(
            subscriptionConfiguration,
            customerRepository,
            subscriptionPlanRepository,
            subscriptionRepository,
            giftCardRepository,
            accountServiceContract,
            androidPublisherApi,
            stripeApi,
            cache,
            exchangeRatesProvider);
    }

    @Test
    void listPlans_withSupportedProviders() throws UnsupportedSubscriptionPlanProviderException {
        val googlePlayPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "google_plan_plan_id");
        val stripePlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "stripe_plan_id");

        lenient()
            .when(subscriptionPlanRepository.findAll(any(Sort.class)))
            .thenReturn(List.of(googlePlayPlan, stripePlan));

        lenient()
            .when(subscriptionPlanRepository.findAllByProvider(eq(SubscriptionPlan.Provider.GOOGLE_PLAY), any()))
            .thenReturn(List.of(googlePlayPlan));

        lenient()
            .when(subscriptionPlanRepository.findAllByProvider(eq(SubscriptionPlan.Provider.STRIPE), any()))
            .thenReturn(List.of(stripePlan));

        lenient()
            .when(exchangeRatesProvider.getRateForCurrency(any(), any())).thenReturn(Optional.of(1.0));

        val testCases = new HashMap<SubscriptionPlan.Provider, List<SubscriptionPlan>>();
        testCases.put(null, List.of(googlePlayPlan, stripePlan));
        testCases.put(SubscriptionPlan.Provider.GOOGLE_PLAY, List.of(googlePlayPlan));
        testCases.put(SubscriptionPlan.Provider.STRIPE, List.of(stripePlan));

        for (val entry : testCases.entrySet()) {
            val provider = entry.getKey();
            val result = entry.getValue();
            val requestingCurrencyCode = "USD";
            val plans = service.listPlans(provider == null ? null : provider.name(), requestingCurrencyCode);

            assertEquals(result.size(), plans.size());
            for (int i = 0; i < result.size(); i++) {
                val expecting = result.get(i);
                val got = plans.get(i);

                assertEquals(expecting.getId(), got.getId());
                assertEquals(expecting.getProvider().name().toLowerCase(), got.getProvider().toLowerCase());
                assertEquals(expecting.getBillingPeriodMonths(), got.getBillingPeriodMonths());
                assertEquals(expecting.getTrialPeriodDays(), got.getTrialPeriodDays());
                assertEquals(expecting.getPriceInIndianPaise(), got.getPriceInIndianPaise());
                assertEquals(expecting.getPriceInIndianPaise() / 100.0, got.getPriceInRequestedCurrency());
                assertEquals(requestingCurrencyCode, got.getRequestedCurrencyCode());
                assertEquals(
                    expecting.getProvider() == SubscriptionPlan.Provider.GOOGLE_PLAY
                        ? expecting.getProvidedId()
                        : null,
                    got.getGooglePlaySubscriptionId());
            }
        }
    }

    @Test
    void listPlans_withUnsupportedProvider() {
        assertThrows(
            UnsupportedSubscriptionPlanProviderException.class,
            () -> service.listPlans("unsupported-provider", null));
    }

    @Test
    void createSubscription_withExistingActiveSubscription() {
        val userId = 1L;
        when(subscriptionPlanRepository.findById((short) 1))
            .thenReturn(Optional.of(buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "provider-plan-id")));

        when(subscriptionRepository.existsActiveByCustomerUserId(userId))
            .thenReturn(true);

        val params = new SubscriptionFlowParams((short) 1, "success-url", "cancel-url");
        assertThrows(DuplicateSubscriptionException.class, () -> service.createSubscription(userId, params));
    }

    @Test
    void createSubscription_withInvalidPlanId() {
        val userId = 1L;
        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.empty());
        assertThrows(SubscriptionPlanNotFoundException.class, () -> service.createSubscription(userId, params));
    }

    @Test
    void createSubscription_withStripeApiError() throws Exception {
        val userId = 1L;
        val stripePriceId = "stripe-price-id-1";
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, stripePriceId);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findById(planId))
            .thenReturn(Optional.of(plan));

        when(customerRepository.findById(userId))
            .thenReturn(Optional.of(Customer.builder().userId(userId).build()));

        val subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture()))
            .thenAnswer((Answer<Subscription>) invocation -> subscriptionCaptor.getValue());

        when(stripeApi.createCheckoutSession(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new ApiConnectionException("test-error"));

        assertThrows(RuntimeException.class, () -> service.createSubscription(userId, params));
    }

    @Test
    void createSubscription_withValidParams() throws Exception {
        val userId = 1L;
        val stripePriceId = "stripe-price-id-1";
        val stripeCustomerId = "stripe-customer-id";
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, stripePriceId);

        val planId = (short) 1;
        val params = new SubscriptionFlowParams(planId, "success-url", "cancel-url");

        when(subscriptionPlanRepository.findById(planId))
            .thenReturn(Optional.of(plan));

        val subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture()))
            .thenAnswer((Answer<Subscription>) invocation -> subscriptionCaptor.getValue());

        when(customerRepository.findById(userId))
            .thenReturn(Optional.of(Customer.builder()
                .userId(userId)
                .stripeId(stripeCustomerId)
                .build()));

        val redirectUrl = "test-redirect-url";
        val mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn(redirectUrl);

        when(
            stripeApi.createCheckoutSession(
                eq(params.getSuccessUrl()),
                eq(params.getCancelUrl()),
                eq(stripePriceId),
                any(),
                eq(null),
                eq(stripeCustomerId),
                any()))
            .thenReturn(mockSession);

        val result = service.createSubscription(userId, params);
        assertNotNull(result);
        assertEquals(redirectUrl, result.getStripeCheckoutSessionUrl());
    }

    @Test
    void listSubscriptions() throws StripeException {
        val userId1 = 1L;
        val userId2 = 2L;
        val plan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provider-id");
        val subscription1 = buildSubscription(userId1, plan, true, true);
        val subscription2 = buildSubscription(userId2, plan, false, false);

        lenient().when(subscriptionRepository.findAllStartedByCustomerUserId(eq(userId1), any()))
            .thenReturn(new PageImpl<>(List.of(subscription1)));

        lenient().when(subscriptionRepository.findAllStartedByCustomerUserId(eq(userId2), any()))
            .thenReturn(new PageImpl<>(List.of(subscription2)));

        lenient().when(stripeApi.createCustomerPortalSession(any(), any()))
            .thenReturn(mock(com.stripe.model.billingportal.Session.class));

        lenient().when(exchangeRatesProvider.getRateForCurrency(any(), any()))
            .thenReturn(Optional.of(1.0));

        val result1 = service.listSubscriptions(userId1, false, null, 0);
        assertEquals(1, result1.size());
        assertEquals(subscription1.getId(), result1.get(0).getId());
        assertNull(result1.get(0).getPlan().getPriceInRequestedCurrency());

        val result2 = service.listSubscriptions(userId2, false, "USD", 0);
        assertEquals(1, result2.size());
        assertEquals(subscription2.getId(), result2.get(0).getId());
        assertNotNull(result2.get(0).getPlan().getPriceInRequestedCurrency());
    }

    @Test
    void getSubscription() {
        // skipped unit tests, wrote integration tests instead.
    }

    @ParameterizedTest
    @MethodSource("cancelSubscriptionTestCases")
    <T extends Throwable> void cancelSubscription(
        @NonNull Subscription subscription,
        @NonNull Long principalId,
        Class<T> expectedException
    ) throws IOException, StripeException {
        when(subscriptionRepository.findById(subscription.getId()))
            .thenReturn(Optional.of(subscription));

        if (expectedException != null) {
            assertThrows(expectedException, () -> service.cancelSubscription(principalId, subscription.getId()));
        } else {
            assertDoesNotThrow(() -> service.cancelSubscription(principalId, subscription.getId()));

            switch (subscription.getPlan().getProvider()) {
                case GOOGLE_PLAY:
                    verify(androidPublisherApi, times(1))
                        .cancelSubscription(subscription.getProvidedId());
                    break;
                case STRIPE:
                    verify(stripeApi, times(1))
                        .cancelSubscription(subscription.getProvidedId());
                    break;
                case GIFT_CARD:
                    break;
                default:
                    throw new RuntimeException("unknown provider");
            }
        }
    }

    static Stream<Arguments> cancelSubscriptionTestCases() {
        val userId1 = 1L;
        val userId2 = 2L;
        val googlePlayPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GOOGLE_PLAY, "test-provided-id");
        val stripePlan = buildSubscriptionPlan(SubscriptionPlan.Provider.STRIPE, "test-provided-id");
        val giftCardPlan = buildSubscriptionPlan(SubscriptionPlan.Provider.GIFT_CARD, "test-provided-id");

        return Stream.of(
            // subscription, principalId, expected exception
            arguments(buildSubscription(userId1, googlePlayPlan, true, false), userId1, null),
            arguments(buildSubscription(userId1, stripePlan, true, false), userId1, null),
            arguments(buildSubscription(userId1, giftCardPlan, true, false), userId1, null),
            arguments(buildSubscription(userId2, googlePlayPlan, true, false), userId1, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, stripePlan, true, false), userId2, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, giftCardPlan, true, false), userId2, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, googlePlayPlan, false, false), userId1, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, stripePlan, false, false), userId1, SubscriptionNotFoundException.class),
            arguments(buildSubscription(userId1, giftCardPlan, false, false), userId1, SubscriptionNotFoundException.class)
        );
    }

    @Test
    void handleGooglePlayWebhookEvent() {
        // The unit test quickly becomes too complex due to all the mocking. The best case scenario
        // here is to try covering as many cases in integration tests as possible.
    }

    @Test
    void handleStripeWebhookEvent() {
        // skipped unit tests, wrote integration tests instead.
    }

    @Test
    void getStripeCustomerPortalUrl_withNonExistingCustomer() {
        val customerId = 1L;
        val returnUrl = "https://api.test/return-url";
        assertThrows(StripeCustomerPortalUrlException.class, () -> service.getStripeCustomerPortalUrl(customerId, returnUrl));
    }

    @Test
    void getStripeCustomerPortalUrl_withNonExistingStripeCustomer() {
        val customerId = 1L;
        val returnUrl = "https://api.test/return-url";
        val customer = Customer.builder()
            .userId(customerId)
            .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        assertThrows(StripeCustomerPortalUrlException.class, () -> service.getStripeCustomerPortalUrl(customerId, returnUrl));
    }

    @Test
    void getStripeCustomerPortalUrl() throws StripeException {
        val customerId = 1L;
        val stripeId = "test-id";
        val returnUrl = "https://api.test/return-url";
        val customer = Customer.builder()
            .userId(customerId)
            .stripeId(stripeId)
            .build();

        val customerPortalUrl = "https://api.test/customer-portal-url";
        val customerPortalSession = mock(com.stripe.model.billingportal.Session.class);
        when(customerPortalSession.getUrl()).thenReturn(customerPortalUrl);
        when(stripeApi.createCustomerPortalSession(stripeId, returnUrl)).thenReturn(customerPortalSession);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        val response = assertDoesNotThrow(() -> service.getStripeCustomerPortalUrl(customerId, returnUrl));
        assertEquals(customerPortalUrl, response.getUrl());
    }

    @Test
    void getGiftCard_withNonExistingCode() {
        val code = "test-code-1";
        when(giftCardRepository.findByCode(code)).thenReturn(Optional.empty());
        assertThrows(GiftCardNotFoundException.class, () -> service.getGiftCard(1L, code));

        when(giftCardRepository.findByCode(code)).thenReturn(Optional.of(buildGiftCard(code, 2L)));
        assertThrows(GiftCardNotFoundException.class, () -> service.getGiftCard(1L, code));
    }

    @Test
    void getGiftCard_withValidCode() {
        val code = "test-code-1";
        val card = buildGiftCard(code, 2L);
        when(giftCardRepository.findByCode(code)).thenReturn(Optional.of(card));
        val response = assertDoesNotThrow(() -> service.getGiftCard(2L, code));
        assertEquals(card.getCode(), response.getCode());
        assertEquals(card.getHourCredits(), response.getHourCredits());
        assertEquals(card.isRedeemed(), response.getIsRedeemed());
        assertEquals(card.getExpiresAt(), response.getExpiresAt());
    }

    @Test
    void redeemGiftCard_withNonExistingCode() {
        val code = "test-code-1";
        when(giftCardRepository.findByCode(code)).thenReturn(Optional.empty());
        assertThrows(GiftCardNotFoundException.class, () -> service.redeemGiftCard(1L, code));

        when(giftCardRepository.findByCode(code)).thenReturn(Optional.of(buildGiftCard(code, 2L)));
        assertThrows(GiftCardNotFoundException.class, () -> service.redeemGiftCard(1L, code));
    }

    @Test
    void redeemGiftCard_withExpiredCode() {
        val code = "test-code-2";
        val card = buildGiftCard(code, 1L);
        card.setExpiresAt(OffsetDateTime.now().minusHours(1));
        when(giftCardRepository.findByCode(code)).thenReturn(Optional.of(card));
        assertThrows(GiftCardExpiredException.class, () -> service.redeemGiftCard(1L, code));
    }

    @Test
    void redeemGiftCard_withRedeemedCode() {
        val code = "test-code-2";
        val card = buildGiftCard(code, 1L);
        card.setRedeemed(true);
        when(giftCardRepository.findByCode(code)).thenReturn(Optional.of(card));
        assertThrows(GiftCardRedeemedException.class, () -> service.redeemGiftCard(1L, code));
    }

    @Test
    void redeemGiftCard_withExistingSubscription() {
        when(subscriptionRepository.existsActiveByCustomerUserId(1L)).thenReturn(true);
        assertThrows(DuplicateSubscriptionException.class, () -> service.redeemGiftCard(1L, "test-code-3"));
    }

    @Test
    void redeemGiftCard_withValidCode() {
        val code = "test-code-2";
        val card = buildGiftCard(code, 1L);
        when(giftCardRepository.findByCode(code)).thenReturn(Optional.of(card));
        when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        val response = assertDoesNotThrow(() -> service.redeemGiftCard(1L, code));
        assertEquals(card.getCode(), response.getGiftCardCode());
    }

    @Test
    void isUserSubscribed() {
        val testCases = Map.of(
            1L, true,
            2L, false
        );

        testCases.forEach((userId, isSubscribed) ->
            when(subscriptionRepository.existsActiveByCustomerUserId(userId))
                .thenReturn(isSubscribed));

        testCases.forEach((userId, isSubscribed) ->
            assertEquals(isSubscribed, service.isUserSubscribed(userId)));
    }

    @NonNull
    private static SubscriptionPlan buildSubscriptionPlan(@NonNull SubscriptionPlan.Provider provider, @NonNull String providedId) {
        return SubscriptionPlan.builder()
            .id((short) Math.round(Math.random() * 1000))
            .provider(provider)
            .providedId(providedId)
            .billingPeriodMonths((short) 2)
            .trialPeriodDays((short) 1)
            .priceInIndianPaise(22500)
            .build();
    }

    @NonNull
    private static Subscription buildSubscription(
        long ownerId,
        @NonNull SubscriptionPlan plan,
        boolean isActive,
        boolean isPaymentPending
    ) {
        val now = OffsetDateTime.now();
        return Subscription.builder()
            .id(Math.round(Math.random() * 1000))
            .customer(Customer.builder().userId(ownerId).build())
            .plan(plan)
            .providedId(UUID.randomUUID().toString())
            .isPaymentPending(isPaymentPending)
            .startAt(now.plusHours(-2))
            .endAt(now.plusHours(isActive ? 2 : -1))
            .build();
    }

    @NonNull
    private static GiftCard buildGiftCard(@NonNull String code, Long customerId) {
        return GiftCard.builder()
            .code(code)
            .customer(customerId == null ? null : Customer.builder().userId(customerId).build())
            .plan(buildSubscriptionPlan(SubscriptionPlan.Provider.GIFT_CARD, "gift-card"))
            .build();
    }
}

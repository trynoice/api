package com.trynoice.api.subscription;

import com.stripe.exception.StripeException;
import com.trynoice.api.contracts.AccountServiceContract;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.CustomerRepository;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import com.trynoice.api.subscription.entities.SubscriptionRepository;
import com.trynoice.api.subscription.payload.GooglePlayDeveloperNotification;
import com.trynoice.api.subscription.payload.GooglePlaySubscriptionPurchase;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.trynoice.api.subscription.SubscriptionTestUtils.buildSubscriptionPlan;
import static com.trynoice.api.testing.AuthTestUtils.createAuthUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
public class SubscriptionServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @MockBean
    private AndroidPublisherApi androidPublisherApi;

    @MockBean
    private StripeApi stripeApi;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private SubscriptionService service;

    @ParameterizedTest
    @MethodSource("handleGooglePlayWebhookEventTestCases")
    void handleGooglePlayWebhookEvent(
        @NonNull GooglePlaySubscriptionPurchase purchase,
        boolean wasActive,
        boolean wasPaymentPending,
        boolean isActive,
        boolean isPaymentPending,
        boolean shouldAcknowledgePurchase
    ) throws Exception {
        val purchaseToken = UUID.randomUUID().toString();
        val authUser = createAuthUser(entityManager);
        val customer = buildCustomer(authUser, null);
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GOOGLE_PLAY, purchase.getProductId());
        val subscription = buildSubscription(customer, plan, wasActive, wasPaymentPending, wasActive ? purchaseToken : null);
        purchase = purchase.withObfuscatedExternalAccountId(String.valueOf(subscription.getId()));
        when(androidPublisherApi.getSubscriptionPurchase(purchaseToken))
            .thenReturn(purchase);

        service.handleGooglePlayDeveloperNotification(
            buildGooglePlayDeveloperNotification(
                wasActive
                    ? GooglePlayDeveloperNotification.SubscriptionNotification.TYPE_RENEWED
                    : GooglePlayDeveloperNotification.SubscriptionNotification.TYPE_PURCHASED,
                purchaseToken));

        assertEquals(isActive, subscription.isActive());
        assertEquals(isPaymentPending, subscription.isPaymentPending());
        assertEquals(purchaseToken, subscription.getProvidedId());
        assertTrue(subscription.getCustomer().isTrialPeriodUsed());

        verify(androidPublisherApi, times(shouldAcknowledgePurchase ? 1 : 0))
            .acknowledgePurchase(purchase.getProductId(), purchaseToken);
    }

    static Stream<Arguments> handleGooglePlayWebhookEventTestCases() {
        val futureMillis = System.currentTimeMillis() + 60 * 60 * 1000L;
        val pastMillis = System.currentTimeMillis() - 60 * 60 * 1000L;
        val basePurchase = GooglePlaySubscriptionPurchase.builder()
            .productId("test-product-id")
            .startTimeMillis(System.currentTimeMillis())
            .isTestPurchase(true)
            .build();

        return Stream.of(
            // purchase, was subscription active, was payment pending, is subscription active, is payment pending, should acknowledge
            arguments(
                basePurchase.withExpiryTimeMillis(futureMillis)
                    .withPaymentPending(false)
                    .withAcknowledged(true),
                false, false,
                true, false,
                false),
            arguments(
                basePurchase.withExpiryTimeMillis(futureMillis)
                    .withPaymentPending(false)
                    .withAcknowledged(false),
                true, false,
                true, false,
                true),
            arguments(
                basePurchase.withExpiryTimeMillis(futureMillis)
                    .withPaymentPending(false)
                    .withAcknowledged(false),
                true, true,
                true, false,
                true),
            arguments(
                basePurchase.withExpiryTimeMillis(futureMillis)
                    .withPaymentPending(false)
                    .withAcknowledged(false),
                false, false,
                true, false,
                true),
            arguments(
                basePurchase.withExpiryTimeMillis(futureMillis)
                    .withPaymentPending(true)
                    .withAcknowledged(false),
                false, false,
                true, true,
                true),
            arguments(
                basePurchase.withExpiryTimeMillis(pastMillis)
                    .withPaymentPending(false)
                    .withAcknowledged(true),
                false, false,
                false, false,
                false),
            arguments(
                basePurchase.withExpiryTimeMillis(pastMillis)
                    .withPaymentPending(false)
                    .withAcknowledged(false),
                false, false,
                false, false,
                true)
        );
    }

    @Test
    void handleGooglePlayWebhookEvent_planUpgrade() throws Exception {
        val oldPlan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GOOGLE_PLAY, "test-plan-1");
        val newPlan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GOOGLE_PLAY, "test-plan-2");
        val oldPurchaseToken = UUID.randomUUID().toString();
        val newPurchaseToken = UUID.randomUUID().toString();
        val authUser = createAuthUser(entityManager);
        val customer = buildCustomer(authUser, null);
        val subscription = buildSubscription(customer, oldPlan, true, false, oldPurchaseToken);
        val purchase = GooglePlaySubscriptionPurchase.builder()
            .productId(newPlan.getProvidedId())
            .startTimeMillis(System.currentTimeMillis())
            .expiryTimeMillis(System.currentTimeMillis() + 60 * 60 * 1000L)
            .isPaymentPending(false)
            .isAcknowledged(true)
            .linkedPurchaseToken(oldPurchaseToken)
            .obfuscatedExternalAccountId(String.valueOf(subscription.getId()))
            .isTestPurchase(true)
            .build();

        when(androidPublisherApi.getSubscriptionPurchase(newPurchaseToken))
            .thenReturn(purchase);

        service.handleGooglePlayDeveloperNotification(
            buildGooglePlayDeveloperNotification(
                GooglePlayDeveloperNotification.SubscriptionNotification.TYPE_RENEWED,
                newPurchaseToken));

        assertEquals(newPlan.getProvidedId(), subscription.getPlan().getProvidedId());
        assertEquals(newPurchaseToken, subscription.getProvidedId());
    }

    @Test
    void handleGooglePlayWebhookEvent_doublePurchase() throws Exception {
        // when user initiates purchase flow twice without completion and then goes on to complete
        // both the flows.
        val authUser = createAuthUser(entityManager);
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GOOGLE_PLAY, "test-plan");
        val purchaseToken = UUID.randomUUID().toString();
        val customer = buildCustomer(authUser, null);
        val subscription1 = buildSubscription(customer, plan, true, false, UUID.randomUUID().toString());
        val subscription2 = buildSubscription(customer, plan, false, false, null);
        val purchase = GooglePlaySubscriptionPurchase.builder()
            .productId(plan.getProvidedId())
            .startTimeMillis(System.currentTimeMillis())
            .expiryTimeMillis(System.currentTimeMillis() * 60 * 60 * 1000L)
            .isPaymentPending(true)
            .isAcknowledged(false)
            .obfuscatedExternalAccountId(String.valueOf(subscription2.getId()))
            .isTestPurchase(true)
            .build();

        when(androidPublisherApi.getSubscriptionPurchase(purchaseToken))
            .thenReturn(purchase);

        service.handleGooglePlayDeveloperNotification(
            buildGooglePlayDeveloperNotification(
                GooglePlayDeveloperNotification.SubscriptionNotification.TYPE_PURCHASED,
                purchaseToken));

        assertTrue(subscription1.isActive());
        assertFalse(subscription2.isActive());
        verify(androidPublisherApi, times(0)).acknowledgePurchase(plan.getProvidedId(), purchaseToken);
    }

    @Test
    void onUserDeleted() throws StripeException {
        val plan = buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.STRIPE, "stripe-test-plan");
        val deletedCustomers = IntStream.range(0, 5)
            .mapToObj(i -> buildCustomer(createAuthUser(entityManager), "stripe-customer-" + i))
            .collect(Collectors.toUnmodifiableList());

        val activeCustomers = IntStream.range(5, 10)
            .mapToObj(i -> buildCustomer(createAuthUser(entityManager), "stripe-customer-" + i))
            .collect(Collectors.toUnmodifiableList());

        val activeSubscriptions = IntStream.range(0, 5)
            .mapToObj(i -> i % 2 == 0 ? deletedCustomers.get(i) : activeCustomers.get(i))
            .map(c -> buildSubscription(c, plan, true, false, "stripe-subscription-" + c.getUserId()))
            .collect(Collectors.toUnmodifiableList());

        // TODO: how to test it using ApplicationEventPublisher#publishEvent? Since the following is
        //  a TransactionalEventListener, it probably gets invoked after the test has finished.
        deletedCustomers.forEach(c -> service.onUserDeleted(new AccountServiceContract.UserDeletedEvent(c.getUserId())));

        for (val deletedCustomer : deletedCustomers) {
            verify(stripeApi, times(1)).resetCustomerNameAndEmail(deletedCustomer.getStripeId());
        }

        for (val activeCustomer : activeCustomers) {
            verify(stripeApi, times(0)).resetCustomerNameAndEmail(activeCustomer.getStripeId());
        }

        activeSubscriptions.stream()
            .filter(s -> deletedCustomers.contains(s.getCustomer()))
            .map(s -> subscriptionRepository.findById(s.getId()).orElseThrow())
            .forEach(s -> assertFalse(s.isActive()));

        activeSubscriptions.stream()
            .filter(s -> activeCustomers.contains(s.getCustomer()))
            .map(s -> subscriptionRepository.findById(s.getId()).orElseThrow())
            .forEach(s -> assertTrue(s.isActive()));
    }

    @NonNull
    private Subscription buildSubscription(
        @NonNull Customer customer,
        @NonNull SubscriptionPlan plan,
        boolean isActive,
        boolean isPaymentPending,
        String providedId
    ) {
        return subscriptionRepository.save(
            Subscription.builder()
                .customer(customer)
                .plan(plan)
                .providedId(providedId)
                .isPaymentPending(isPaymentPending)
                .startAt(OffsetDateTime.now().plusHours(-2))
                .endAt(OffsetDateTime.now().plusHours(isActive ? 2 : -1))
                .build());
    }

    @NonNull
    private Customer buildCustomer(@NonNull AuthUser user, String stripeCustomerId) {
        return customerRepository.save(
            Customer.builder()
                .userId(user.getId())
                .stripeId(stripeCustomerId)
                .build());
    }

    @NonNull
    private static GooglePlayDeveloperNotification buildGooglePlayDeveloperNotification(int type, @NonNull String purchaseToken) {
        return GooglePlayDeveloperNotification.builder()
            .version("")
            .packageName("test-package-name")
            .eventTimeMillis(System.currentTimeMillis())
            .subscriptionNotification(
                GooglePlayDeveloperNotification.SubscriptionNotification.builder()
                    .version("")
                    .notificationType(type)
                    .purchaseToken(purchaseToken)
                    .build())
            .build();
    }
}

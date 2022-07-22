package com.trynoice.api.subscription;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.checkout.Session;
import com.trynoice.api.identity.entities.AuthUser;
import com.trynoice.api.subscription.entities.Customer;
import com.trynoice.api.subscription.entities.GiftCard;
import com.trynoice.api.subscription.entities.Subscription;
import com.trynoice.api.subscription.entities.SubscriptionPlan;
import lombok.NonNull;
import lombok.val;

import javax.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public class SubscriptionTestUtils {

    @NonNull
    static SubscriptionPlan buildSubscriptionPlan(
        @NonNull EntityManager entityManager,
        @NonNull SubscriptionPlan.Provider provider,
        @NonNull String providedId
    ) {
        val plan = SubscriptionPlan.builder()
            .provider(provider)
            .providedId(providedId)
            .billingPeriodMonths((short) 1)
            .trialPeriodDays((short) 1)
            .priceInIndianPaise(10000)
            .build();

        return entityManager.merge(plan);
    }

    @NonNull
    static Subscription buildSubscription(
        @NonNull EntityManager entityManager,
        @NonNull AuthUser owner,
        @NonNull SubscriptionPlan plan,
        boolean isActive,
        boolean isPaymentPending,
        String providedId
    ) {
        val subscription = Subscription.builder()
            .customer(buildCustomer(entityManager, owner))
            .plan(plan)
            .providedId(providedId)
            .isPaymentPending(isPaymentPending)
            .startAt(OffsetDateTime.now().plusHours(-2))
            .endAt(OffsetDateTime.now().plusHours(isActive ? 2 : -1))
            .build();

        return entityManager.merge(subscription);
    }

    @NonNull
    static Customer buildCustomer(@NonNull EntityManager entityManager, @NonNull AuthUser user) {
        val customer = Customer.builder()
            .userId(user.getId())
            .stripeId(UUID.randomUUID().toString())
            .build();

        return entityManager.merge(customer);
    }

    @NonNull
    static GiftCard buildGiftCard(
        @NonNull EntityManager entityManager,
        @NonNull String code,
        Customer customer,
        boolean isRedeemed,
        Boolean isExpired
    ) {
        val giftCard = GiftCard.builder()
            .code(code)
            .hourCredits((short) 1)
            .plan(buildSubscriptionPlan(entityManager, SubscriptionPlan.Provider.GIFT_CARD, "gift-card"))
            .customer(customer)
            .isRedeemed(isRedeemed)
            .expiresAt(isExpired == null ? null : OffsetDateTime.now().plusHours(isExpired ? -1 : 1))
            .build();

        return entityManager.merge(giftCard);
    }

    @NonNull
    static Event buildStripeEvent(@NonNull String type, @NonNull StripeObject dataObject) {
        // TODO: maybe find a fix in free time.
        // mock because event data object serialization/deserialization is confusing and all my
        // attempts failed.
        val event = mock(Event.class);
        lenient().when(event.getType()).thenReturn(type);

        val deserializer = mock(EventDataObjectDeserializer.class);
        lenient().when(deserializer.getObject()).thenReturn(Optional.of(dataObject));
        lenient().when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        lenient().when(event.toJson()).thenReturn("{}");
        return event;
    }

    @NonNull
    static Session buildStripeCheckoutSession(@NonNull String status, @NonNull String paymentStatus, String clientReferenceId) {
        val session = new Session();
        session.setMode("subscription");
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);
        session.setSubscription(UUID.randomUUID().toString());
        session.setCustomer(UUID.randomUUID().toString());
        session.setClientReferenceId(clientReferenceId);
        return session;
    }

    @NonNull
    static com.stripe.model.Subscription buildStripeSubscription(String id, @NonNull String status, @NonNull String priceId) {
        val subscription = new com.stripe.model.Subscription();
        subscription.setId(id);
        subscription.setStatus(status);

        val now = OffsetDateTime.now().toEpochSecond();
        subscription.setCurrentPeriodStart(now);
        subscription.setStartDate(now);
        subscription.setCurrentPeriodEnd(now + 60 * 60);

        val items = new SubscriptionItemCollection();
        items.setData(List.of(buildSubscriptionItem(priceId)));
        subscription.setItems(items);
        return subscription;
    }

    @NonNull
    static SubscriptionItem buildSubscriptionItem(@NonNull String priceId) {
        val price = new Price();
        price.setId(priceId);

        val subscriptionItem = new SubscriptionItem();
        subscriptionItem.setPrice(price);
        return subscriptionItem;
    }
}

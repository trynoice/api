package com.trynoice.api.subscription;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.NonNull;
import lombok.val;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A thin-wrapper around {@link AndroidPublisher} API to enable easy mocking.
 */
public class AndroidPublisherApi {

    private final AndroidPublisher client;

    public AndroidPublisherApi(
        @NonNull GoogleCredentials credentials,
        @NonNull String applicationName
    ) throws GeneralSecurityException, IOException {
        client = new AndroidPublisher.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials))
            .setApplicationName(applicationName)
            .build();
    }

    /**
     * @see com.google.api.services.androidpublisher.AndroidPublisher.Purchases.Subscriptions#get(String, String, String)
     */
    @NonNull
    SubscriptionPurchase getSubscriptionPurchase(
        @NonNull String applicationId,
        @NonNull String subscriptionId,
        @NonNull String purchaseToken
    ) throws IOException {
        return client.purchases()
            .subscriptions()
            .get(applicationId, subscriptionId, purchaseToken)
            .execute();
    }

    /**
     * @see com.google.api.services.androidpublisher.AndroidPublisher.Purchases.Subscriptions#acknowledge(
     *String, String, String, SubscriptionPurchasesAcknowledgeRequest)
     */
    void acknowledgePurchase(@NonNull String applicationId, @NonNull String subscriptionId, @NonNull String purchaseToken) throws IOException {
        client.purchases()
            .subscriptions()
            .acknowledge(applicationId, subscriptionId, purchaseToken, new SubscriptionPurchasesAcknowledgeRequest())
            .execute();
    }

    /**
     * Cancels subscription purchases that renew automatically.
     *
     * @see com.google.api.services.androidpublisher.AndroidPublisher.Purchases.Subscriptions#cancel(String, String, String)
     * @see <a href="https://developer.android.com/google/play/billing/subscriptions#cancel">Cancellations</a>
     */
    void cancelSubscription(
        @NonNull String applicationId,
        @NonNull String subscriptionId,
        @NonNull String purchaseToken
    ) throws IOException {
        val purchase = getSubscriptionPurchase(applicationId, subscriptionId, purchaseToken);
        if (Boolean.FALSE.equals(purchase.getAutoRenewing())) {
            // subscription is already cancelled.
            // https://developer.android.com/google/play/billing/subscriptions#cancel
            return;
        }

        client.purchases()
            .subscriptions()
            .cancel(applicationId, subscriptionId, purchaseToken)
            .execute();
    }
}

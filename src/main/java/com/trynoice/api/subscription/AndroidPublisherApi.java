package com.trynoice.api.subscription;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.trynoice.api.subscription.payload.GooglePlaySubscriptionPurchase;
import lombok.NonNull;
import lombok.val;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A thin-wrapper around {@link AndroidPublisher} API to enable easy mocking.
 */
public class AndroidPublisherApi {

    private final AndroidPublisher client;
    private final String clientAppId;

    public AndroidPublisherApi(
        @NonNull GoogleCredentials credentials,
        @NonNull String serverAppName,
        @NonNull String clientAppId
    ) throws GeneralSecurityException, IOException {
        client = new AndroidPublisher.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials))
            .setApplicationName(serverAppName)
            .build();

        this.clientAppId = clientAppId;
    }

    /**
     * @see com.google.api.services.androidpublisher.AndroidPublisher.Purchases.Subscriptionsv2#get(String, String)
     */
    @NonNull
    GooglePlaySubscriptionPurchase getSubscriptionPurchase(@NonNull String purchaseToken) throws IOException {
        return new GooglePlaySubscriptionPurchase(
            client.purchases()
                .subscriptionsv2()
                .get(clientAppId, purchaseToken)
                .execute());
    }

    /**
     * @see com.google.api.services.androidpublisher.AndroidPublisher.Purchases.Subscriptions#acknowledge(
     *String, String, String, SubscriptionPurchasesAcknowledgeRequest)
     */
    void acknowledgePurchase(@NonNull String productId, @NonNull String purchaseToken) throws IOException {
        client.purchases()
            .subscriptions()
            .acknowledge(clientAppId, productId, purchaseToken, new SubscriptionPurchasesAcknowledgeRequest())
            .execute();
    }

    /**
     * Cancels subscription purchases that renew automatically.
     *
     * @see com.google.api.services.androidpublisher.AndroidPublisher.Purchases.Subscriptions#cancel(String, String, String)
     * @see <a href="https://developer.android.com/google/play/billing/subscriptions#cancel">Cancellations</a>
     */
    void cancelSubscription(@NonNull String purchaseToken) throws IOException {
        val purchase = getSubscriptionPurchase(purchaseToken);
        if ("SUBSCRIPTION_STATE_CANCELED".equals(purchase.getSubscriptionState())) {
            // subscription is already cancelled.
            // https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2#subscriptionstate
            return;
        }

        client.purchases()
            .subscriptions()
            .cancel(clientAppId, purchase.getProductId(), purchaseToken)
            .execute();
    }
}

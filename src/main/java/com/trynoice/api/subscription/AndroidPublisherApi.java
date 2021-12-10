package com.trynoice.api.subscription;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.NonNull;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A thin-wrapper around {@link AndroidPublisher} API to enable easy mocking.
 */
public class AndroidPublisherApi {

    private final AndroidPublisher client;

    public AndroidPublisherApi(@NonNull GoogleCredentials credentials) throws GeneralSecurityException, IOException {
        client = new AndroidPublisher.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials))
            .build();
    }

    /**
     * Checks whether a user's subscription purchase is valid and returns its expiry time.
     *
     * @param applicationId  The package name of the application for which this subscription was
     *                       purchased (for example, 'com.some.thing').
     * @param subscriptionId The purchased subscription ID (for example, 'monthly001').
     * @param purchaseToken  The token provided to the user's device when the subscription was
     *                       purchased.
     * @return a non-null {@link SubscriptionPurchase}
     * @throws IOException on request failure
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
     * Acknowledges a subscription purchase.
     *
     * @param applicationId  The package name of the application for which this subscription was
     *                       purchased (for example, 'com.some.thing').
     * @param subscriptionId The purchased subscription ID (for example, 'monthly001').
     * @param purchaseToken  The token provided to the user's device when the subscription was
     *                       purchased.
     * @throws IOException on request failure.
     */
    void acknowledgePurchase(@NonNull String applicationId, @NonNull String subscriptionId, @NonNull String purchaseToken) throws IOException {
        client.purchases()
            .subscriptions()
            .acknowledge(applicationId, subscriptionId, purchaseToken, new SubscriptionPurchasesAcknowledgeRequest())
            .execute();
    }
}

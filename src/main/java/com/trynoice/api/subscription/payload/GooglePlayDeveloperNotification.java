package com.trynoice.api.subscription.payload;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * Developer Notifications received from Google Play though GCP Cloud pub/sub subscription.
 *
 * @see <a href="https://developer.android.com/google/play/billing/rtdn-reference">RTDN Reference</a>
 */
@Data
@Builder
public class GooglePlayDeveloperNotification {

    // omitting the fields that we're not using, namely `oneTimeProductNotification` and
    // `testNotification`.

    /**
     * The version of this notification. Initially, this is "1.0". This version is distinct from
     * other version fields.
     */
    @NonNull
    private final String version;

    /**
     * The package name of the app that this notification relates to (e.g. `com.some.thing`).
     */
    @NonNull
    private final String packageName;

    /**
     * The timestamp when the event occurred, in milliseconds since the Epoch.
     */
    private final long eventTimeMillis;

    /**
     * If this field is present, then this notification is related to a subscription, and this field
     * contains additional information related to the subscription. Note that this field is mutually
     * exclusive with `testNotification` and `oneTimeProductNotification`.
     */
    private SubscriptionNotification subscriptionNotification;

    /**
     * Holds additional information related to the subscription in {@link
     * GooglePlayDeveloperNotification}.
     */
    @Data
    @Builder
    public static class SubscriptionNotification {

        /**
         * A subscription was recovered from account hold.
         */
        public final static int TYPE_RECOVERED = 1;

        /**
         * An active subscription was renewed.
         */
        public final static int TYPE_RENEWED = 2;

        /**
         * A subscription was either voluntarily or involuntarily cancelled. For voluntary
         * cancellation, sent when the user cancels.
         */
        public final static int TYPE_CANCELED = 3;

        /**
         * A new subscription was purchased.
         */
        public final static int TYPE_PURCHASED = 4;

        /**
         * A subscription has entered account hold (if enabled).
         */
        public final static int TYPE_ON_HOLD = 5;

        /**
         * A subscription has entered grace period (if enabled).
         */
        public final static int TYPE_IN_GRACE_PERIOD = 6;

        /**
         * User has restored their subscription from Play > Account > Subscriptions. The
         * subscription was canceled but had not expired yet when the user restores. For more
         * information.
         *
         * @see <a href="https://developer.android.com/google/play/billing/subscriptions#restore">Restorations</a>
         */
        public final static int TYPE_RESTARTED = 7;

        /**
         * A subscription price change has successfully been confirmed by the user.
         */
        public final static int TYPE_PRICE_CHANGE_CONFIRMED = 8;

        /**
         * A subscription's recurrence time has been extended.
         */
        public final static int TYPE_DEFERRED = 9;

        /**
         * A subscription has been paused.
         */
        public final static int TYPE_PAUSED = 10;

        /**
         * A subscription pause schedule has been changed.
         */
        public final static int TYPE_PAUSE_SCHEDULE_CHANGED = 11;

        /**
         * A subscription has been revoked from the user before the expiration time.
         */
        public final static int TYPE_REVOKED = 12;

        /**
         * A subscription has expired.
         */
        public final static int TYPE_EXPIRED = 13;

        /**
         * The version of this notification. Initially, this is "1.0". This version is distinct from
         * other version fields.
         */
        @NonNull
        private final String version;

        /**
         * The `notificationType` for a subscription can have the following values:
         *
         * <ul>
         * <li>{@link #TYPE_RECOVERED}</li>
         * <li>{@link #TYPE_RENEWED}</li>
         * <li>{@link #TYPE_CANCELED}</li>
         * <li>{@link #TYPE_PURCHASED}</li>
         * <li>{@link #TYPE_ON_HOLD}</li>
         * <li>{@link #TYPE_IN_GRACE_PERIOD}</li>
         * <li>{@link #TYPE_RESTARTED}</li>
         * <li>{@link #TYPE_PRICE_CHANGE_CONFIRMED}</li>
         * <li>{@link #TYPE_DEFERRED}</li>
         * <li>{@link #TYPE_PAUSED}</li>
         * <li>{@link #TYPE_PAUSE_SCHEDULE_CHANGED}</li>
         * <li>{@link #TYPE_REVOKED}</li>
         * <li>{@link #TYPE_EXPIRED}</li>
         * </ul>
         */
        private final int notificationType;

        /**
         * The token provided to the user's device when the subscription was purchased.
         */
        @NonNull
        private final String purchaseToken;
    }
}

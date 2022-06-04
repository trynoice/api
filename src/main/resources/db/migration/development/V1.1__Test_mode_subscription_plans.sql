-- Remove subscription plans for production
DELETE FROM subscription_plan;

-- Re-insert subscription plans for test mode
INSERT INTO subscription_plan (created_at, version, provider, provider_plan_id, billing_period_months, trial_period_days, price_in_indian_paise)
  VALUES
    (now(), 0, 'GOOGLE_PLAY', 'monthly', 1, 14, 22500),
    (now(), 0, 'GOOGLE_PLAY', 'quarterly', 3, 14, 60000),
    (now(), 0, 'GOOGLE_PLAY', 'bi_yearly', 6, 14, 105000),
    (now(), 0, 'GOOGLE_PLAY', 'yearly', 12, 14, 180000),
    (now(), 0, 'STRIPE', 'price_1Ko0DFSEeVq01jORuGMXnINT', 1, 14, 22500),
    (now(), 0, 'STRIPE', 'price_1Ko0DFSEeVq01jORt7saWcXM', 3, 14, 60000),
    (now(), 0, 'STRIPE', 'price_1Ko0DFSEeVq01jOR9aCG7EM8', 6, 14, 105000),
    (now(), 0, 'STRIPE', 'price_1Ko0DFSEeVq01jORgHlVkTEN', 12, 14, 180000),
    (now(), 0, 'GIFT_CARD', 'gift-card', 0, 0, 0);

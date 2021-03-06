-- A table to describe the columns that all basic entities must have.
--
-- CREATE TABLE basic_entity (
--   created_at timestamp with time zone NOT NULL,
--   deleted_at timestamp with time zone,
--   version bigint NOT NULL
-- );
--
CREATE TABLE auth_user (
  id bigserial NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  email varchar(64) NOT NULL,
  name varchar(64) NOT NULL,
  last_active_at timestamp with time zone NOT NULL,
  incomplete_sign_in_attempts smallint NOT NULL,
  last_sign_in_attempt_at timestamp with time zone
);

CREATE UNIQUE INDEX auth_user__email__unqiue_idx ON auth_user (email)
WHERE
  deleted_at IS NULL;

CREATE TABLE refresh_token (
  id bigserial NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  owner_id bigint NOT NULL,
  user_agent varchar(128) NOT NULL,
  ordinal bigint NOT NULL,
  expires_at timestamp with time zone NOT NULL
);

CREATE INDEX refresh_token__owner_id__idx ON refresh_token USING btree (owner_id)
WHERE
  deleted_at IS NULL;

CREATE TABLE subscription_plan (
  id smallserial NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  provider varchar(16) NOT NULL,
  provider_plan_id varchar(255) NOT NULL,
  billing_period_months smallint NOT NULL,
  trial_period_days smallint NOT NULL,
  price_in_indian_paise integer NOT NULL
);

INSERT INTO subscription_plan (created_at, version, provider, provider_plan_id, billing_period_months, trial_period_days, price_in_indian_paise)
  VALUES
    (now(), 0, 'GOOGLE_PLAY', 'monthly', 1, 14, 22500),
    (now(), 0, 'GOOGLE_PLAY', 'quarterly', 3, 14, 60000),
    (now(), 0, 'GOOGLE_PLAY', 'bi_yearly', 6, 14, 105000),
    (now(), 0, 'GOOGLE_PLAY', 'yearly', 12, 14, 180000),
    (now(), 0, 'STRIPE', 'price_1L5R9JSEeVq01jORQPQdUC5J', 1, 14, 22500),
    (now(), 0, 'STRIPE', 'price_1L5R9JSEeVq01jORRBhkQFoC', 3, 14, 60000),
    (now(), 0, 'STRIPE', 'price_1L5R9JSEeVq01jORI9QGRC3w', 6, 14, 105000),
    (now(), 0, 'STRIPE', 'price_1L5R9JSEeVq01jORWCxWXcDO', 12, 14, 180000),
    (now(), 0, 'GIFT_CARD', 'gift-card', 0, 0, 0);

CREATE TABLE customer (
  user_id bigint NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  stripe_id varchar(255),
  is_trial_period_used boolean NOT NULL
);

CREATE TABLE subscription (
  id bigserial NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  customer_user_id bigint NOT NULL,
  plan_id smallint NOT NULL,
  provider_subscription_id varchar(255),
  is_payment_pending boolean NOT NULL,
  is_auto_renewing boolean NOT NULL,
  is_refunded boolean NOT NULL,
  start_at timestamp with time zone,
  end_at timestamp with time zone
);

CREATE INDEX subscription__customer_user_id__idx ON subscription USING btree (customer_user_id)
WHERE
  deleted_at IS NULL;

CREATE UNIQUE INDEX subscription__provider_subscription_id__unique_idx ON subscription (provider_subscription_id)
WHERE
  deleted_at IS NULL AND provider_subscription_id IS NOT NULL;

-- A table to describe the columns that all basic entities must have.
--
-- CREATE TABLE basic_entity (
--   id serial NOT NULL PRIMARY KEY,
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
  sign_in_attempts smallint NOT NULL
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
  expires_at timestamp with time zone NOT NULL,
  last_used_at timestamp with time zone NOT NULL
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
  provider_plan_id varchar(32) NOT NULL,
  billing_period_months smallint NOT NULL,
  price_in_indian_paise integer NOT NULL
);

INSERT INTO subscription_plan (created_at, version, provider, provider_plan_id, billing_period_months, price_in_indian_paise)
  VALUES
    (now(), 0, 'GOOGLE_PLAY', 'monthly', 1, 22500),
    (now(), 0, 'GOOGLE_PLAY', 'quarterly', 3, 60000),
    (now(), 0, 'GOOGLE_PLAY', 'bi_yearly', 6, 105000),
    (now(), 0, 'GOOGLE_PLAY', 'yearly', 12, 180000),
    (now(), 0, 'STRIPE', 'price_1K5DVGSEeVq01jORTL7UKS05', 1, 22500),
    (now(), 0, 'STRIPE', 'price_1K5DVGSEeVq01jOR5Utj2H24', 3, 60000),
    (now(), 0, 'STRIPE', 'price_1K5DVGSEeVq01jORPUdnVRBx', 6, 105000),
    (now(), 0, 'STRIPE', 'price_1K5DVGSEeVq01jORenAfLOCj', 12, 180000);

CREATE TABLE subscription (
  id bigserial NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  owner_id bigint NOT NULL,
  plan_id smallint NOT NULL,
  provider_subscription_id varchar(64) NOT NULL,
  status varchar(24) NOT NULL,
  start_at timestamp with time zone NOT NULL,
  end_at timestamp with time zone
);

CREATE UNIQUE INDEX subscription__provider_subscription_id__unique_idx ON subscription (provider_subscription_id)
WHERE
  deleted_at IS NULL;

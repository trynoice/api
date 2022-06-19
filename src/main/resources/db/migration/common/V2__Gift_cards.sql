CREATE TABLE gift_card (
  id bigserial NOT NULL PRIMARY KEY,
  created_at timestamp with time zone NOT NULL,
  deleted_at timestamp with time zone,
  version bigint NOT NULL,
  code varchar(32) NOT NULL,
  hour_credits smallint NOT NULL,
  plan_id smallint NOT NULL,
  customer_user_id bigint,
  is_redeemed boolean NOT NULL,
  expires_at timestamp with time zone
);

CREATE UNIQUE INDEX gift_card__code__unique_idx ON gift_card (code)
WHERE
    deleted_at IS NULL;

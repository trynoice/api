DROP INDEX auth_user__email__unqiue_idx;
ALTER TABLE auth_user RENAME COLUMN deleted_at TO deactivated_at;
CREATE UNIQUE INDEX auth_user__email__unqiue_idx ON auth_user (email);

DROP INDEX refresh_token__owner_id__idx;
ALTER TABLE refresh_token DROP COLUMN deleted_at;
CREATE INDEX refresh_token__owner_id__idx ON refresh_token USING btree (owner_id);

ALTER TABLE subscription_plan
  DROP COLUMN created_at,
  DROP COLUMN deleted_at,
  DROP COLUMN version;

ALTER TABLE customer
  DROP COLUMN created_at,
  DROP COLUMN deleted_at;

DROP INDEX subscription__customer_user_id__idx, subscription__provided_id__unique_idx;
ALTER TABLE subscription DROP COLUMN deleted_at;
CREATE INDEX subscription__customer_user_id__idx ON subscription USING btree (customer_user_id);
CREATE UNIQUE INDEX subscription__provided_id__unique_idx ON subscription (provided_id)
  WHERE provided_id IS NOT NULL;

DROP INDEX gift_card__code__unique_idx;
ALTER TABLE gift_card DROP COLUMN deleted_at;
CREATE UNIQUE INDEX gift_card__code__unique_idx ON gift_card (code);

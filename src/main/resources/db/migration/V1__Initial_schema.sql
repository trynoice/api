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
  id serial NOT NULL PRIMARY KEY,
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
  owner_id integer NOT NULL,
  user_agent varchar(128) NOT NULL,
  expires_at timestamp with time zone NOT NULL
);

CREATE INDEX refresh_token__owner_id__idx ON refresh_token USING btree (owner_id)
WHERE
  deleted_at IS NULL;

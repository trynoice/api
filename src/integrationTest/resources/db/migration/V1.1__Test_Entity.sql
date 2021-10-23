 CREATE TABLE test_entity (
   id serial NOT NULL PRIMARY KEY,
   created_at timestamp with time zone NOT NULL,
   deleted_at timestamp with time zone,
   version bigint NOT NULL
 );

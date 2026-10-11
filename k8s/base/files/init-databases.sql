-- One database per service: each service owns its schema (Flyway) and nothing else can couple to it.
-- Runs once, on the first start of an empty Postgres data directory.
CREATE DATABASE inventory;
CREATE DATABASE orders;

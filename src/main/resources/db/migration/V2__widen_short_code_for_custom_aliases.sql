-- Custom aliases share the short_code column and may contain up to 50 characters.
ALTER TABLE urls ALTER COLUMN short_code TYPE VARCHAR(50);

-- Reading that raised each alert, so clients can phrase it in their own language.
-- Nullable: alerts raised before this version keep only their message.
ALTER TABLE alerts ADD COLUMN measured_value NUMERIC(6, 2);
ALTER TABLE alerts ADD COLUMN minimum_value NUMERIC(6, 2);
ALTER TABLE alerts ADD COLUMN maximum_value NUMERIC(6, 2);

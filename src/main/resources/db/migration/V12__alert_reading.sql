-- Reading that raised each alert, so clients can phrase it in their own language.
-- Nullable: alerts raised before this version keep only their message.
ALTER TABLE alerts ADD COLUMN measured_value NUMERIC(6, 2);
ALTER TABLE alerts ADD COLUMN minimum_value NUMERIC(6, 2);
ALTER TABLE alerts ADD COLUMN maximum_value NUMERIC(6, 2);

-- Earlier alerts recorded the same reading only inside their message; recover it from there.
UPDATE alerts
SET measured_value = CAST(REGEXP_SUBSTR(message, 'medicion (-?[0-9]+(\.[0-9]+)?) \(rango seguro', 1, 1, 'c', 1) AS NUMERIC(6, 2)),
    minimum_value = CAST(REGEXP_SUBSTR(message, 'rango seguro (-?[0-9]+(\.[0-9]+)?) a ', 1, 1, 'c', 1) AS NUMERIC(6, 2)),
    maximum_value = CAST(REGEXP_SUBSTR(message, ' a (-?[0-9]+(\.[0-9]+)?)\)$', 1, 1, 'c', 1) AS NUMERIC(6, 2))
WHERE measured_value IS NULL
  AND message LIKE '%medicion % (rango seguro % a %)';

ALTER TABLE alerts DROP COLUMN severity;
ALTER TABLE incidents ADD COLUMN severity VARCHAR(10);   -- P1-P4, AI điền

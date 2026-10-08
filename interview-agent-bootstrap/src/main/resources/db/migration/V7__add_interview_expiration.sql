ALTER TABLE interview
    ADD COLUMN expires_at TIMESTAMP NULL COMMENT 'Interview 硬截止时间' AFTER current_phase;

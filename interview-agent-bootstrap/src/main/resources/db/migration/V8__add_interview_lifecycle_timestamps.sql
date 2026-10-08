ALTER TABLE interview
    ADD COLUMN started_at TIMESTAMP NULL COMMENT 'Interview 正式启动时间' AFTER created_at,
    ADD COLUMN completed_at TIMESTAMP NULL COMMENT 'Interview 首次完成时间' AFTER started_at;

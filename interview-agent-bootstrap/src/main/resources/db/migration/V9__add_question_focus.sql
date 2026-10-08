ALTER TABLE question
    ADD COLUMN focus VARCHAR(500) NULL COMMENT 'MAIN Question 考察焦点；FOLLOW_UP 不单独设置' AFTER question_content;

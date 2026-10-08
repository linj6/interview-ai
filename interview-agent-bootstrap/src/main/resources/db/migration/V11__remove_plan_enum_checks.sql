-- Enum values and mode/focusArea combinations are validated in Java.
-- Preserve NOT NULL columns and structural unique constraints from V10.
ALTER TABLE interview DROP CHECK ck_interview_mode_area;
ALTER TABLE interview_plan_item DROP CHECK ck_plan_item_area;

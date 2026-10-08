-- Remove the obsolete Question focus; PlanItem.focus owns new V1 directions.
-- Keep historical NULL plan_item_id values; NOT NULL enforcement is deferred.
-- Do not backfill, synthesize PlanItems, or delete historical Questions.
ALTER TABLE question
    DROP COLUMN focus;

ALTER TABLE `environment`
    ADD COLUMN IF NOT EXISTS `project_id` bigint(20) unsigned DEFAULT NULL COMMENT '项目id';

CREATE INDEX IF NOT EXISTS idx_environment_project_id on environment (`project_id`);

UPDATE `environment` e
SET e.`project_id` = (
    SELECT d.`project_id`
    FROM `data_source` d
    WHERE d.`environment_id` = e.`id`
      AND d.`project_id` IS NOT NULL
    LIMIT 1
)
WHERE e.`project_id` IS NULL
  AND EXISTS (
      SELECT 1
      FROM `data_source` d2
      WHERE d2.`environment_id` = e.`id`
        AND d2.`project_id` IS NOT NULL
  );

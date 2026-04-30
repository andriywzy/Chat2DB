INSERT INTO `project` (`id`, `gmt_create`, `gmt_modified`, `user_id`, `name`, `description`, `scope_type`, `scope_id`)
SELECT g.`id`,
       g.`gmt_create`,
       g.`gmt_modified`,
       g.`user_id`,
       g.`name`,
       NULL,
       'USER',
       g.`user_id`
FROM `data_source_group` g
WHERE NOT EXISTS (
    SELECT 1
    FROM `project` p
    WHERE p.`id` = g.`id`
);

UPDATE `data_source` d
SET d.`project_id` = (
    SELECT m.`group_id`
    FROM `data_source_group_mapping` m
    WHERE m.`data_source_id` = d.`id`
    LIMIT 1
)
WHERE d.`project_id` IS NULL
  AND EXISTS (
      SELECT 1
      FROM `data_source_group_mapping` m2
      WHERE m2.`data_source_id` = d.`id`
  );

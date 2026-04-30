DELETE FROM data_source_group_mapping
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY data_source_id ORDER BY gmt_modified DESC, id DESC) AS rn
        FROM data_source_group_mapping
    ) t
    WHERE t.rn > 1
);

DROP INDEX IF EXISTS uk_data_source_group_mapping_user_id_data_source_id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_data_source_group_mapping_data_source_id
    ON data_source_group_mapping (data_source_id);

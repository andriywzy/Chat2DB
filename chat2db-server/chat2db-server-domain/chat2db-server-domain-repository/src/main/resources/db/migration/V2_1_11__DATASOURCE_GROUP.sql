CREATE TABLE IF NOT EXISTS `data_source_group`
(
    `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`   datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `user_id`      bigint(20) unsigned NOT NULL COMMENT '用户id',
    `name`         varchar(128)        NOT NULL COMMENT '分组名称',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源分组'
;

CREATE INDEX idx_data_source_group_user_id on data_source_group (`user_id`);

CREATE TABLE IF NOT EXISTS `data_source_group_mapping`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`     datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`   datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `user_id`        bigint(20) unsigned NOT NULL COMMENT '用户id',
    `group_id`       bigint(20) unsigned NOT NULL COMMENT '分组id',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源id',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源分组映射'
;

CREATE INDEX idx_data_source_group_mapping_group_id on data_source_group_mapping (`group_id`);
CREATE INDEX idx_data_source_group_mapping_data_source_id on data_source_group_mapping (`data_source_id`);
CREATE UNIQUE INDEX uk_data_source_group_mapping_user_id_data_source_id
    on data_source_group_mapping (`user_id`, `data_source_id`);

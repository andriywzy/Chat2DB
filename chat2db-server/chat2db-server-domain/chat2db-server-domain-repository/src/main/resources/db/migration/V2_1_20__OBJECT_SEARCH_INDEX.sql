CREATE TABLE IF NOT EXISTS `object_search_sync_status` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源连接ID',
    `last_sync_status` varchar(32) DEFAULT NULL COMMENT '最近同步状态',
    `last_sync_time` datetime DEFAULT NULL COMMENT '最近同步完成时间',
    `last_sync_error` varchar(2048) DEFAULT NULL COMMENT '最近同步错误信息',
    `last_sync_version` bigint(20) unsigned DEFAULT NULL COMMENT '最近成功同步版本',
    `next_sync_time` datetime DEFAULT NULL COMMENT '下次同步时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='object search sync status';
create unique INDEX IF NOT EXISTS uk_object_search_sync_status_data_source_id on object_search_sync_status(`data_source_id`);
create INDEX IF NOT EXISTS idx_object_search_sync_status_next_sync_time on object_search_sync_status(`next_sync_time`);

CREATE TABLE IF NOT EXISTS `object_search_index` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `data_source_id` bigint(20) unsigned NOT NULL COMMENT '数据源连接ID',
    `data_source_name_snapshot` varchar(256) DEFAULT NULL COMMENT '数据源名称快照',
    `database_type` varchar(64) DEFAULT NULL COMMENT '数据库类型',
    `database_name` varchar(256) DEFAULT NULL COMMENT '数据库名称',
    `schema_name` varchar(256) DEFAULT NULL COMMENT 'schema名称',
    `object_type` varchar(32) NOT NULL COMMENT '对象类型',
    `object_name` varchar(512) NOT NULL COMMENT '对象名称',
    `comment` varchar(2048) DEFAULT NULL COMMENT '对象备注',
    `sync_version` bigint(20) unsigned NOT NULL COMMENT '同步版本',
    `deleted` varchar(8) NOT NULL DEFAULT 'N' COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='object search index';
create INDEX IF NOT EXISTS idx_object_search_index_data_source_id on object_search_index(`data_source_id`);
create INDEX IF NOT EXISTS idx_object_search_index_data_source_version on object_search_index(`data_source_id`,`sync_version`);
create INDEX IF NOT EXISTS idx_object_search_index_object_type on object_search_index(`object_type`);
create INDEX IF NOT EXISTS idx_object_search_index_object_name on object_search_index(`object_name`);

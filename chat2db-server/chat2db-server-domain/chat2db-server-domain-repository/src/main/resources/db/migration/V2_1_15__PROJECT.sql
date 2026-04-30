CREATE TABLE IF NOT EXISTS `project`
(
    `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`   datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `user_id`      bigint(20) unsigned NOT NULL COMMENT '创建用户id',
    `name`         varchar(128)        NOT NULL COMMENT '项目名称',
    `description`  varchar(512)                 DEFAULT NULL COMMENT '项目描述',
    `scope_type`   varchar(32)         NOT NULL DEFAULT 'USER' COMMENT '作用域类型',
    `scope_id`     bigint(20) unsigned NOT NULL COMMENT '作用域id',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='项目'
;

CREATE INDEX idx_project_user_id on project (`user_id`);
CREATE INDEX idx_project_scope on project (`scope_type`, `scope_id`);

CREATE TABLE IF NOT EXISTS `project_access`
(
    `id`                 bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`         datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`       datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `project_id`         bigint(20) unsigned NOT NULL COMMENT '项目id',
    `access_object_type` varchar(32)         NOT NULL COMMENT '授权对象类型',
    `access_object_id`   bigint(20) unsigned NOT NULL COMMENT '授权对象id',
    `permission_type`    varchar(32)         NOT NULL DEFAULT 'VIEW' COMMENT '权限类型',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='项目授权'
;

CREATE INDEX idx_project_access_project_id on project_access (`project_id`);
CREATE INDEX idx_project_access_object on project_access (`access_object_type`, `access_object_id`);
CREATE UNIQUE INDEX uk_project_access
    on project_access (`project_id`, `access_object_type`, `access_object_id`);

ALTER TABLE `data_source`
    ADD COLUMN IF NOT EXISTS `project_id` bigint(20) unsigned DEFAULT NULL COMMENT '项目id';

CREATE INDEX IF NOT EXISTS idx_data_source_project_id on data_source (`project_id`);

ALTER TABLE `environment`
    ADD COLUMN IF NOT EXISTS `scope_type` varchar(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域类型';

ALTER TABLE `environment`
    ADD COLUMN IF NOT EXISTS `scope_id` bigint(20) unsigned DEFAULT NULL COMMENT '作用域id';

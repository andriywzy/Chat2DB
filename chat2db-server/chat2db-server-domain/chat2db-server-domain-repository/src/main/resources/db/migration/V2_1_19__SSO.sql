CREATE TABLE IF NOT EXISTS `user_identity_binding`
(
    `id`                   bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`           datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`         datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `user_id`              bigint(20) unsigned NOT NULL COMMENT '本地用户id',
    `provider_type`        varchar(32)         NOT NULL COMMENT '外部身份提供方类型',
    `issuer`               varchar(512)        NOT NULL COMMENT 'OIDC issuer',
    `subject_value`        varchar(512)        NOT NULL COMMENT 'OIDC subject',
    `username_claim_value` varchar(512)                 DEFAULT NULL COMMENT '最近一次同步的用户名claim',
    `email_claim_value`    varchar(512)                 DEFAULT NULL COMMENT '最近一次同步的邮箱claim',
    `status`               varchar(32)         NOT NULL DEFAULT 'ACTIVE' COMMENT '绑定状态',
    `last_login_at`        datetime                     DEFAULT NULL COMMENT '最近登录时间',
    `last_sync_at`         datetime                     DEFAULT NULL COMMENT '最近同步时间',
    `raw_profile_snapshot` text                         DEFAULT NULL COMMENT '最近一次外部资料快照',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='外部身份绑定表'
;

create UNIQUE INDEX uk_user_identity_binding_subject on user_identity_binding (`provider_type`, `issuer`, `subject_value`);
create INDEX idx_user_identity_binding_user_id on user_identity_binding (`user_id`);

CREATE TABLE IF NOT EXISTS `sso_group_team_mapping`
(
    `id`                  bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`          datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`        datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `provider_type`       varchar(32)         NOT NULL COMMENT '外部身份提供方类型',
    `issuer`              varchar(512)        NOT NULL COMMENT 'OIDC issuer',
    `external_group_code` varchar(512)        NOT NULL COMMENT '外部group编码',
    `team_id`             bigint(20) unsigned NOT NULL COMMENT '本地团队id',
    `sync_mode`           varchar(32)         NOT NULL DEFAULT 'MEMBERSHIP' COMMENT '同步模式',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='SSO group团队映射表'
;

create UNIQUE INDEX uk_sso_group_team_mapping on sso_group_team_mapping (`provider_type`, `issuer`, `external_group_code`, `team_id`);
create INDEX idx_sso_group_team_mapping_team_id on sso_group_team_mapping (`team_id`);

ALTER TABLE `team_user`
    ADD COLUMN `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '成员来源';

CREATE TABLE IF NOT EXISTS `team_user_binding_source`
(
    `id`                  bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create`          datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`        datetime            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `team_user_id`        bigint(20) unsigned NOT NULL COMMENT '团队成员关系id',
    `provider_type`       varchar(32)         NOT NULL COMMENT '外部身份提供方类型',
    `issuer`              varchar(512)        NOT NULL COMMENT 'OIDC issuer',
    `external_group_code` varchar(512)        NOT NULL COMMENT '外部group编码',
    `sync_source`         varchar(32)         NOT NULL DEFAULT 'SSO_GROUP' COMMENT '同步来源',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='SSO同步的团队成员来源表'
;

create UNIQUE INDEX uk_team_user_binding_source on team_user_binding_source (`team_user_id`, `provider_type`, `issuer`, `external_group_code`);
create INDEX idx_team_user_binding_source_team_user_id on team_user_binding_source (`team_user_id`);

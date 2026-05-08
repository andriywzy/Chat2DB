CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `category` varchar(32) NOT NULL COMMENT '审计类别',
    `action_type` varchar(32) NOT NULL COMMENT '操作类型',
    `resource_type` varchar(64) NOT NULL COMMENT '资源类型',
    `operator_user_id` bigint(20) unsigned DEFAULT NULL COMMENT '操作人ID',
    `operator_user_name` varchar(256) DEFAULT NULL COMMENT '操作人名称',
    `role_code` varchar(32) DEFAULT NULL COMMENT '角色编码',
    `target_id` varchar(128) DEFAULT NULL COMMENT '目标对象ID',
    `target_name` varchar(512) DEFAULT NULL COMMENT '目标对象名称',
    `request_path` varchar(512) DEFAULT NULL COMMENT '请求路径',
    `request_method` varchar(32) DEFAULT NULL COMMENT '请求方法',
    `request_id` varchar(128) DEFAULT NULL COMMENT '请求ID',
    `client_ip` varchar(128) DEFAULT NULL COMMENT '客户端IP',
    `user_agent` varchar(1024) DEFAULT NULL COMMENT '用户代理',
    `status` varchar(32) NOT NULL COMMENT '执行状态',
    `detail_summary` varchar(2048) DEFAULT NULL COMMENT '摘要',
    `detail_payload` clob DEFAULT NULL COMMENT '详情载荷',
    `error_message` varchar(2048) DEFAULT NULL COMMENT '错误信息',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='控制台审计日志';
create INDEX IF NOT EXISTS idx_audit_log_category_time on audit_log(`category`,`gmt_create`);
create INDEX IF NOT EXISTS idx_audit_log_operator_time on audit_log(`operator_user_id`,`gmt_create`);
create INDEX IF NOT EXISTS idx_audit_log_resource_target on audit_log(`resource_type`,`target_id`);

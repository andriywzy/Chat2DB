CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `gmt_create` datetime DEFAULT NULL COMMENT '创建时间',
    `gmt_modified` datetime DEFAULT NULL COMMENT '修改时间',
    `user_id` bigint(20) unsigned NOT NULL COMMENT '用户ID',
    `name` varchar(255) NOT NULL COMMENT '知识库名称',
    `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
    `file_type` varchar(32) DEFAULT NULL COMMENT '文件类型',
    `status` varchar(32) NOT NULL COMMENT '状态',
    `sentence_count` int(11) DEFAULT 0 COMMENT '切片数',
    `word_count` int(11) DEFAULT 0 COMMENT '字数',
    `vector_count` int(11) DEFAULT 0 COMMENT '向量数',
    `content_preview` text DEFAULT NULL COMMENT '内容预览',
    `error_message` text DEFAULT NULL COMMENT '失败原因',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档元数据';

create INDEX idx_knowledge_document_user_id on knowledge_document(user_id);
create INDEX idx_knowledge_document_status on knowledge_document(status);

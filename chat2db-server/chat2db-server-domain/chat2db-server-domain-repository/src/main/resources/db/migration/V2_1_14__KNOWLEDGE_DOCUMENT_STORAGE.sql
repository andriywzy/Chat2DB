ALTER TABLE `knowledge_document`
    ADD COLUMN IF NOT EXISTS `storage_path` varchar(1024) DEFAULT NULL COMMENT '本地文件存储路径';

import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Input, Popconfirm, Space, Table, Tag, Upload, message } from 'antd';
import { DeleteOutlined, EyeOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import i18n from '@/i18n';
import knowledgeService from '@/service/knowledge';
import { IKnowledgeDocument, IKnowledgeDocumentPageParams, KnowledgeDocumentStatus } from '@/typings';
import styles from './index.less';

const statusColorMap: Record<KnowledgeDocumentStatus, string> = {
  PROCESSING: 'processing',
  READY: 'success',
  FAILED: 'error',
};

export default function KnowledgeBaseSetting() {
  const [documents, setDocuments] = useState<IKnowledgeDocument[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<IKnowledgeDocument | null>(null);
  const [searchKey, setSearchKey] = useState('');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [knowledgeName, setKnowledgeName] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
  });

  const columns = useMemo(
    () => [
      {
        title: i18n('setting.knowledge.table.name'),
        dataIndex: 'name',
        key: 'name',
      },
      {
        title: i18n('setting.knowledge.table.fileName'),
        dataIndex: 'fileName',
        key: 'fileName',
      },
      {
        title: i18n('setting.knowledge.table.status'),
        dataIndex: 'status',
        key: 'status',
        width: 140,
        render: (status: KnowledgeDocumentStatus) => (
          <Tag className={styles.statusTag} color={statusColorMap[status]}>
            {i18n(`setting.knowledge.status.${status}`)}
          </Tag>
        ),
      },
      {
        title: i18n('setting.knowledge.table.sentences'),
        dataIndex: 'sentenceCount',
        key: 'sentenceCount',
        width: 110,
      },
      {
        title: i18n('setting.knowledge.table.vectors'),
        dataIndex: 'vectorCount',
        key: 'vectorCount',
        width: 110,
      },
      {
        title: i18n('setting.knowledge.table.updatedAt'),
        dataIndex: 'gmtModified',
        key: 'gmtModified',
        width: 180,
      },
      {
        title: i18n('common.text.action'),
        key: 'action',
        width: 160,
        render: (_: unknown, record: IKnowledgeDocument) => (
          <Space size={4}>
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetail(record.id)}
            >
              {i18n('setting.knowledge.action.view')}
            </Button>
            <Popconfirm
              title={i18n('setting.knowledge.delete.confirm')}
              description={i18n('setting.knowledge.delete.description')}
              onConfirm={() => handleDelete(record.id)}
              okText={i18n('common.button.affirm')}
              cancelText={i18n('common.button.cancel')}
            >
              <Button danger type="link" icon={<DeleteOutlined />}>
                {i18n('setting.knowledge.action.remove')}
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [],
  );

  const queryList = async (
    nextSearchKey = searchKey,
    nextPageNo = pagination.current,
    nextPageSize = pagination.pageSize,
  ) => {
    setIsLoading(true);
    try {
      const params: IKnowledgeDocumentPageParams = {
        pageNo: nextPageNo,
        pageSize: nextPageSize,
        searchKey: nextSearchKey,
      };
      const result = await knowledgeService.getKnowledgeDocumentList(params);
      setDocuments(result?.data || []);
      setPagination((prev) => ({
        ...prev,
        total: result?.total || 0,
      }));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    queryList(searchKey, pagination.current, pagination.pageSize);
  }, [pagination.current, pagination.pageSize]);

  const handleSearch = (value: string) => {
    setSearchKey(value);
    setPagination((prev) => ({
      ...prev,
      current: 1,
    }));
    queryList(value, 1, pagination.pageSize);
  };

  const handleUpload = async () => {
    const currentFile = fileList[0]?.originFileObj as File | undefined;
    if (!currentFile) {
      message.warning(i18n('setting.knowledge.upload.selectFile'));
      return;
    }

    setIsUploading(true);
    try {
      await knowledgeService.uploadKnowledgeDocument({
        name: knowledgeName,
        file: currentFile,
      });
      message.success(i18n('setting.knowledge.upload.success'));
      setFileList([]);
      setKnowledgeName('');
      queryList();
    } catch (error: any) {
      message.error(error?.message || i18n('setting.knowledge.upload.failed'));
      queryList();
    } finally {
      setIsUploading(false);
    }
  };

  const handleDelete = async (id: number) => {
    await knowledgeService.deleteKnowledgeDocument({ id });
    message.success(i18n('common.text.successfullyDelete'));
    if (selectedDocument?.id === id) {
      setSelectedDocument(null);
    }
    queryList();
  };

  const handleViewDetail = async (id: number) => {
    const detail = await knowledgeService.getKnowledgeDocumentDetail({ id });
    setSelectedDocument(detail);
  };

  return (
    <div className={styles.page}>
      <Alert
        className={styles.tips}
        type="info"
        showIcon
        message={i18n('setting.knowledge.tip')}
        description={i18n('setting.knowledge.tip.description')}
      />

      <div className={styles.toolbar}>
        <Input.Search
          allowClear
          placeholder={i18n('setting.knowledge.search.placeholder')}
          onSearch={handleSearch}
          style={{ width: 280 }}
        />
        <div className={styles.uploadBox}>
          <Input
            placeholder={i18n('setting.knowledge.upload.namePlaceholder')}
            value={knowledgeName}
            onChange={(event) => setKnowledgeName(event.target.value)}
            style={{ width: 220 }}
          />
          <Upload
            accept=".pdf"
            beforeUpload={(file) => {
              setFileList([{ uid: file.uid, name: file.name, status: 'done', originFileObj: file }]);
              return false;
            }}
            fileList={fileList}
            maxCount={1}
            onRemove={() => {
              setFileList([]);
              return true;
            }}
          >
            <Button icon={<UploadOutlined />}>{i18n('setting.knowledge.upload.select')}</Button>
          </Upload>
          <Button type="primary" loading={isUploading} onClick={handleUpload}>
            {i18n('setting.knowledge.upload.submit')}
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => queryList()}>
            {i18n('common.button.refresh')}
          </Button>
        </div>
      </div>

      <Table
        rowKey="id"
        columns={columns as any}
        dataSource={documents}
        loading={isLoading}
        pagination={pagination}
        onChange={(nextPagination) => {
          setPagination((prev) => ({
            ...prev,
            current: nextPagination.current || 1,
            pageSize: nextPagination.pageSize || prev.pageSize,
          }));
        }}
      />

      <Drawer
        width={720}
        title={selectedDocument?.name || i18n('setting.knowledge.detail.title')}
        open={Boolean(selectedDocument)}
        onClose={() => setSelectedDocument(null)}
      >
        {selectedDocument && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label={i18n('setting.knowledge.table.fileName')}>
                {selectedDocument.fileName}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('setting.knowledge.table.status')}>
                <Tag color={statusColorMap[selectedDocument.status]}>
                  {i18n(`setting.knowledge.status.${selectedDocument.status}`)}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={i18n('setting.knowledge.table.sentences')}>
                {selectedDocument.sentenceCount}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('setting.knowledge.table.words')}>
                {selectedDocument.wordCount}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('setting.knowledge.table.vectors')}>
                {selectedDocument.vectorCount}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('setting.knowledge.table.updatedAt')}>
                {selectedDocument.gmtModified}
              </Descriptions.Item>
              {selectedDocument.errorMessage ? (
                <Descriptions.Item label={i18n('setting.knowledge.detail.error')}>
                  {selectedDocument.errorMessage}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
            <div className={styles.preview}>{selectedDocument.contentPreview || '-'}</div>
          </Space>
        )}
      </Drawer>
    </div>
  );
}

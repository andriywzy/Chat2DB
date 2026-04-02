import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Upload,
  message,
} from 'antd';
import { DeleteOutlined, EyeOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import i18n from '@/i18n';
import knowledgeService, { IKnowledgeServiceError } from '@/service/knowledge';
import {
  IKnowledgeDocument,
  IKnowledgeDocumentPageParams,
  IKnowledgeSearchSource,
  KnowledgeDocumentStatus,
} from '@/typings';
import connectToEventSource from '@/utils/eventSource';
import { isAiStreamDone, parseAiStreamMessage } from '@/utils/aiStream';
import { v4 as uuidv4 } from 'uuid';
import styles from './index.less';

const statusColorMap: Record<KnowledgeDocumentStatus, string> = {
  PROCESSING: 'processing',
  READY: 'success',
  FAILED: 'error',
};

const knowledgeUploadErrorKeyMap: Record<string, string> = {
  'knowledge.document.file.empty': 'setting.knowledge.upload.error.fileEmpty',
  'knowledge.document.file.unsupported': 'setting.knowledge.upload.error.unsupported',
  'knowledge.document.text.empty': 'setting.knowledge.upload.error.textEmpty',
  'knowledge.document.embedding.unsupported': 'setting.knowledge.upload.error.embeddingUnsupported',
  'knowledge.document.embedding.empty': 'setting.knowledge.upload.error.embeddingEmpty',
  'knowledge.search.document.not.ready': 'setting.knowledge.ask.error.documentNotReady',
};

type SearchScope = 'all' | 'selected';

interface IProps {
  className?: string;
}

export default function KnowledgeBaseSetting(props: IProps) {
  const { className } = props;
  const pageClassName = [styles.page, className].filter(Boolean).join(' ');
  const [documents, setDocuments] = useState<IKnowledgeDocument[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<IKnowledgeDocument | null>(null);
  const [searchKey, setSearchKey] = useState('');
  const [testQuestion, setTestQuestion] = useState('');
  const [testAnswer, setTestAnswer] = useState('');
  const [searchSources, setSearchSources] = useState<IKnowledgeSearchSource[]>([]);
  const [searchScope, setSearchScope] = useState<SearchScope>('all');
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<number[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [readyQuestionDocuments, setReadyQuestionDocuments] = useState<IKnowledgeDocument[]>([]);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [knowledgeName, setKnowledgeName] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const searchUid = useMemo(() => uuidv4(), []);
  const closeSearchEventSourceRef = useRef<null | (() => void)>(null);
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
        title: i18n('setting.knowledge.table.fileType'),
        dataIndex: 'fileType',
        key: 'fileType',
        width: 110,
        render: (fileType: string) => String(fileType || '-').toUpperCase(),
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
        width: 220,
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
              title={i18n('setting.knowledge.rebuild.confirm')}
              description={i18n('setting.knowledge.rebuild.description')}
              onConfirm={() => handleRebuild(record.id)}
              okText={i18n('common.button.affirm')}
              cancelText={i18n('common.button.cancel')}
              disabled={record.status === 'PROCESSING'}
            >
              <Button
                type="link"
                icon={<ReloadOutlined />}
                disabled={record.status === 'PROCESSING'}
              >
                {i18n('setting.knowledge.action.rebuild')}
              </Button>
            </Popconfirm>
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

  const readyDocuments = useMemo(() => readyQuestionDocuments, [readyQuestionDocuments]);

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

  const queryReadyDocuments = async () => {
    const result = await knowledgeService.getKnowledgeDocumentList({
      pageNo: 1,
      pageSize: 1000,
      searchKey: '',
      status: 'READY',
    });
    setReadyQuestionDocuments(result?.data || []);
  };

  useEffect(() => {
    queryList(searchKey, pagination.current, pagination.pageSize);
  }, [pagination.current, pagination.pageSize]);

  useEffect(() => {
    queryReadyDocuments();
  }, []);

  useEffect(() => {
    return () => {
      closeSearchEventSourceRef.current?.();
    };
  }, []);

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
      queryReadyDocuments();
    } catch (error: any) {
      message.error(getKnowledgeUploadErrorMessage(error));
      queryList();
    } finally {
      setIsUploading(false);
    }
  };

  const getKnowledgeUploadErrorMessage = (error: IKnowledgeServiceError | Error | string) => {
    if (typeof error === 'string') {
      return error;
    }
    const errorCode = (error as IKnowledgeServiceError)?.code;
    const localeKey = errorCode ? knowledgeUploadErrorKeyMap[errorCode] : undefined;
    if (localeKey) {
      return i18n(localeKey);
    }
    return error?.message || i18n('setting.knowledge.upload.failed');
  };

  const handleDelete = async (id: number) => {
    await knowledgeService.deleteKnowledgeDocument({ id });
    message.success(i18n('common.text.successfullyDelete'));
    if (selectedDocument?.id === id) {
      setSelectedDocument(null);
    }
    setSelectedDocumentIds((prev) => prev.filter((currentId) => currentId !== id));
    queryList();
    queryReadyDocuments();
  };

  const handleRebuild = async (id: number) => {
    await knowledgeService.rebuildKnowledgeDocument({ id });
    message.success(i18n('setting.knowledge.rebuild.success'));
    if (selectedDocument?.id === id) {
      const detail = await knowledgeService.getKnowledgeDocumentDetail({ id });
      setSelectedDocument(detail);
    }
    queryList();
    queryReadyDocuments();
  };

  const handleViewDetail = async (id: number) => {
    const detail = await knowledgeService.getKnowledgeDocumentDetail({ id });
    setSelectedDocument(detail);
  };

  const handleCancelSearch = () => {
    closeSearchEventSourceRef.current?.();
    closeSearchEventSourceRef.current = null;
    setIsSearching(false);
  };

  const handleKnowledgeSearch = async () => {
    if (!testQuestion.trim()) {
      message.warning(i18n('setting.knowledge.ask.questionRequired'));
      return;
    }

    if (searchScope === 'selected' && !selectedDocumentIds.length) {
      message.warning(i18n('setting.knowledge.ask.documentRequired'));
      return;
    }

    handleCancelSearch();
    setTestAnswer('');
    setSearchSources([]);
    setIsSearching(true);

    const trimmedQuestion = testQuestion.trim();
    const activeDocumentIds = searchScope === 'selected' ? selectedDocumentIds : [];

    try {
      const context = await knowledgeService.searchKnowledgeContext({
        message: trimmedQuestion,
        documentIds: activeDocumentIds,
      });
      setSearchSources(context?.knowledgeList || []);
    } catch (error: any) {
      handleCancelSearch();
      message.error(getKnowledgeUploadErrorMessage(error) || i18n('setting.knowledge.ask.failed'));
      return;
    }

    const params = new URLSearchParams();
    params.append('uid', searchUid);
    params.append('message', trimmedQuestion);
    activeDocumentIds.forEach((documentId) => {
      params.append('documentIds', String(documentId));
    });

    closeSearchEventSourceRef.current = connectToEventSource({
      url: `/api/ai/knowledge/search?${params.toString()}`,
      onOpen: () => {
        setIsSearching(true);
      },
      onMessage: (_message: string) => {
        if (isAiStreamDone(_message)) {
          handleCancelSearch();
          return;
        }

        try {
          const nextContent = parseAiStreamMessage(_message).content || '';
          if (nextContent) {
            setTestAnswer((prev) => `${prev}${nextContent}`);
          }
        } catch (error: any) {
          handleCancelSearch();
          const fallbackMessage = typeof _message === 'string' && _message ? _message : error?.message;
          message.error(fallbackMessage || i18n('setting.knowledge.ask.failed'));
        }
      },
      onError: (error: any) => {
        handleCancelSearch();
        message.error(error?.message || i18n('setting.knowledge.ask.failed'));
      },
    });
  };

  return (
    <div className={pageClassName}>
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
            accept=".pdf,.md,.markdown,.txt,.doc,.docx,.xls,.xlsx,.csv"
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
          <span className={styles.supportedFormats}>{i18n('setting.knowledge.upload.supportedFormats')}</span>
        </div>
      </div>

      <div className={styles.searchPanel}>
        <div className={styles.searchHeader}>
          <div className={styles.searchTitle}>{i18n('setting.knowledge.ask.title')}</div>
          <div className={styles.searchDescription}>{i18n('setting.knowledge.ask.description')}</div>
        </div>
        <div className={styles.scopeRow}>
          <div className={styles.scopeLabel}>{i18n('setting.knowledge.ask.scope.label')}</div>
          <div className={styles.scopeControls}>
            <Select<SearchScope>
              value={searchScope}
              onChange={(value) => {
                setSearchScope(value);
                if (value === 'all') {
                  setSelectedDocumentIds([]);
                }
              }}
              style={{ width: 220 }}
              options={[
                { value: 'all', label: i18n('setting.knowledge.ask.scope.all') },
                { value: 'selected', label: i18n('setting.knowledge.ask.scope.selected') },
              ]}
            />
            <Select
              mode="multiple"
              allowClear
              disabled={searchScope !== 'selected'}
              value={selectedDocumentIds}
              onChange={(value) => setSelectedDocumentIds(value)}
              style={{ minWidth: 320, flex: 1 }}
              placeholder={i18n('setting.knowledge.ask.scope.placeholder')}
              options={readyDocuments.map((document) => ({
                value: document.id,
                label: `${document.name} (${String(document.fileType || '-').toUpperCase()})`,
              }))}
            />
          </div>
        </div>
        <Input.TextArea
          value={testQuestion}
          rows={3}
          placeholder={i18n('setting.knowledge.ask.placeholder')}
          onChange={(event) => setTestQuestion(event.target.value)}
        />
        <div className={styles.searchActions}>
          <Space>
            <Button type="primary" loading={isSearching} onClick={handleKnowledgeSearch}>
              {i18n('setting.knowledge.ask.submit')}
            </Button>
            <Button disabled={!isSearching} onClick={handleCancelSearch}>
              {i18n('setting.knowledge.ask.cancel')}
            </Button>
          </Space>
        </div>
        <div className={styles.answerBox}>
          {testAnswer || (
            <span className={styles.answerPlaceholder}>{i18n('setting.knowledge.ask.empty')}</span>
          )}
        </div>
        <div className={styles.sourcesSection}>
          <div className={styles.sourcesTitle}>{i18n('setting.knowledge.ask.sourcesTitle')}</div>
          {searchSources.length ? (
            <div className={styles.sourceList}>
              {searchSources.map((source, index) => (
                <div
                  className={styles.sourceCard}
                  key={`${source.documentId || 'unknown'}-${source.id || index}-${index}`}
                >
                  <div className={styles.sourceHeader}>
                    <div className={styles.sourceName}>
                      {source.documentName || i18n('setting.knowledge.ask.source.unknown')}
                    </div>
                    <Space size={8}>
                      {source.fileType ? (
                        <Tag>{String(source.fileType).toUpperCase()}</Tag>
                      ) : null}
                      {typeof source.score === 'number' ? (
                        <span className={styles.sourceScore}>
                          {i18n('setting.knowledge.ask.source.score', source.score.toFixed(2))}
                        </span>
                      ) : null}
                    </Space>
                  </div>
                  <div className={styles.sourceSnippet}>{source.content}</div>
                </div>
              ))}
            </div>
          ) : (
            <div className={styles.sourcesEmpty}>{i18n('setting.knowledge.ask.sourcesEmpty')}</div>
          )}
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
              <Descriptions.Item label={i18n('setting.knowledge.table.fileType')}>
                {String(selectedDocument.fileType || '-').toUpperCase()}
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

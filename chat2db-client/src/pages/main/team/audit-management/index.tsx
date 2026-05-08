import React, { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Input, Select, Space, Table, Tag } from 'antd';
import { getAuditDetail, getAuditList } from '@/service/team';
import { AuditCategory, AuditResourceType, IAuditRecord } from '@/typings/team';
import i18n from '@/i18n';
import { formatDate } from '@/utils/date';

function AuditManagement() {
  const [records, setRecords] = useState<IAuditRecord[]>([]);
  const [detail, setDetail] = useState<IAuditRecord>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [query, setQuery] = useState({
    category: AuditCategory.CONSOLE,
    resourceType: undefined as AuditResourceType | undefined,
    status: undefined as string | undefined,
    searchKey: '',
    current: 1,
    pageSize: 10,
    total: 0,
  });

  useEffect(() => {
    fetchList();
  }, [query.current, query.pageSize, query.category, query.resourceType, query.status, query.searchKey]);

  const columns = useMemo(
    () => [
      {
        title: i18n('team.audit.category'),
        dataIndex: 'category',
        key: 'category',
      },
      {
        title: i18n('team.audit.actionType'),
        dataIndex: 'actionType',
        key: 'actionType',
      },
      {
        title: i18n('team.audit.resourceType'),
        dataIndex: 'resourceType',
        key: 'resourceType',
      },
      {
        title: i18n('team.audit.operator'),
        dataIndex: 'operatorUserName',
        key: 'operatorUserName',
      },
      {
        title: i18n('team.audit.target'),
        dataIndex: 'targetName',
        key: 'targetName',
        render: (_: string, record: IAuditRecord) => record.targetName || record.targetId || '-',
      },
      {
        title: i18n('team.audit.status'),
        dataIndex: 'status',
        key: 'status',
        render: (value: string) => <Tag color={value === 'SUCCESS' ? 'green' : 'red'}>{value}</Tag>,
      },
      {
        title: i18n('team.audit.occurredAt'),
        dataIndex: 'occurredAt',
        key: 'occurredAt',
        render: (value: string) => formatDate(value, 'yyyy-MM-dd hh:mm:ss'),
      },
      {
        title: i18n('team.audit.summary'),
        dataIndex: 'detailSummary',
        key: 'detailSummary',
        ellipsis: true,
      },
      {
        title: i18n('common.text.action'),
        key: 'action',
        render: (_: unknown, record: IAuditRecord) => (
          <Button type="link" onClick={() => openDetail(record.id)}>
            {i18n('team.audit.detail')}
          </Button>
        ),
      },
    ],
    [],
  );

  async function fetchList() {
    const res = await getAuditList({
      pageNo: query.current,
      pageSize: query.pageSize,
      category: query.category,
      resourceType: query.resourceType,
      status: query.status,
      searchKey: query.searchKey,
    });
    if (res) {
      setRecords(res.data || []);
      setQuery((prev) => ({ ...prev, total: res.total || 0 }));
    }
  }

  async function openDetail(id: string) {
    const res = await getAuditDetail({ id });
    if (res) {
      setDetail(res);
      setDetailOpen(true);
    }
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select
          value={query.category}
          style={{ width: 220 }}
          onChange={(value) => setQuery((prev) => ({ ...prev, current: 1, category: value }))}
          options={[
            { label: i18n('team.audit.category.console'), value: AuditCategory.CONSOLE },
            { label: i18n('team.audit.category.database'), value: AuditCategory.DATABASE },
          ]}
        />
        <Select
          allowClear
          placeholder={i18n('team.audit.resourceType')}
          style={{ width: 180 }}
          value={query.resourceType}
          onChange={(value) => setQuery((prev) => ({ ...prev, current: 1, resourceType: value }))}
          options={Object.values(AuditResourceType).map((value) => ({ label: value, value }))}
        />
        <Select
          allowClear
          placeholder={i18n('team.audit.status')}
          style={{ width: 140 }}
          value={query.status}
          onChange={(value) => setQuery((prev) => ({ ...prev, current: 1, status: value }))}
          options={[
            { label: 'SUCCESS', value: 'SUCCESS' },
            { label: 'FAILED', value: 'FAILED' },
          ]}
        />
        <Input.Search
          allowClear
          style={{ width: 320 }}
          placeholder={i18n('team.input.search.placeholder')}
          onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, searchKey: value }))}
        />
      </Space>
      <Table
        rowKey="id"
        dataSource={records}
        columns={columns}
        pagination={{
          current: query.current,
          pageSize: query.pageSize,
          total: query.total,
          showSizeChanger: true,
          showQuickJumper: true,
        }}
        onChange={(pagination) =>
          setQuery((prev) => ({
            ...prev,
            current: pagination.current || 1,
            pageSize: pagination.pageSize || 10,
          }))
        }
      />
      <Drawer open={detailOpen} title={i18n('team.audit.detailTitle')} width={720} onClose={() => setDetailOpen(false)}>
        <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{detail?.detailPayload || '-'}</pre>
      </Drawer>
    </div>
  );
}

export default AuditManagement;

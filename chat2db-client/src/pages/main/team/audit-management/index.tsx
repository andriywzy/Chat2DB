import React, { useEffect, useMemo, useState } from 'react';
import { DatePicker, Drawer, Input, Select, Space, Table, Tag } from 'antd';
import { getAuditDetail, getAuditList } from '@/service/team';
import { AuditCategory, AuditResourceType, IAuditRecord } from '@/typings/team';
import i18n from '@/i18n';
import { formatDate } from '@/utils/date';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const TIME_COLUMN_WIDTH = 180;
const STATUS_COLUMN_WIDTH = 100;
const MIN_TEXT_COLUMN_WIDTH = 96;
const MAX_TEXT_COLUMN_WIDTH = 320;
const TEXT_MEASURE_FONT =
  '14px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
let textMeasureCanvas: HTMLCanvasElement | null = null;

function getTextMeasureContext() {
  if (typeof document === 'undefined') {
    return null;
  }
  if (!textMeasureCanvas) {
    textMeasureCanvas = document.createElement('canvas');
  }
  const context = textMeasureCanvas.getContext('2d');
  if (!context) {
    return null;
  }
  context.font = TEXT_MEASURE_FONT;
  return context;
}

function calcAutoTextWidth(values: Array<string | undefined>) {
  const context = getTextMeasureContext();
  if (!context) {
    return MIN_TEXT_COLUMN_WIDTH;
  }
  const widestText = values.reduce((max, value) => {
    const currentWidth = context.measureText(value || '-').width;
    return Math.max(max, currentWidth);
  }, 0);
  return Math.min(Math.max(Math.ceil(widestText) + 32, MIN_TEXT_COLUMN_WIDTH), MAX_TEXT_COLUMN_WIDTH);
}

function AuditManagement() {
  const defaultStartTime = dayjs()
    .subtract(180, 'day')
    .startOf('day');
  const defaultEndTime = dayjs().endOf('day');
  const [records, setRecords] = useState<IAuditRecord[]>([]);
  const [detail, setDetail] = useState<IAuditRecord>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [query, setQuery] = useState({
    category: AuditCategory.CONSOLE,
    resourceType: undefined as AuditResourceType | undefined,
    status: undefined as string | undefined,
    searchKey: '',
    startTime: defaultStartTime.valueOf(),
    endTime: defaultEndTime.valueOf(),
    current: 1,
    pageSize: 30,
    total: 0,
  });

  useEffect(() => {
    fetchList();
  }, [
    query.current,
    query.pageSize,
    query.category,
    query.resourceType,
    query.status,
    query.searchKey,
    query.startTime,
    query.endTime,
  ]);

  const operatorColumnWidth = useMemo(
    () => calcAutoTextWidth(records.map((record) => record.operatorUserName)),
    [records],
  );
  const targetColumnWidth = useMemo(
    () => calcAutoTextWidth(records.map((record) => record.targetName || record.targetId)),
    [records],
  );

  const columns = useMemo(
    () => [
      {
        title: i18n('team.audit.occurredAt'),
        dataIndex: 'occurredAt',
        key: 'occurredAt',
        width: TIME_COLUMN_WIDTH,
        render: (value: string) => (
          <span style={{ whiteSpace: 'nowrap' }}>{formatDate(value, 'yyyy-MM-dd hh:mm:ss')}</span>
        ),
      },
      {
        title: i18n('team.audit.operator'),
        dataIndex: 'operatorUserName',
        key: 'operatorUserName',
        width: operatorColumnWidth,
        render: (value?: string) => <span style={{ whiteSpace: 'nowrap' }}>{value || '-'}</span>,
      },
      {
        title: i18n('team.audit.target'),
        dataIndex: 'targetName',
        key: 'targetName',
        width: targetColumnWidth,
        render: (_: string, record: IAuditRecord) => (
          <span style={{ whiteSpace: 'nowrap' }}>{record.targetName || record.targetId || '-'}</span>
        ),
      },
      {
        title: i18n('team.audit.summary'),
        dataIndex: 'detailSummary',
        key: 'detailSummary',
        render: (value?: string) => (
          <div style={{ whiteSpace: 'normal', wordBreak: 'break-word', lineHeight: 1.5 }}>
            {value || '-'}
          </div>
        ),
      },
      {
        title: i18n('team.audit.status'),
        dataIndex: 'status',
        key: 'status',
        width: STATUS_COLUMN_WIDTH,
        render: (value: string) => (
          <span style={{ whiteSpace: 'nowrap' }}>
            <Tag color={value === 'SUCCESS' ? 'green' : 'red'}>{value}</Tag>
          </span>
        ),
      },
    ],
    [operatorColumnWidth, targetColumnWidth],
  );

  async function fetchList() {
    const res = await getAuditList({
      pageNo: query.current,
      pageSize: query.pageSize,
      category: query.category,
      resourceType: query.resourceType,
      status: query.status,
      searchKey: query.searchKey,
      startTime: query.startTime,
      endTime: query.endTime,
    });
    if (res) {
      const nextRecords = (res.data || []).filter(
        (record) => !(query.category === AuditCategory.DATABASE && (record.detailSummary || '').trim() === '-'),
      );
      setRecords(nextRecords);
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
        <RangePicker
          value={[dayjs(query.startTime), dayjs(query.endTime)]}
          showTime
          onChange={(value) =>
            setQuery((prev) => ({
              ...prev,
              current: 1,
              startTime: value?.[0]?.valueOf() || defaultStartTime.valueOf(),
              endTime: value?.[1]?.valueOf() || defaultEndTime.valueOf(),
            }))
          }
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
        scroll={{ x: 'max-content', y: 'calc(100vh - 360px)' }}
        pagination={{
          current: query.current,
          pageSize: query.pageSize,
          total: query.total,
          showQuickJumper: true,
        }}
        onRow={(record) => ({
          onClick: () => openDetail(record.id),
          style: { cursor: 'pointer' },
        })}
        onChange={(pagination) =>
          setQuery((prev) => ({
            ...prev,
            current: pagination.current || 1,
            pageSize: Math.min(pagination.pageSize || 30, 30),
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

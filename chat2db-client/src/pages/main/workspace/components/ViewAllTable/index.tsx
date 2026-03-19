import React, { memo, useEffect, useMemo, useRef, useState } from 'react';
import i18n from '@/i18n';
import styles from './index.less';
import classnames from 'classnames';
import {
  Button,
  Dropdown,
  Form,
  Input,
  InputNumber,
  message,
  Pagination,
  Select,
  Space,
  Table,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DatabaseTypeCode, OperationColumn, TreeNodeType, WorkspaceTabType } from '@/constants';
import { v4 as uuid } from 'uuid';

import sqlServer, {
  IRedisFieldValue,
  IRedisKeyDetail,
  IRedisKeyItem,
  IRedisZSetValue,
} from '@/service/sql';
import { IPageParams } from '@/typings';

import Iconfont from '@/components/Iconfont';
import { addWorkspaceTab } from '@/pages/main/workspace/store/console';
import { setCurrentWorkspaceGlobalExtend } from '@/pages/main/workspace/store/common';
import { getRightClickMenu } from '@/blocks/Tree/hooks/useGetRightClickMenu';
import MenuLabel from '@/components/MenuLabel';

const { Search, TextArea } = Input;

const REDIS_PAGE_SIZE = 100;
const REDIS_TYPES = ['string', 'hash', 'list', 'set', 'zset', 'stream'];

interface IProps {
  className?: string;
  uniqueData: {
    dataSourceId: string;
    dataSourceName: string;
    databaseType: DatabaseTypeCode;
    databaseName?: string;
    schemaName?: string;
  };
}

interface IHashRow extends IRedisFieldValue {
  id: string;
}

interface ISimpleRow {
  id: string;
  value: string;
}

interface IZSetRow extends IRedisZSetValue {
  id: string;
}

interface IRedisEditorForm {
  originalKeyName?: string;
  keyName: string;
  keyType: string;
  ttlSeconds?: number;
}

const buildRedisTtlText = (ttlSeconds?: number | null) => {
  if (ttlSeconds === null || ttlSeconds === undefined) {
    return '-';
  }
  if (ttlSeconds === -1) {
    return i18n('workspace.redis.noExpire');
  }
  if (ttlSeconds === -2) {
    return i18n('workspace.redis.expired');
  }
  return `${ttlSeconds}s`;
};

const mapListToRows = (values?: string[]) => (values || []).map((value) => ({ id: uuid(), value: value || '' }));

const mapHashToRows = (values?: IRedisFieldValue[]) =>
  (values || []).map((item) => ({ id: uuid(), field: item.field || '', value: item.value || '' }));

const mapZsetToRows = (values?: IRedisZSetValue[]) =>
  (values || []).map((item) => ({ id: uuid(), member: item.member || '', score: item.score || '' }));

const RedisBrowserView = memo(({ uniqueData, className }: Omit<IProps, 'className'> & { className?: string }) => {
  const [tableLoading, setTableLoading] = useState(false);
  const [tableData, setTableData] = useState<IRedisKeyItem[]>([]);
  const [tableTotal, setTableTotal] = useState(0);
  const [currentPageNo, setCurrentPageNo] = useState(1);
  const [searchKey, setSearchKey] = useState('');
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [activeKey, setActiveKey] = useState<string>('');

  const [detailLoading, setDetailLoading] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);

  const [stringValue, setStringValue] = useState('');
  const [hashRows, setHashRows] = useState<IHashRow[]>([]);
  const [listRows, setListRows] = useState<ISimpleRow[]>([]);
  const [setRows, setSetRows] = useState<ISimpleRow[]>([]);
  const [zsetRows, setZsetRows] = useState<IZSetRow[]>([]);
  const [streamJson, setStreamJson] = useState('[]');

  const [editorForm] = Form.useForm<IRedisEditorForm>();

  const [allTableHeight, setAllTableHeight] = useState(0);
  const tableBoxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchTable({ pageNo: 1, pageSize: REDIS_PAGE_SIZE, refresh: true });
  }, []);

  useEffect(() => {
    if (!tableBoxRef.current) {
      return;
    }
    const resizeObserver = new ResizeObserver((entries) => {
      const { height } = entries[0].contentRect;
      setAllTableHeight(height);
    });
    resizeObserver.observe(tableBoxRef.current);
    return () => resizeObserver.disconnect();
  }, []);

  const fetchTable = (params: IPageParams & { refresh?: boolean; searchKey?: string }) => {
    setTableLoading(true);
    setCurrentPageNo(params.pageNo);
    sqlServer
      .getRedisKeyPage({
        dataSourceId: Number(uniqueData.dataSourceId),
        databaseName: uniqueData.databaseName || '0',
        schemaName: uniqueData.schemaName,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        refresh: params.refresh,
        searchKey: params.searchKey,
      })
      .then((res) => {
        const data = res.data || [];
        setTableData(data);
        setTableTotal(res.total || 0);

        if (activeKey && !data.find((item) => item.keyName === activeKey)) {
          setActiveKey('');
        }
      })
      .finally(() => setTableLoading(false));
  };

  const resetEditor = () => {
    editorForm.setFieldsValue({
      originalKeyName: '',
      keyName: '',
      keyType: 'string',
      ttlSeconds: -1,
    });
    setStringValue('');
    setHashRows([]);
    setListRows([]);
    setSetRows([]);
    setZsetRows([]);
    setStreamJson('[]');
  };

  const loadDetail = async (keyName: string) => {
    setDetailLoading(true);
    try {
      const detail: IRedisKeyDetail = await sqlServer.getRedisKeyDetail({
        dataSourceId: Number(uniqueData.dataSourceId),
        databaseName: uniqueData.databaseName || '0',
        schemaName: uniqueData.schemaName,
        keyName,
      });

      editorForm.setFieldsValue({
        originalKeyName: detail.keyName,
        keyName: detail.keyName,
        keyType: detail.keyType,
        ttlSeconds: detail.ttlSeconds === null || detail.ttlSeconds === undefined ? -1 : detail.ttlSeconds,
      });

      setStringValue(detail.stringValue || '');
      setHashRows(mapHashToRows(detail.hashValues));
      setListRows(mapListToRows(detail.listValues));
      setSetRows(mapListToRows(detail.setValues));
      setZsetRows(mapZsetToRows(detail.zsetValues));
      setStreamJson(JSON.stringify(detail.streamValues || [], null, 2));

      setActiveKey(detail.keyName);
    } finally {
      setDetailLoading(false);
    }
  };

  const onCreateKey = () => {
    setActiveKey('');
    setSelectedRowKeys([]);
    resetEditor();
  };

  const onDeleteKeys = async () => {
    const currentKey = editorForm.getFieldValue('originalKeyName');
    const deletingKeys = (selectedRowKeys.length ? selectedRowKeys : currentKey ? [currentKey] : []) as string[];

    if (!deletingKeys.length) {
      message.warning(i18n('workspace.redis.deleteSelectFirst'));
      return;
    }

    await sqlServer.deleteRedisKeys({
      dataSourceId: Number(uniqueData.dataSourceId),
      databaseName: uniqueData.databaseName || '0',
      schemaName: uniqueData.schemaName,
      keyNames: deletingKeys,
    });
    message.success(i18n('common.text.successfullyDelete'));
    setSelectedRowKeys([]);
    if (deletingKeys.includes(activeKey)) {
      setActiveKey('');
      resetEditor();
    }
    fetchTable({ pageNo: 1, pageSize: REDIS_PAGE_SIZE, refresh: true, searchKey });
  };

  const onSave = async () => {
    const formValues = await editorForm.validateFields();

    let parsedStreamValues: any[] | undefined;
    if (formValues.keyType === 'stream') {
      try {
        parsedStreamValues = streamJson.trim() ? JSON.parse(streamJson) : [];
      } catch (error) {
        message.error('Stream JSON format is invalid');
        return;
      }
    }

    setSaveLoading(true);
    try {
      await sqlServer.saveRedisKey({
        dataSourceId: Number(uniqueData.dataSourceId),
        databaseName: uniqueData.databaseName || '0',
        schemaName: uniqueData.schemaName,
        originalKeyName: formValues.originalKeyName,
        keyName: formValues.keyName?.trim(),
        keyType: formValues.keyType,
        ttlSeconds: formValues.ttlSeconds,
        stringValue,
        hashValues: hashRows
          .filter((item) => item.field.trim().length > 0)
          .map((item) => ({ field: item.field, value: item.value })),
        listValues: listRows.map((item) => item.value),
        setValues: setRows.map((item) => item.value),
        zsetValues: zsetRows
          .filter((item) => item.member.trim().length > 0)
          .map((item) => ({ member: item.member, score: item.score })),
        streamValues: parsedStreamValues,
      });

      message.success(i18n('common.tips.saveSuccessfully'));

      fetchTable({ pageNo: currentPageNo, pageSize: REDIS_PAGE_SIZE, refresh: true, searchKey });
      await loadDetail(formValues.keyName.trim());
      setSelectedRowKeys([formValues.keyName.trim()]);
    } finally {
      setSaveLoading(false);
    }
  };

  const keyType = Form.useWatch('keyType', editorForm) || 'string';

  const tableColumns: ColumnsType<IRedisKeyItem> = [
    {
      title: i18n('workspace.redis.key'),
      dataIndex: 'keyName',
      key: 'keyName',
      width: '28%',
      render: (value, record) => (
        <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeKey === record.keyName })}>{value}</div>
      ),
    },
    {
      title: i18n('workspace.redis.type'),
      dataIndex: 'keyType',
      key: 'keyType',
      width: '14%',
      render: (value, record) => (
        <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeKey === record.keyName })}>{value}</div>
      ),
    },
    {
      title: i18n('workspace.redis.value'),
      dataIndex: 'valuePreview',
      key: 'valuePreview',
      width: '40%',
      render: (value, record) => (
        <div
          className={classnames(styles.tableCell, styles.valueCell, { [styles.activeTableCell]: activeKey === record.keyName })}
          title={value || ''}
        >
          {value || '-'}
        </div>
      ),
    },
    {
      title: i18n('workspace.redis.ttl'),
      dataIndex: 'ttlSeconds',
      key: 'ttlSeconds',
      width: '18%',
      render: (value, record) => (
        <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeKey === record.keyName })}>
          {buildRedisTtlText(value)}
        </div>
      ),
    },
  ];

  const hashColumns: ColumnsType<IHashRow> = [
    {
      title: 'Field',
      dataIndex: 'field',
      key: 'field',
      render: (_, row) => (
        <Input
          value={row.field}
          onChange={(e) => {
            setHashRows((prev) => prev.map((item) => (item.id === row.id ? { ...item, field: e.target.value } : item)));
          }}
          size="small"
        />
      ),
    },
    {
      title: 'Value',
      dataIndex: 'value',
      key: 'value',
      render: (_, row) => (
        <Input
          value={row.value}
          onChange={(e) => {
            setHashRows((prev) => prev.map((item) => (item.id === row.id ? { ...item, value: e.target.value } : item)));
          }}
          size="small"
        />
      ),
    },
    {
      title: '',
      dataIndex: 'op',
      key: 'op',
      width: 60,
      render: (_, row) => (
        <Button size="small" type="text" danger onClick={() => setHashRows((prev) => prev.filter((item) => item.id !== row.id))}>
          -
        </Button>
      ),
    },
  ];

  const simpleColumns = (setter: React.Dispatch<React.SetStateAction<ISimpleRow[]>>): ColumnsType<ISimpleRow> => [
    {
      title: 'Value',
      dataIndex: 'value',
      key: 'value',
      render: (_, row) => (
        <Input
          value={row.value}
          onChange={(e) => setter((prev) => prev.map((item) => (item.id === row.id ? { ...item, value: e.target.value } : item)))}
          size="small"
        />
      ),
    },
    {
      title: '',
      dataIndex: 'op',
      key: 'op',
      width: 60,
      render: (_, row) => (
        <Button size="small" type="text" danger onClick={() => setter((prev) => prev.filter((item) => item.id !== row.id))}>
          -
        </Button>
      ),
    },
  ];

  const zsetColumns: ColumnsType<IZSetRow> = [
    {
      title: 'Member',
      dataIndex: 'member',
      key: 'member',
      render: (_, row) => (
        <Input
          value={row.member}
          onChange={(e) => {
            setZsetRows((prev) => prev.map((item) => (item.id === row.id ? { ...item, member: e.target.value } : item)));
          }}
          size="small"
        />
      ),
    },
    {
      title: 'Score',
      dataIndex: 'score',
      key: 'score',
      width: 140,
      render: (_, row) => (
        <Input
          value={row.score}
          onChange={(e) => {
            setZsetRows((prev) => prev.map((item) => (item.id === row.id ? { ...item, score: e.target.value } : item)));
          }}
          size="small"
        />
      ),
    },
    {
      title: '',
      dataIndex: 'op',
      key: 'op',
      width: 60,
      render: (_, row) => (
        <Button size="small" type="text" danger onClick={() => setZsetRows((prev) => prev.filter((item) => item.id !== row.id))}>
          -
        </Button>
      ),
    },
  ];

  const valueEditor = useMemo(() => {
    if (keyType === 'string') {
      return <TextArea value={stringValue} onChange={(e) => setStringValue(e.target.value)} rows={8} />;
    }

    if (keyType === 'hash') {
      return (
        <>
          <div className={styles.editorActionRow}>
            <Button size="small" onClick={() => setHashRows((prev) => [...prev, { id: uuid(), field: '', value: '' }])}>
              +
            </Button>
          </div>
          <Table rowKey="id" columns={hashColumns} dataSource={hashRows} pagination={false} size="small" />
        </>
      );
    }

    if (keyType === 'list') {
      return (
        <>
          <div className={styles.editorActionRow}>
            <Button size="small" onClick={() => setListRows((prev) => [...prev, { id: uuid(), value: '' }])}>
              +
            </Button>
          </div>
          <Table rowKey="id" columns={simpleColumns(setListRows)} dataSource={listRows} pagination={false} size="small" />
        </>
      );
    }

    if (keyType === 'set') {
      return (
        <>
          <div className={styles.editorActionRow}>
            <Button size="small" onClick={() => setSetRows((prev) => [...prev, { id: uuid(), value: '' }])}>
              +
            </Button>
          </div>
          <Table rowKey="id" columns={simpleColumns(setSetRows)} dataSource={setRows} pagination={false} size="small" />
        </>
      );
    }

    if (keyType === 'zset') {
      return (
        <>
          <div className={styles.editorActionRow}>
            <Button size="small" onClick={() => setZsetRows((prev) => [...prev, { id: uuid(), member: '', score: '0' }])}>
              +
            </Button>
          </div>
          <Table rowKey="id" columns={zsetColumns} dataSource={zsetRows} pagination={false} size="small" />
        </>
      );
    }

    return (
      <TextArea
        rows={8}
        value={streamJson}
        onChange={(e) => setStreamJson(e.target.value)}
        placeholder='[{"id":"*","values":[{"field":"name","value":"alice"}]}]'
      />
    );
  }, [keyType, stringValue, hashRows, listRows, setRows, zsetRows, streamJson]);

  return (
    <div className={classnames(styles.redisBrowser, className)}>
      <div className={styles.headerBox}>
        <div className={styles.headerBoxLeft}>
          <Button size="small" type="text" onClick={onCreateKey}>
            + {i18n('workspace.redis.createKey')}
          </Button>
          <Button size="small" type="text" danger onClick={onDeleteKeys}>
            - {i18n('workspace.redis.deleteKey')}
          </Button>
          <Button
            size="small"
            type="text"
            onClick={() => fetchTable({ pageNo: 1, pageSize: REDIS_PAGE_SIZE, refresh: true, searchKey })}
          >
            {i18n('common.button.refresh')}
          </Button>
        </div>
        <Search
          size="small"
          placeholder={i18n('common.text.search')}
          style={{ width: 220 }}
          value={searchKey}
          onChange={(e) => setSearchKey(e.target.value)}
          onSearch={(value) => fetchTable({ pageNo: 1, pageSize: REDIS_PAGE_SIZE, searchKey: value })}
        />
      </div>

      <div className={styles.redisTopPanel}>
        <div ref={tableBoxRef} className={styles.tableBox}>
          <Table
            loading={tableLoading}
            rowKey={(row) => row.keyName}
            rowSelection={{
              selectedRowKeys,
              onChange: (keys) => setSelectedRowKeys(keys),
            }}
            onRow={(row) => ({
              onClick: () => loadDetail(row.keyName),
            })}
            columns={tableColumns}
            dataSource={tableData}
            pagination={false}
            scroll={{ y: Math.max(allTableHeight - 12, 120) }}
            size="small"
          />
        </div>
        <div className={styles.pagingBox}>
          <Pagination
            current={currentPageNo}
            pageSize={REDIS_PAGE_SIZE}
            total={tableTotal}
            showSizeChanger={false}
            onChange={(pageNo) => fetchTable({ pageNo, pageSize: REDIS_PAGE_SIZE, searchKey })}
          />
        </div>
      </div>

      <div className={styles.redisBottomPanel}>
        <Form form={editorForm} layout="vertical" className={styles.editorForm}>
          <Form.Item name="originalKeyName" hidden>
            <Input />
          </Form.Item>
          <div className={styles.editorMetaRow}>
            <Form.Item
              label={i18n('workspace.redis.key')}
              name="keyName"
              rules={[{ required: true, message: i18n('workspace.redis.keyName') }]}
              className={styles.editorMetaItem}
            >
              <Input size="small" />
            </Form.Item>
            <Form.Item label={i18n('workspace.redis.type')} name="keyType" className={styles.editorMetaItem}>
              <Select
                size="small"
                options={REDIS_TYPES.map((type) => ({ label: type, value: type }))}
                onChange={() => {
                  setStringValue('');
                  setHashRows([]);
                  setListRows([]);
                  setSetRows([]);
                  setZsetRows([]);
                  setStreamJson('[]');
                }}
              />
            </Form.Item>
            <Form.Item label={i18n('workspace.redis.ttlSeconds')} name="ttlSeconds" className={styles.editorMetaItem}>
              <InputNumber size="small" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <div className={styles.editorValueTitle}>{i18n('workspace.redis.value')}</div>
          <div className={styles.editorValueBody}>{detailLoading ? <div>Loading...</div> : valueEditor}</div>

          <div className={styles.editorFooter}>
            <Space>
              <Button type="primary" loading={saveLoading} onClick={onSave}>
                {i18n('common.button.save')}
              </Button>
            </Space>
          </div>
        </Form>
      </div>
    </div>
  );
});

const CommonTableView = memo(({ uniqueData, className }: Omit<IProps, 'className'> & { className?: string }) => {
  const pageSize = 1000;

  const [tableData, setTableData] = useState<any[] | null>(null);
  const [tableLoading, setTableLoading] = useState<boolean>(false);
  const tableBoxRef = useRef<HTMLDivElement>(null);
  const [allTableWidth, setAllTableWidth] = useState(0);
  const [allTableHeight, setAllTableHeight] = useState(0);
  const [activeId, setActiveId] = useState<string>('');
  const [tableDataTotal, setTableDataTotal] = useState(0);
  const [currentPageNo, setCurrentPageNo] = useState(1);
  const [openDropdown, setOpenDropdown] = useState<boolean | undefined>(undefined);
  const [dropdownItems, setDropdownItems] = useState<any[]>([]);

  useEffect(() => {
    getTable({ pageNo: 1, pageSize });
  }, []);

  useEffect(() => {
    if (openDropdown === false) {
      setOpenDropdown(undefined);
    }
  }, [openDropdown]);

  const getTable = (params: IPageParams) => {
    setCurrentPageNo(params.pageNo);
    setTableLoading(true);

    sqlServer
      .getTableList({
        ...(params || {}),
        ...uniqueData,
      } as any)
      .then((res: any) => {
        setTableDataTotal(res.total || 0);
        const data = (res.data || []).map((t: any) => {
          const rowId = uuid();
          return {
            uuid: rowId,
            name: t.name,
            treeNodeType: TreeNodeType.TABLE,
            key: t.name,
            pinned: t.pinned,
            comment: t.comment,
            extraParams: {
              ...uniqueData,
              tableName: t.name,
            },
          };
        });
        setTableData(data);
      })
      .finally(() => {
        setTableLoading(false);
      });
  };

  useEffect(() => {
    if (!tableBoxRef.current) {
      return;
    }

    const resizeObserver = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      setAllTableWidth(width);
      setAllTableHeight(height);
    });

    resizeObserver.observe(tableBoxRef.current);
    return () => resizeObserver.disconnect();
  }, []);

  const createTable = () => {
    addWorkspaceTab({
      id: uuid(),
      title: i18n('editTable.button.createTable'),
      type: WorkspaceTabType.CreateTable,
      uniqueData: {
        ...uniqueData,
      },
    });
  };

  const getDropdownsItems = (record: any) => {
    const rightClickMenu = getRightClickMenu({
      treeNodeData: record,
      loadData: () => {},
    });

    const dropdownsItems: any = rightClickMenu.map((item) => ({
      key: item.key,
      type: item.type,
      onClick: () => {
        setOpenDropdown(false);
        item.onClick(record);
      },
      label: <MenuLabel icon={item.labelProps.icon} label={item.labelProps.label} />,
    }));

    const excludeList = [
      OperationColumn.OpenTable,
      OperationColumn.CreateConsole,
      OperationColumn.ViewDDL,
      OperationColumn.EditTable,
      OperationColumn.CopyName,
    ];

    return dropdownsItems.filter((item) => excludeList.includes(item.type));
  };

  const renderCell = (text: string, record: any) => (
    <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeId === record.key })}>{text}</div>
  );

  return (
    <div className={classnames(styles.allTable, className)}>
      <div className={styles.headerBox}>
        <div className={styles.headerBoxLeft}>
          <Iconfont code="&#xe726;" box boxSize={24} onClick={createTable} />
          <Iconfont
            onClick={() => getTable({ pageNo: 1, pageSize, refresh: true } as any)}
            code="&#xe668;"
            box
            boxSize={24}
          />
        </div>
        <Search
          size="small"
          placeholder={i18n('common.text.search')}
          onSearch={(value) => getTable({ pageNo: 1, pageSize, searchKey: value } as any)}
          style={{ width: 180 }}
        />
      </div>
      <div className={styles.contentCenter}>
        <Dropdown
          open={openDropdown}
          menu={{ items: dropdownItems }}
          trigger={['contextMenu']}
          onOpenChange={(_open) => setOpenDropdown(_open)}
        >
          <div ref={tableBoxRef} className={styles.tableBox}>
            <Table
              loading={tableLoading}
              onRow={(row) => ({
                onClick: () => {
                  setActiveId(row.key);
                  setCurrentWorkspaceGlobalExtend({
                    code: 'viewDDL',
                    uniqueData: {
                      ...uniqueData,
                      tableName: row.name,
                    },
                  });
                },
                onContextMenu: (event) => {
                  event.preventDefault();
                  setActiveId(row.key);
                  setOpenDropdown(true);
                  setDropdownItems(getDropdownsItems(tableData?.find((t) => t.key === row.key)));
                },
              })}
              virtual
              scroll={{ x: allTableWidth - 10, y: allTableHeight - 25 }}
              columns={[
                { title: 'Table name', dataIndex: 'name', key: 'name', render: renderCell },
                { title: 'Comment', dataIndex: 'comment', key: 'comment', render: renderCell },
              ]}
              pagination={false}
              dataSource={tableData || []}
            />
          </div>
        </Dropdown>
      </div>
      <div className={styles.pagingBox}>
        <Pagination
          onChange={(pageNo) => getTable({ pageNo, pageSize })}
          showSizeChanger={false}
          current={currentPageNo}
          pageSize={pageSize}
          total={tableDataTotal}
        />
      </div>
    </div>
  );
});

export default memo<IProps>((props) => {
  const { className, uniqueData } = props;
  if (uniqueData.databaseType === 'REDIS') {
    return <RedisBrowserView uniqueData={uniqueData} className={className} />;
  }
  return <CommonTableView uniqueData={uniqueData} className={className} />;
});

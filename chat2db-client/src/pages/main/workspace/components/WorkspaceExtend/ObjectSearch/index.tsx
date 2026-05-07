import React, { memo, useEffect, useMemo, useState } from 'react';
import { Alert, Empty, Input, Pagination, Segmented, Spin } from 'antd';

import i18n from '@/i18n';
import { TreeNodeType } from '@/constants';
import { addWorkspaceTab } from '@/pages/main/workspace/store/console';
import sqlService, {
  GlobalObjectSearchType,
  IGlobalObjectSearchItem,
  IGlobalObjectSearchResponse,
} from '@/service/sql';
import { useConnectionStore } from '@/pages/main/store/connection';
import { openFunction, openProcedure, openTrigger, openView } from '@/blocks/Tree/functions/openAsyncSql';
import { openSqlTable } from '@/blocks/Tree/hooks/useSqlRightClickMenu';
import styles from './index.less';

type SearchTabKey = 'all' | GlobalObjectSearchType;

const PAGE_SIZE = 50;
const SEARCH_DEBOUNCE = 300;
const SEARCH_TABS: SearchTabKey[] = ['all', 'table', 'view', 'function', 'procedure', 'trigger'];

const TYPE_CONFIG: Record<GlobalObjectSearchType, { nodeType: TreeNodeType; labelKey: string }> = {
  table: {
    nodeType: TreeNodeType.TABLE,
    labelKey: 'workspace.objectSearch.type.table',
  },
  view: {
    nodeType: TreeNodeType.VIEW,
    labelKey: 'workspace.tree.view',
  },
  function: {
    nodeType: TreeNodeType.FUNCTION,
    labelKey: 'workspace.tree.function',
  },
  procedure: {
    nodeType: TreeNodeType.PROCEDURE,
    labelKey: 'workspace.tree.procedure',
  },
  trigger: {
    nodeType: TreeNodeType.TRIGGER,
    labelKey: 'workspace.tree.trigger',
  },
};

const initialResponse: IGlobalObjectSearchResponse = {
  data: [],
  total: 0,
  partial: false,
  warnings: [],
  countsByType: {
    table: 0,
    view: 0,
    function: 0,
    procedure: 0,
    trigger: 0,
  },
};

const ObjectSearch = memo(() => {
  const connectionList = useConnectionStore((state) => state.connectionList);

  const [keyword, setKeyword] = useState('');
  const [activeTab, setActiveTab] = useState<SearchTabKey>('all');
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<IGlobalObjectSearchResponse>(initialResponse);

  useEffect(() => {
    setPageNo(1);
  }, [activeTab]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setLoading(true);
      sqlService
        .searchGlobalObjects({
          keyword,
          types: activeTab === 'all' ? undefined : [activeTab],
          pageNo,
          pageSize: PAGE_SIZE,
          refresh: true,
        })
        .then((result) => {
          setResponse({
            ...initialResponse,
            ...result,
            data: (result?.data || []).filter((item) => item?.objectName),
            warnings: result?.warnings || [],
            countsByType: {
              ...initialResponse.countsByType,
              ...(result?.countsByType || {}),
            },
          });
        })
        .finally(() => {
          setLoading(false);
        });
    }, SEARCH_DEBOUNCE);

    return () => clearTimeout(timer);
  }, [activeTab, keyword, pageNo]);

  const itemCounts = useMemo(() => {
    const countsByType = response.countsByType || {};
    return {
      all: Object.values(countsByType).reduce((sum, value) => sum + (value || 0), 0),
      table: countsByType.table || 0,
      view: countsByType.view || 0,
      function: countsByType.function || 0,
      procedure: countsByType.procedure || 0,
      trigger: countsByType.trigger || 0,
    };
  }, [response.countsByType]);

  const tabOptions = useMemo(() => {
    return SEARCH_TABS.map((item) => {
      const label =
        item === 'all'
          ? i18n('workspace.objectSearch.all')
          : i18n(TYPE_CONFIG[item].labelKey);
      return {
        label: `${label} (${itemCounts[item]})`,
        value: item,
      };
    });
  }, [itemCounts]);

  const openItem = (item: IGlobalObjectSearchItem) => {
    const treeNodeData = {
      key: `${item.objectType}-${item.dataSourceId}-${item.objectName}`,
      name: item.objectName,
      treeNodeType: TYPE_CONFIG[item.objectType].nodeType,
      extraParams: {
        dataSourceId: item.dataSourceId,
        dataSourceName: item.dataSourceName,
        databaseType: item.databaseType,
        supportDatabase: item.supportDatabase,
        supportSchema: item.supportSchema,
        databaseName: item.databaseName,
        schemaName: item.schemaName,
      },
    };

    if (item.objectType === 'table') {
      openSqlTable(treeNodeData as any, addWorkspaceTab);
      return;
    }

    const consoleParams = {
      treeNodeData,
    };
    switch (item.objectType) {
      case 'view':
        openView(consoleParams as any);
        return;
      case 'function':
        openFunction(consoleParams as any);
        return;
      case 'procedure':
        openProcedure(consoleParams as any);
        return;
      case 'trigger':
        openTrigger(consoleParams as any);
        return;
      default:
        return;
    }
  };

  const scopeText = i18n('workspace.objectSearch.scope.global');
  const hasConnections = (connectionList?.length || 0) > 0;

  const renderBody = () => {
    if (!hasConnections) {
      return (
        <Empty
          className={styles.empty}
          description={i18n('workspace.tips.noConnection')}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      );
    }

    if (loading) {
      return (
        <div className={styles.loadingBox}>
          <Spin />
        </div>
      );
    }

    if (!response.data?.length) {
      return (
        <Empty
          className={styles.empty}
          description={i18n('workspace.objectSearch.empty')}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      );
    }

    return (
      <div className={styles.list}>
        {response.data.map((item) => (
          <div
            key={`${item.objectType}-${item.dataSourceId}-${item.databaseName}-${item.schemaName}-${item.objectName}`}
            className={styles.listItem}
            onClick={() => openItem(item)}
          >
            <div className={styles.listItemMain}>
              <div className={styles.content}>
                <div className={styles.name}>{item.objectName}</div>
                <div className={styles.meta}>
                  <span>{i18n(TYPE_CONFIG[item.objectType].labelKey)}</span>
                  <span className={styles.path}>
                    {[item.dataSourceName, item.databaseName, item.schemaName].filter(Boolean).join(' / ')}
                  </span>
                </div>
                {item.comment ? <div className={styles.comment}>{item.comment}</div> : null}
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className={styles.objectSearch}>
      <div className={styles.header}>
        <div className={styles.headerTitle}>{i18n('workspace.objectSearch.panelTitle')}</div>
        <button
          type="button"
          className={styles.refreshButton}
          onClick={() => {
            setLoading(true);
            sqlService
              .searchGlobalObjects({
                keyword,
                types: activeTab === 'all' ? undefined : [activeTab],
                pageNo,
                pageSize: PAGE_SIZE,
                refresh: true,
              })
              .then((result) => {
                setResponse({
                  ...initialResponse,
                  ...result,
                  data: (result?.data || []).filter((item) => item?.objectName),
                  warnings: result?.warnings || [],
                  countsByType: {
                    ...initialResponse.countsByType,
                    ...(result?.countsByType || {}),
                  },
                });
              })
              .finally(() => setLoading(false));
          }}
        >
          {i18n('common.button.refresh')}
        </button>
      </div>
      <div className={styles.scope}>{scopeText}</div>
      <div className={styles.searchBox}>
        <Input
          allowClear
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setPageNo(1);
          }}
          placeholder={i18n('workspace.objectSearch.placeholder')}
        />
      </div>
      <div className={styles.tabs}>
        <Segmented
          block
          options={tabOptions}
          value={activeTab}
          onChange={(value) => {
            setActiveTab(value as SearchTabKey);
            setPageNo(1);
          }}
        />
      </div>
      {response.partial ? (
        <div className={styles.warningBox}>
          <Alert
            showIcon
            type="warning"
            message={i18n('workspace.objectSearch.partialWarning')}
            description={response.warnings?.[0]}
          />
        </div>
      ) : null}
      <div className={styles.body}>{renderBody()}</div>
      <div className={styles.pagingBox}>
        <Pagination
          size="small"
          current={pageNo}
          pageSize={PAGE_SIZE}
          total={response.total || 0}
          showSizeChanger={false}
          onChange={(nextPage) => setPageNo(nextPage)}
        />
      </div>
    </div>
  );
});

export default ObjectSearch;

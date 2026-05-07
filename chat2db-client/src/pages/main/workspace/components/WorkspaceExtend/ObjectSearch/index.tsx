import React, { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { Empty, Input, Segmented, Spin } from 'antd';
import { v4 as uuid } from 'uuid';

import i18n from '@/i18n';
import Iconfont from '@/components/Iconfont';
import { isRedisWorkspace, TreeNodeType } from '@/constants';
import { useTreeStore } from '@/blocks/Tree/treeStore';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import { addWorkspaceTab } from '@/pages/main/workspace/store/console';
import sqlService from '@/service/sql';
import { openFunction, openProcedure, openTrigger, openView } from '@/blocks/Tree/functions/openAsyncSql';
import { openSqlTable } from '@/blocks/Tree/hooks/useSqlRightClickMenu';
import styles from './index.less';

type SearchTabKey = 'all' | 'table' | 'view' | 'function' | 'procedure' | 'trigger';
type SearchItemType = Exclude<SearchTabKey, 'all'>;

interface ISearchItem {
  key: string;
  name: string;
  comment?: string | null;
  type: SearchItemType;
}

const TYPE_CONFIG: Record<SearchItemType, { icon: string; nodeType: TreeNodeType; labelKey: string }> = {
  table: {
    icon: '\ue63e',
    nodeType: TreeNodeType.TABLE,
    labelKey: 'workspace.objectSearch.type.table',
  },
  view: {
    icon: '\ue70c',
    nodeType: TreeNodeType.VIEW,
    labelKey: 'workspace.tree.view',
  },
  function: {
    icon: '\ue76a',
    nodeType: TreeNodeType.FUNCTION,
    labelKey: 'workspace.tree.function',
  },
  procedure: {
    icon: '\ue73c',
    nodeType: TreeNodeType.PROCEDURE,
    labelKey: 'workspace.tree.procedure',
  },
  trigger: {
    icon: '\ue64a',
    nodeType: TreeNodeType.TRIGGER,
    labelKey: 'workspace.tree.trigger',
  },
};

const SEARCH_TABS: SearchTabKey[] = ['all', 'table', 'view', 'function', 'procedure', 'trigger'];

const ObjectSearch = memo(() => {
  const focusTreeNode = useTreeStore((state) => state.focusTreeNode);
  const { activeConsoleId, currentConnectionDetails, workspaceTabList } = useWorkspaceStore((state) => ({
    activeConsoleId: state.activeConsoleId,
    currentConnectionDetails: state.currentConnectionDetails,
    workspaceTabList: state.workspaceTabList,
  }));

  const [keyword, setKeyword] = useState('');
  const [activeTab, setActiveTab] = useState<SearchTabKey>('all');
  const [loading, setLoading] = useState(false);
  const [metadata, setMetadata] = useState<Record<SearchItemType, ISearchItem[]>>({
    table: [],
    view: [],
    function: [],
    procedure: [],
    trigger: [],
  });

  const activeTabData = useMemo(() => {
    return workspaceTabList?.find((item) => item.id === activeConsoleId)?.uniqueData;
  }, [activeConsoleId, workspaceTabList]);

  const searchScope = useMemo(() => {
    const scopeSource = activeTabData?.dataSourceId
      ? activeTabData
      : focusTreeNode?.dataSourceId
        ? focusTreeNode
        : currentConnectionDetails
          ? {
              dataSourceId: currentConnectionDetails.id,
              dataSourceName: currentConnectionDetails.alias,
              databaseType: currentConnectionDetails.type,
            }
          : null;

    if (!scopeSource?.dataSourceId) {
      return null;
    }

    return {
      dataSourceId: Number(scopeSource.dataSourceId),
      dataSourceName: scopeSource.dataSourceName,
      databaseType: scopeSource.databaseType,
      databaseName: scopeSource.databaseName,
      schemaName: scopeSource.schemaName,
    };
  }, [activeTabData, currentConnectionDetails, focusTreeNode]);

  const missingDatabase = useMemo(() => {
    if (!searchScope || isRedisWorkspace(searchScope.databaseType)) {
      return false;
    }
    return !searchScope.databaseName && !!currentConnectionDetails?.supportDatabase;
  }, [currentConnectionDetails?.supportDatabase, searchScope]);

  const fetchMetadata = useCallback(async () => {
    if (!searchScope || isRedisWorkspace(searchScope.databaseType) || missingDatabase) {
      setMetadata({
        table: [],
        view: [],
        function: [],
        procedure: [],
        trigger: [],
      });
      return;
    }

    const params = {
      dataSourceId: searchScope.dataSourceId,
      databaseName: searchScope.databaseName,
      schemaName: searchScope.schemaName,
    };

    setLoading(true);
    try {
      const [tableList, viewRes, functionRes, procedureRes, triggerRes] = await Promise.all([
        sqlService.getAllTableList({ ...params, refresh: true }),
        sqlService.getViewList({ ...params, pageNo: 1, pageSize: 1000 } as any),
        sqlService.getFunctionList({ ...params, pageNo: 1, pageSize: 1000 } as any),
        sqlService.getProcedureList({ ...params, pageNo: 1, pageSize: 1000 } as any),
        sqlService.getTriggerList({ ...params, pageNo: 1, pageSize: 1000 } as any),
      ]);

      setMetadata({
        table: (tableList || []).map((item) => ({
          key: `table-${item.name}`,
          name: item.name,
          comment: item.comment,
          type: 'table',
        })),
        view: ((viewRes as any)?.data || []).map((item) => ({
          key: `view-${item.name}`,
          name: item.name,
          comment: item.comment,
          type: 'view',
        })),
        function: ((functionRes as any)?.data || []).map((item) => ({
          key: `function-${item.name}`,
          name: item.name,
          comment: item.comment,
          type: 'function',
        })),
        procedure: ((procedureRes as any)?.data || []).map((item) => ({
          key: `procedure-${item.name}`,
          name: item.name,
          comment: item.comment,
          type: 'procedure',
        })),
        trigger: ((triggerRes as any)?.data || []).map((item) => ({
          key: `trigger-${item.name}`,
          name: item.name,
          comment: item.comment,
          type: 'trigger',
        })),
      });
    } finally {
      setLoading(false);
    }
  }, [missingDatabase, searchScope]);

  useEffect(() => {
    fetchMetadata();
  }, [fetchMetadata]);

  const allItems = useMemo(() => {
    return SEARCH_TABS.filter((item): item is SearchItemType => item !== 'all').flatMap((type) => metadata[type]);
  }, [metadata]);

  const itemCounts = useMemo(() => {
    return {
      all: allItems.length,
      table: metadata.table.length,
      view: metadata.view.length,
      function: metadata.function.length,
      procedure: metadata.procedure.length,
      trigger: metadata.trigger.length,
    };
  }, [allItems.length, metadata]);

  const filteredItems = useMemo(() => {
    const targetItems = activeTab === 'all' ? allItems : metadata[activeTab];
    const normalizedKeyword = keyword.trim().toLowerCase();

    if (!normalizedKeyword) {
      return targetItems;
    }

    return targetItems.filter((item) => {
      const comment = item.comment?.toLowerCase() || '';
      return item.name.toLowerCase().includes(normalizedKeyword) || comment.includes(normalizedKeyword);
    });
  }, [activeTab, allItems, keyword, metadata]);

  const openItem = (item: ISearchItem) => {
    if (!searchScope) {
      return;
    }

    const treeNodeData = {
      uuid: uuid(),
      key: item.key,
      name: item.name,
      treeNodeType: TYPE_CONFIG[item.type].nodeType,
      extraParams: {
        ...searchScope,
        tableName: item.type === 'table' ? item.name : undefined,
        functionName: item.type === 'function' ? item.name : undefined,
        procedureName: item.type === 'procedure' ? item.name : undefined,
        triggerName: item.type === 'trigger' ? item.name : undefined,
      },
    };

    switch (item.type) {
      case 'table':
        openSqlTable(treeNodeData as any, addWorkspaceTab);
        break;
      case 'view':
        openView({ treeNodeData } as any);
        break;
      case 'function':
        openFunction({ treeNodeData } as any);
        break;
      case 'procedure':
        openProcedure({ treeNodeData } as any);
        break;
      case 'trigger':
        openTrigger({ treeNodeData } as any);
        break;
      default:
        break;
    }
  };

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

  const scopeText = useMemo(() => {
    if (!searchScope) {
      return '';
    }
    return [searchScope.dataSourceName, searchScope.databaseName, searchScope.schemaName].filter(Boolean).join(' / ');
  }, [searchScope]);

  const renderBody = () => {
    if (!searchScope) {
      return (
        <Empty
          className={styles.empty}
          description={i18n('workspace.tips.noConnection')}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      );
    }

    if (isRedisWorkspace(searchScope.databaseType)) {
      return (
        <Empty
          className={styles.empty}
          description={i18n('workspace.objectSearch.unsupported')}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      );
    }

    if (missingDatabase) {
      return (
        <Empty
          className={styles.empty}
          description={i18n('workspace.objectSearch.selectDatabase')}
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

    if (!filteredItems.length) {
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
        {filteredItems.map((item) => (
          <div key={item.key} className={styles.listItem} onClick={() => openItem(item)}>
            <div className={styles.listItemMain}>
              <div className={styles.iconBox}>
                <Iconfont code={TYPE_CONFIG[item.type].icon} />
              </div>
              <div className={styles.content}>
                <div className={styles.name}>{item.name}</div>
                <div className={styles.meta}>
                  <span>{i18n(TYPE_CONFIG[item.type].labelKey)}</span>
                  {item.comment ? <span className={styles.comment}>{item.comment}</span> : null}
                </div>
              </div>
            </div>
            <Iconfont code="\ue651" className={styles.arrow} />
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className={styles.objectSearch}>
      <div className={styles.header}>
        <div className={styles.headerTitle}>{i18n('workspace.objectSearch.panelTitle')}</div>
        <Iconfont code="\ue668" box boxSize={24} onClick={fetchMetadata} />
      </div>
      <div className={styles.scope}>{scopeText || i18n('workspace.objectSearch.scope')}</div>
      <div className={styles.searchBox}>
        <Input
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder={i18n('workspace.objectSearch.placeholder')}
          prefix={<Iconfont code="\ue888" />}
        />
      </div>
      <div className={styles.tabs}>
        <Segmented
          block
          options={tabOptions}
          value={activeTab}
          onChange={(value) => setActiveTab(value as SearchTabKey)}
        />
      </div>
      <div className={styles.body}>{renderBody()}</div>
    </div>
  );
});

export default ObjectSearch;

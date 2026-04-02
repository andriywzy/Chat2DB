import { useMemo } from 'react';
import { v4 as uuid } from 'uuid';

import { ITreeNode } from '@/typings';
import { OperationColumn, TreeNodeType, WorkspaceTabType, isRedisWorkspace } from '@/constants';
import i18n from '@/i18n';

import { dataSourceFormConfigs } from '@/components/ConnectionEdit/config/dataSource';
import { IConnectionConfig } from '@/components/ConnectionEdit/config/types';
import { getTreeConfigItem, ITreeConfigItem } from '../treeConfig';

import { createConsole, addWorkspaceTab } from '@/pages/main/workspace/store/console';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import { getConnectionList, setConnectionManageActiveId } from '@/pages/main/store/connection';
import { setMainPageActiveTab } from '@/pages/main/store/main';

import { openFunction, openProcedure, openSequence, openTrigger, openView } from '../functions/openAsyncSql';
import { deleteSequence } from '../functions/deleteSequence';
import { deleteTable } from '../functions/deleteTable';
import { handelPinTable } from '../functions/pinTable';
import { viewDDL } from '../functions/viewDDL';
import connectionService from '@/service/connection';

import { createSqlViewAllTableTab, openSqlTable } from './useSqlRightClickMenu';
import { getRedisViewAllTableTitle, openRedisKey } from './useRedisRightClickMenu';

interface IProps {
  treeNodeData: ITreeNode;
  loadData: any;
}

interface IOperationColumnConfigItem {
  text: string;
  icon: string;
  doubleClickTrigger?: boolean;
  handle: (treeNodeData: ITreeNode) => void;
  discard?: boolean;
}

interface IRightClickMenu {
  key: number;
  onClick: (treeNodeData: ITreeNode) => void;
  type: OperationColumn;
  doubleClickTrigger?: boolean;
  labelProps: {
    icon: string;
    label: string;
  };
}

interface IMenuRuntime {
  openCreateDatabaseModal?: any;
  currentConnectionDetails?: any;
}

const getDataSourceFormConfig = (databaseType?: string) => {
  return dataSourceFormConfigs.find((t: IConnectionConfig) => t.type === databaseType);
};

const excludeUnsupportedOperations = (
  operationColumn: OperationColumn[] = [],
  dataSourceFormConfig?: IConnectionConfig,
) => {
  const excludes = dataSourceFormConfig?.baseInfo.excludes || [];

  return operationColumn.filter((item) => !excludes.includes(item));
};

const createViewAllTableTab = (treeNodeData: ITreeNode) => {
  if (isRedisWorkspace(treeNodeData.extraParams?.databaseType)) {
    addWorkspaceTab({
      id: uuid(),
      type: WorkspaceTabType.ViewAllTable,
      title: getRedisViewAllTableTitle(treeNodeData),
      uniqueData: {
        dataSourceId: treeNodeData.extraParams!.dataSourceId!,
        dataSourceName: treeNodeData.extraParams!.dataSourceName!,
        databaseType: treeNodeData.extraParams!.databaseType!,
        databaseName: treeNodeData.extraParams?.databaseName,
        schemaName: treeNodeData.extraParams?.schemaName,
      },
    });
    return;
  }

  createSqlViewAllTableTab(treeNodeData, addWorkspaceTab);
};

const openTable = (treeNodeData: ITreeNode) => {
  if (isRedisWorkspace(treeNodeData.extraParams?.databaseType) && treeNodeData.treeNodeType === TreeNodeType.KEY) {
    openRedisKey(treeNodeData, addWorkspaceTab);
    return;
  }

  openSqlTable(treeNodeData, addWorkspaceTab);
};

const buildRightClickMenu = ({ treeNodeData, loadData }: IProps, runtime: IMenuRuntime): IRightClickMenu[] => {
  const { openCreateDatabaseModal, currentConnectionDetails } = runtime;
  const treeNodeConfig: ITreeConfigItem = getTreeConfigItem(treeNodeData);
  const operationColumn = treeNodeConfig.operationColumn || [];
  const dataSourceFormConfig = getDataSourceFormConfig(treeNodeData.extraParams?.databaseType);

  const handelOpenCreateDatabaseModal = (type: 'database' | 'schema') => {
    const relyOnParams = {
      databaseType: treeNodeData.extraParams!.databaseType,
      dataSourceId: treeNodeData.extraParams!.dataSourceId!,
      databaseName: treeNodeData.name,
    };

    openCreateDatabaseModal?.({
      type,
      relyOnParams,
      executedCallback: () => {
        loadData({
          refresh: true,
        });
      },
    });
  };

  const handleEditSource = () => {
    const connectionDetail = treeNodeData.extraParams?.connectionDetail;
    if (!connectionDetail?.id) {
      return;
    }
    setConnectionManageActiveId(connectionDetail.id);
    setMainPageActiveTab('connections');
  };

  const handleShiftOut = async () => {
    const connectionId = treeNodeData.extraParams?.connectionDetail?.id || treeNodeData.extraParams?.dataSourceId;
    if (!connectionId) {
      return;
    }
    const detail = await connectionService.getDetails({ id: connectionId });
    await connectionService.update({
      ...detail,
      projectId: null as any,
    });
    await getConnectionList();
  };

  const operationColumnConfig: { [key in string]: IOperationColumnConfigItem } = {
    [OperationColumn.Refresh]: {
      text: i18n('common.button.refresh'),
      icon: '\uec08',
      handle: () => {
        loadData?.({
          refresh: true,
        });
      },
    },
    [OperationColumn.EditSource]: {
      text: i18n('connection.title.editConnection'),
      icon: '\ue602',
      handle: handleEditSource,
    },
    [OperationColumn.ShiftOut]: {
      text: i18n('workspace.database.removeFromProject'),
      icon: '\ue6a7',
      handle: handleShiftOut,
      discard: !(
        treeNodeData.extraParams?.connectionDetail?.projectId ||
        treeNodeData.extraParams?.connectionDetail?.groupId
      ),
    },
    [OperationColumn.CreateConsole]: {
      text: i18n('workspace.menu.queryConsole'),
      icon: '\ue619',
      handle: () => {
        createConsole({
          dataSourceId: treeNodeData.extraParams!.dataSourceId!,
          dataSourceName: treeNodeData.extraParams!.dataSourceName!,
          databaseType: treeNodeData.extraParams!.databaseType!,
          databaseName: treeNodeData.extraParams?.databaseName,
          schemaName: treeNodeData.extraParams?.schemaName,
        });
      },
    },
    [OperationColumn.ViewAllTable]: {
      text: i18n('workspace.menu.viewAllTable'),
      icon: '\ue611',
      doubleClickTrigger: true,
      handle: () => {
        createViewAllTableTab(treeNodeData);
      },
    },
    [OperationColumn.CreateTable]: {
      text: i18n('editTable.button.createTable'),
      icon: '\ue792',
      handle: () => {
        addWorkspaceTab({
          id: uuid(),
          title: i18n('editTable.button.createTable'),
          type: WorkspaceTabType.CreateTable,
          uniqueData: {
            dataSourceId: treeNodeData.extraParams!.dataSourceId!,
            databaseType: treeNodeData.extraParams!.databaseType!,
            databaseName: treeNodeData.extraParams?.databaseName,
            schemaName: treeNodeData.extraParams?.schemaName,
            submitCallback: () => {
              loadData?.({ refresh: true });
            },
          },
        });
      },
      discard: treeNodeData.treeNodeType === TreeNodeType.DATABASE && currentConnectionDetails?.supportSchema,
    },
    [OperationColumn.DeleteTable]: {
      text: i18n('workspace.menu.deleteTable'),
      icon: '\ue6a7',
      handle: () => {
        deleteTable(treeNodeData, loadData);
      },
    },
    [OperationColumn.ViewDDL]: {
      text: i18n('workspace.menu.ViewDDL'),
      icon: '\ue665',
      handle: () => {
        viewDDL(treeNodeData);
      },
    },
    [OperationColumn.Pin]: {
      text: treeNodeData.pinned ? i18n('workspace.menu.unPin') : i18n('workspace.menu.pin'),
      icon: treeNodeData.pinned ? '\ue61d' : '\ue627',
      handle: () => {
        handelPinTable({
          treeNodeData,
          loadData: () => {
            loadData({ treeNodeData: treeNodeData.parentNode });
          },
        });
      },
    },
    [OperationColumn.EditTable]: {
      text: i18n('workspace.menu.editTable'),
      icon: '\ue602',
      handle: () => {
        addWorkspaceTab({
          id: `${OperationColumn.EditTable}-${treeNodeData.uuid}`,
          title: treeNodeData?.name,
          type: WorkspaceTabType.EditTable,
          uniqueData: {
            dataSourceId: treeNodeData.extraParams!.dataSourceId!,
            databaseType: treeNodeData.extraParams!.databaseType!,
            databaseName: treeNodeData.extraParams?.databaseName,
            schemaName: treeNodeData.extraParams?.schemaName,
            tableName: treeNodeData?.name,
            submitCallback: () => {
              loadData({
                treeNodeData: treeNodeData.parentNode,
                refresh: true,
              });
            },
          },
        });
      },
    },
    [OperationColumn.CopyName]: {
      text: i18n('common.button.copyName'),
      icon: '\uec7a',
      handle: () => {
        navigator.clipboard.writeText(treeNodeData.name);
      },
    },
    [OperationColumn.OpenTable]: {
      text: i18n('workspace.menu.openTable'),
      icon: '\ue618',
      doubleClickTrigger: true,
      handle: () => {
        openTable(treeNodeData);
      },
    },
    [OperationColumn.OpenView]: {
      text: i18n('workspace.menu.view'),
      icon: '\ue651',
      doubleClickTrigger: true,
      handle: () => {
        openView({
          addWorkspaceTab,
          treeNodeData,
        });
      },
    },
    [OperationColumn.OpenFunction]: {
      text: i18n('workspace.menu.view'),
      icon: '\ue651',
      doubleClickTrigger: true,
      handle: () => {
        openFunction({
          addWorkspaceTab,
          treeNodeData,
        });
      },
    },
    [OperationColumn.OpenProcedure]: {
      text: i18n('workspace.menu.view'),
      icon: '\ue651',
      doubleClickTrigger: true,
      handle: () => {
        openProcedure({
          addWorkspaceTab,
          treeNodeData,
        });
      },
    },
    [OperationColumn.OpenTrigger]: {
      text: i18n('workspace.menu.view'),
      icon: '\ue651',
      doubleClickTrigger: true,
      handle: () => {
        openTrigger({
          addWorkspaceTab,
          treeNodeData,
        });
      },
    },
    [OperationColumn.OpenSequence]: {
      text: i18n('workspace.menu.view'),
      icon: '\ue651',
      doubleClickTrigger: true,
      handle: () => {
        openSequence({
          addWorkspaceTab,
          treeNodeData,
        });
      },
    },
    [OperationColumn.CreateSequence]: {
      text: i18n('editSequence.button.createSequence'),
      icon: '\ue792',
      handle: () => {
        addWorkspaceTab({
          id: uuid(),
          title: i18n('editSequence.button.createSequence'),
          type: WorkspaceTabType.CreateSequence,
          uniqueData: {
            dataSourceId: treeNodeData.extraParams!.dataSourceId!,
            databaseType: treeNodeData.extraParams!.databaseType!,
            databaseName: treeNodeData.extraParams?.databaseName,
            schemaName: treeNodeData.extraParams?.schemaName,
            submitCallback: () => {
              loadData?.({ refresh: true });
            },
          },
        });
      },
      discard: treeNodeData.treeNodeType === TreeNodeType.SEQUENCES && currentConnectionDetails?.supportSchema,
    },
    [OperationColumn.EditSequence]: {
      text: i18n('workspace.menu.editSequence'),
      icon: '\ue602',
      handle: () => {
        addWorkspaceTab({
          id: `${OperationColumn.EditSequence}-${treeNodeData.uuid}`,
          title: treeNodeData?.name,
          type: WorkspaceTabType.EditSequence,
          uniqueData: {
            dataSourceId: treeNodeData.extraParams!.dataSourceId!,
            databaseType: treeNodeData.extraParams!.databaseType!,
            databaseName: treeNodeData.extraParams?.databaseName,
            schemaName: treeNodeData.extraParams?.schemaName,
            tableName: treeNodeData?.name,
            submitCallback: () => {
              loadData({
                treeNodeData: treeNodeData.parentNode,
                refresh: true,
              });
            },
          },
        });
      },
    },
    [OperationColumn.DeleteSequence]: {
      text: i18n('workspace.menu.deleteSequence'),
      icon: '\ue6a7',
      handle: () => {
        deleteSequence(treeNodeData, loadData);
      },
    },
    [OperationColumn.CreateDatabase]: {
      text: i18n('workspace.menu.createDatabase'),
      icon: '\ue816',
      handle: () => {
        handelOpenCreateDatabaseModal('database');
      },
    },
    [OperationColumn.CreateSchema]: {
      text: i18n('workspace.menu.createSchema'),
      icon: '\ue696',
      handle: () => {
        handelOpenCreateDatabaseModal('schema');
      },
      discard: !currentConnectionDetails?.supportSchema,
    },
  };

  return excludeUnsupportedOperations(operationColumn, dataSourceFormConfig).reduce<IRightClickMenu[]>(
    (acc, item, index) => {
      const concrete = operationColumnConfig[item];

      if (!concrete || concrete.discard) {
        return acc;
      }

      acc.push({
        key: index,
        onClick: concrete.handle,
        type: item,
        doubleClickTrigger: concrete.doubleClickTrigger,
        labelProps: {
          icon: concrete.icon,
          label: concrete.text,
        },
      });

      return acc;
    },
    [],
  );
};

export const useGetRightClickMenu = (props: IProps) => {
  const { openCreateDatabaseModal, currentConnectionDetails } = useWorkspaceStore((state) => ({
    openCreateDatabaseModal: state.openCreateDatabaseModal,
    currentConnectionDetails: state.currentConnectionDetails,
  }));

  return useMemo(
    () => buildRightClickMenu(props, { openCreateDatabaseModal, currentConnectionDetails }),
    [props.treeNodeData, props.loadData, openCreateDatabaseModal, currentConnectionDetails],
  );
};

export const getRightClickMenu = (props: IProps) => {
  const { openCreateDatabaseModal, currentConnectionDetails } = useWorkspaceStore.getState();

  return buildRightClickMenu(props, { openCreateDatabaseModal, currentConnectionDetails });
};

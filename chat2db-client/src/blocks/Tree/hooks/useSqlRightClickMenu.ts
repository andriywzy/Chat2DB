import { v4 as uuid } from 'uuid';

import { ITreeNode } from '@/typings';
import { OperationColumn, WorkspaceTabType } from '@/constants';
import { compatibleDataBaseName } from '@/utils/database';

interface IAddWorkspaceTab {
  (params: {
    id: string;
    title: string;
    type: WorkspaceTabType;
    uniqueData: Record<string, any>;
  }): void;
}

export const getSqlViewAllTableTitle = (treeNodeData: ITreeNode) => {
  return `${treeNodeData.extraParams!.databaseName!}-tables`;
};

export const openSqlTable = (treeNodeData: ITreeNode, addWorkspaceTab: IAddWorkspaceTab) => {
  const databaseName = compatibleDataBaseName(treeNodeData.name!, treeNodeData.extraParams!.databaseType);

  addWorkspaceTab({
    id: `${OperationColumn.OpenTable}-${treeNodeData.uuid}`,
    title: treeNodeData.name,
    type: WorkspaceTabType.EditTableData,
    uniqueData: {
      dataSourceId: treeNodeData.extraParams!.dataSourceId!,
      dataSourceName: treeNodeData.extraParams!.dataSourceName!,
      databaseType: treeNodeData.extraParams!.databaseType!,
      supportDatabase: treeNodeData.extraParams?.supportDatabase,
      supportSchema: treeNodeData.extraParams?.supportSchema,
      databaseName: treeNodeData.extraParams?.databaseName,
      schemaName: treeNodeData.extraParams?.schemaName,
      tableName: treeNodeData.name,
      sql: `select * from ${databaseName}`,
    },
  });
};

export const createSqlViewAllTableTab = (treeNodeData: ITreeNode, addWorkspaceTab: IAddWorkspaceTab) => {
  addWorkspaceTab({
    id: uuid(),
    type: WorkspaceTabType.ViewAllTable,
    title: getSqlViewAllTableTitle(treeNodeData),
    uniqueData: {
      dataSourceId: treeNodeData.extraParams!.dataSourceId!,
      dataSourceName: treeNodeData.extraParams!.dataSourceName!,
      databaseType: treeNodeData.extraParams!.databaseType!,
      databaseName: treeNodeData.extraParams?.databaseName,
      schemaName: treeNodeData.extraParams?.schemaName,
    },
  });
};

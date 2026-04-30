import { OperationColumn, TreeNodeType } from '@/constants';
import { v4 as uuid } from 'uuid';

import i18n from '@/i18n';
import { ITreeConfig } from './treeConfigTypes';

export const redisTreeConfig: ITreeConfig = {
  [TreeNodeType.SCHEMAS]: {
    icon: '\ue696',
    getChildren: (parentData) => {
      const { dataSourceId, databaseName, schemaName } = parentData.extraParams!;
      const preCode = [dataSourceId, databaseName, schemaName].join('-');
      return new Promise((r) => {
        r([
          {
            uuid: uuid(),
            key: `${preCode}-all-data`,
            name: i18n('workspace.redis.allData'),
            treeNodeType: TreeNodeType.TABLES,
            isLeaf: true,
            extraParams: parentData.extraParams,
          },
        ]);
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.TABLES]: {
    icon: '\ueac5',
    getChildren: () => {
      return Promise.resolve({
        data: [],
        pageNo: 1,
        pageSize: 0,
        total: 0,
        hasNextPage: false,
      } as any);
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.ViewAllTable, OperationColumn.Refresh],
  },
  [TreeNodeType.KEY]: {
    icon: '\ue775',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.OpenTable, OperationColumn.CopyName],
  },
};

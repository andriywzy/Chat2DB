import { IConnectionDetails, ITreeNode } from '@/typings';
import { OperationColumn, TreeNodeType } from '@/constants';
import connectionService from '@/service/connection';
import { v4 as uuid } from 'uuid';
import { ITreeConfig } from './treeConfigTypes';

export const baseTreeConfig: ITreeConfig = {
  [TreeNodeType.DATA_SOURCES]: {
    getChildren: () => {
      return new Promise((r: (value: ITreeNode[]) => void, j) => {
        const p = {
          pageNo: 1,
          pageSize: 1000,
        };
        connectionService
          .getList(p)
          .then((res) => {
            const data: ITreeNode[] = res.data.map((t: IConnectionDetails) => ({
              uuid: uuid(),
              key: t.id,
              name: t.alias,
              treeNodeType: TreeNodeType.DATA_SOURCE,
              extraParams: {
                databaseType: t.type,
                dataSourceId: t.id,
                dataSourceName: t.alias,
              },
            }));
            r(data);
          })
          .catch(() => {
            j();
          });
      });
    },
  },
  [TreeNodeType.DATA_SOURCE]: {
    getChildren: (params: { dataSourceId: number; dataSourceName: string; extraParams: any }) => {
      return new Promise((r, j) => {
        const _extraParams = params.extraParams;
        delete params.extraParams;
        connectionService
          .getDatabaseList(params)
          .then((res) => {
            const data: ITreeNode[] = res.map((t: any) => ({
              uuid: uuid(),
              key: t.name,
              name: t.name,
              treeNodeType: TreeNodeType.DATABASE,
              extraParams: {
                ..._extraParams,
                databaseName: t.name,
              },
            }));
            r(data);
          })
          .catch(() => {
            j();
          });
      });
    },
    operationColumn: [OperationColumn.EditSource, OperationColumn.Refresh, OperationColumn.ShiftOut],
    next: TreeNodeType.DATABASE,
  },
  [TreeNodeType.DATABASE]: {
    icon: '\ue62c',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r: (value: ITreeNode[], b?: any) => void, j) => {
        connectionService
          .getSchemaList(params)
          .then((res) => {
            const data: ITreeNode[] = res.map((t: any) => ({
              uuid: uuid(),
              key: t.name,
              name: t.name,
              treeNodeType: TreeNodeType.SCHEMAS,
              schemaName: t.name,
              extraParams: {
                ..._extraParams,
                schemaName: t.name,
              },
            }));
            r(data);
          })
          .catch(() => {
            j();
          });
      });
    },
    operationColumn: [
      OperationColumn.CreateConsole,
      OperationColumn.CreateSchema,
      OperationColumn.CopyName,
      OperationColumn.Refresh,
    ],
    next: TreeNodeType.SCHEMAS,
  },
};

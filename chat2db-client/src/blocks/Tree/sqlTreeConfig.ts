import { OperationColumn, TreeNodeType } from '@/constants';
import { v4 as uuid } from 'uuid';

import mysqlServer from '@/service/sql';
import { ITreeConfig } from './treeConfigTypes';

export const sqlTreeConfig: ITreeConfig = {
  [TreeNodeType.SCHEMAS]: {
    icon: '\ue696',
    getChildren: (parentData) => {
      const { dataSourceId, databaseName, schemaName } = parentData.extraParams!;
      const preCode = [dataSourceId, databaseName, schemaName].join('-');
      return new Promise((r) => {
        const data = [
          {
            uuid: uuid(),
            key: `${preCode}-tables`,
            name: 'tables',
            treeNodeType: TreeNodeType.TABLES,
            extraParams: parentData.extraParams,
          },
          {
            uuid: uuid(),
            key: `${preCode}-views`,
            name: 'view',
            treeNodeType: TreeNodeType.VIEWS,
            extraParams: parentData.extraParams,
          },
          {
            uuid: uuid(),
            key: `${preCode}-functions`,
            name: 'functions',
            treeNodeType: TreeNodeType.FUNCTIONS,
            extraParams: parentData.extraParams,
          },
          {
            uuid: uuid(),
            key: `${preCode}-procedures`,
            name: 'procedures',
            treeNodeType: TreeNodeType.PROCEDURES,
            extraParams: parentData.extraParams,
          },
          {
            uuid: uuid(),
            key: `${preCode}-triggers`,
            name: 'triggers',
            treeNodeType: TreeNodeType.TRIGGERS,
            extraParams: parentData.extraParams,
          },
        ];
        if (
          (parentData.extraParams?.databaseType === 'POSTGRESQL' ||
            parentData.extraParams?.databaseType === 'ORACLE') &&
          schemaName === 'public'
        ) {
          data.push({
            uuid: uuid(),
            key: `${preCode}-sequences`,
            name: 'sequences',
            treeNodeType: TreeNodeType.SEQUENCES,
            extraParams: parentData.extraParams,
          });
        }
        r(data);
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.TABLES]: {
    icon: '\ueac5',
    getChildren: (params, options) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      params.pageSize = 1000;
      return new Promise((r, j) => {
        mysqlServer
          .getTableList(params, options)
          .then((res) => {
            const tableList = res.data?.map((t: any) => ({
              uuid: uuid(),
              name: t.name,
              treeNodeType: TreeNodeType.TABLE,
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              extraParams: {
                ..._extraParams,
                tableName: t.name,
              },
            }));
            r({
              data: tableList,
              pageNo: res.pageNo,
              pageSize: res.pageSize,
              total: res.total,
              hasNextPage: res.hasNextPage,
            } as any);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [
      OperationColumn.CreateConsole,
      OperationColumn.ViewAllTable,
      OperationColumn.CreateTable,
      OperationColumn.Refresh,
    ],
  },
  [TreeNodeType.TABLE]: {
    icon: '\ue63e',
    getChildren: (params) => {
      return new Promise((r) => {
        const { dataSourceId, databaseName, schemaName, tableName } = params.extraParams!;
        const preCode = [dataSourceId, databaseName, schemaName, tableName].join('-');
        const list = [
          {
            uuid: uuid(),
            key: `${preCode}-columns`,
            name: 'columns',
            treeNodeType: TreeNodeType.COLUMNS,
            extraParams: params.extraParams,
          },
          {
            uuid: uuid(),
            key: `${preCode}-keys`,
            name: 'keys',
            treeNodeType: TreeNodeType.KEYS,
            extraParams: params.extraParams,
          },
          {
            uuid: uuid(),
            key: `${preCode}-indexs`,
            name: 'indexs',
            treeNodeType: TreeNodeType.INDEXES,
            extraParams: params.extraParams,
          },
        ];

        r(list);
      });
    },
    operationColumn: [
      OperationColumn.OpenTable,
      OperationColumn.CreateConsole,
      OperationColumn.Pin,
      OperationColumn.ViewDDL,
      OperationColumn.EditTable,
      OperationColumn.CopyName,
      OperationColumn.Refresh,
      OperationColumn.DeleteTable,
    ],
  },
  [TreeNodeType.VIEWS]: {
    icon: '\ue70c',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getViewList(params)
          .then((res) => {
            const viewList = res.data?.map((t: any) => ({
              uuid: uuid(),
              name: t.name,
              treeNodeType: TreeNodeType.VIEW,
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              extraParams: {
                ..._extraParams,
                tableName: t.name,
              },
            }));
            r(viewList);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.FUNCTIONS]: {
    icon: '\ue76a',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getFunctionList(params)
          .then((res) => {
            const list = res.data?.map((t: any) => ({
              uuid: uuid(),
              name: t.functionName,
              treeNodeType: TreeNodeType.FUNCTION,
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              isLeaf: true,
              extraParams: {
                ..._extraParams,
                functionName: t.functionName,
              },
            }));
            r(list);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.FUNCTION]: {
    icon: '\ue76a',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.OpenFunction, OperationColumn.CopyName],
  },
  [TreeNodeType.PROCEDURES]: {
    icon: '\ue73c',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getProcedureList(params)
          .then((res) => {
            const list = res.data?.map((t: any) => ({
              uuid: uuid(),
              name: t.procedureName,
              treeNodeType: TreeNodeType.PROCEDURE,
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              isLeaf: true,
              extraParams: {
                ..._extraParams,
                procedureName: t.procedureName,
              },
            }));
            r(list);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.PROCEDURE]: {
    icon: '\ue73c',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.OpenProcedure, OperationColumn.CopyName],
  },
  [TreeNodeType.TRIGGERS]: {
    icon: '\ue64a',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getTriggerList(params)
          .then((res) => {
            const list = res.data?.map((t: any) => ({
              uuid: uuid(),
              name: t.triggerName,
              treeNodeType: TreeNodeType.TRIGGER,
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              isLeaf: true,
              extraParams: {
                ..._extraParams,
                triggerName: t.triggerName,
              },
            }));
            r(list);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.TRIGGER]: {
    icon: '\ue64a',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.OpenTrigger, OperationColumn.CopyName],
  },
  [TreeNodeType.VIEW]: {
    icon: '\ue70c',
    getChildren: (params) => {
      return new Promise((r) => {
        r([
          {
            uuid: uuid(),
            name: 'columns',
            treeNodeType: TreeNodeType.COLUMNS,
            key: 'columns',
            extraParams: params.extraParams,
          },
        ]);
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.OpenView, OperationColumn.CopyName],
  },
  [TreeNodeType.VIEWCOLUMNS]: {
    icon: '\ue647',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getViewColumnList(params)
          .then((res) => {
            const list = res.data?.map((t: any) => ({
              uuid: uuid(),
              name: t.name,
              treeNodeType: TreeNodeType.VIEWCOLUMN,
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              isLeaf: true,
              extraParams: _extraParams,
            }));
            r(list);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.CopyName, OperationColumn.Refresh],
  },
  [TreeNodeType.VIEWCOLUMN]: {
    icon: '\ue647',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.CopyName],
  },
  [TreeNodeType.COLUMNS]: {
    icon: '\ueac5',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getColumnList(params)
          .then((res) => {
            const tableList = res?.map((item) => ({
              uuid: uuid(),
              name: item.name,
              treeNodeType: TreeNodeType.COLUMN,
              key: item.name,
              isLeaf: true,
              columnType: item.columnType,
              comment: item.comment,
              extraParams: _extraParams,
            }));
            r(tableList);
          })
          .catch(() => {
            j();
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.Refresh],
  },
  [TreeNodeType.COLUMN]: {
    icon: '\ue611',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.CopyName],
  },
  [TreeNodeType.KEYS]: {
    icon: '\ueac5',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getKeyList(params)
          .then((res) => {
            const tableList = res?.map((item) => ({
              uuid: uuid(),
              name: item.name,
              treeNodeType: TreeNodeType.KEY,
              key: item.name,
              isLeaf: true,
              extraParams: _extraParams,
            }));
            r(tableList);
          })
          .catch(() => {
            j();
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.CopyName, OperationColumn.Refresh],
  },
  [TreeNodeType.KEY]: {
    icon: '\ue775',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.OpenTable, OperationColumn.CopyName],
  },
  [TreeNodeType.INDEXES]: {
    icon: '\ueac5',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getIndexList(params)
          .then((res) => {
            const tableList = res?.map((item) => ({
              uuid: uuid(),
              name: item.name,
              treeNodeType: TreeNodeType.INDEX,
              key: item.name,
              isLeaf: true,
              extraParams: _extraParams,
            }));
            r(tableList);
          })
          .catch(() => {
            j();
          });
      });
    },
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.CopyName, OperationColumn.Refresh],
  },
  [TreeNodeType.INDEX]: {
    icon: '\ue65b',
    operationColumn: [OperationColumn.CreateConsole, OperationColumn.CopyName],
  },
  [TreeNodeType.SEQUENCES]: {
    icon: '\ueabe',
    getChildren: (params) => {
      const _extraParams = params.extraParams;
      delete params.extraParams;
      return new Promise((r, j) => {
        mysqlServer
          .getSequenceList(params)
          .then((res) => {
            const data = res?.map((item: any) => ({
              uuid: uuid(),
              key: item.name,
              name: item.name,
              treeNodeType: TreeNodeType.SEQUENCE,
              sequenceName: item.name,
              isLeaf: true,
              extraParams: {
                ..._extraParams,
                sequenceName: item.name,
              },
            }));
            r(data);
          })
          .catch((error) => {
            j(error);
          });
      });
    },
    operationColumn: [OperationColumn.CreateSequence, OperationColumn.CopyName, OperationColumn.Refresh],
  },
  [TreeNodeType.SEQUENCE]: {
    icon: '\ue611',
    operationColumn: [
      OperationColumn.OpenSequence,
      OperationColumn.EditSequence,
      OperationColumn.CopyName,
      OperationColumn.DeleteSequence,
    ],
  },
};

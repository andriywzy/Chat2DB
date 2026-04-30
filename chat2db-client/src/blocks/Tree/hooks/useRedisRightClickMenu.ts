import { ITreeNode } from '@/typings';
import { OperationColumn, TreeNodeType, WorkspaceTabType } from '@/constants';
import sqlService from '@/service/sql';

interface IAddWorkspaceTab {
  (params: {
    id: string;
    title: string;
    type: WorkspaceTabType;
    uniqueData: Record<string, any>;
  }): void;
}

const buildRedisReadSql = (quotedKey: string, keyType: string) => {
  const normalizedKeyType = (keyType || '').toLowerCase();

  if (normalizedKeyType === 'hash') return `hgetall ${quotedKey}`;
  if (normalizedKeyType === 'list') return `lrange ${quotedKey} 0 -1`;
  if (normalizedKeyType === 'set') return `smembers ${quotedKey}`;
  if (normalizedKeyType === 'zset') return `zrange ${quotedKey} 0 -1 withscores`;
  if (normalizedKeyType === 'stream') return `xrange ${quotedKey} - + count 200`;

  return `get ${quotedKey}`;
};

export const getRedisViewAllTableTitle = (treeNodeData: ITreeNode) => {
  const dataSourceName = treeNodeData.extraParams?.dataSourceName || 'Redis';
  const databaseName = treeNodeData.extraParams?.databaseName ?? '0';
  return `${dataSourceName} / DB ${databaseName} - ALL Data`;
};

export const getRedisViewAllTableId = (treeNodeData: ITreeNode) => {
  const { dataSourceId, databaseName, schemaName } = treeNodeData.extraParams!;
  return `${OperationColumn.ViewAllTable}-redis-${dataSourceId}-${databaseName || '0'}-${schemaName || 'default'}`;
};

export const openRedisKey = async (treeNodeData: ITreeNode, addWorkspaceTab: IAddWorkspaceTab) => {
  if (treeNodeData.treeNodeType !== TreeNodeType.KEY) {
    return;
  }

  const redisKey = (treeNodeData.name || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const quotedKey = `"${redisKey}"`;
  let keyType = 'string';

  try {
    const typeResult = await sqlService.executeSql({
      sql: `type ${quotedKey}`,
      pageNo: 1,
      pageSize: 200,
      dataSourceId: treeNodeData.extraParams!.dataSourceId!,
      databaseName: treeNodeData.extraParams?.databaseName,
      schemaName: treeNodeData.extraParams?.schemaName || null,
    });
    const firstResult = Array.isArray(typeResult) ? typeResult[0] : null;
    const row = firstResult?.dataList?.[0] || [];
    const rawType = row.find((v) => `${v || ''}`.trim().length > 0) || row[0];
    keyType = `${rawType || 'string'}`.trim().toLowerCase();
  } catch (error) {
    // ignore redis TYPE command fallback errors
  }

  addWorkspaceTab({
    id: `${OperationColumn.OpenTable}-${treeNodeData.uuid}`,
    title: treeNodeData.name,
    type: WorkspaceTabType.EditTableData,
    uniqueData: {
      dataSourceId: treeNodeData.extraParams!.dataSourceId!,
      databaseType: treeNodeData.extraParams!.databaseType!,
      databaseName: treeNodeData.extraParams?.databaseName,
      schemaName: treeNodeData.extraParams?.schemaName,
      tableName: treeNodeData.name,
      sql: buildRedisReadSql(quotedKey, keyType),
    },
  });
};

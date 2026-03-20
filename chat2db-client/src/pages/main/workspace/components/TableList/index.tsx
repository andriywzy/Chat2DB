import React, { memo, useEffect, useMemo, useState } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import i18n from '@/i18n';
import { v4 as uuid } from 'uuid';
import { Input, message } from 'antd';
import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';

import { getConnectionList, useConnectionStore } from '@/pages/main/store/connection';
import connectionService from '@/service/connection';

// ----- components -----
import OperationLine from '../OperationLine';
import Iconfont from '@/components/Iconfont';

import Tree from '@/blocks/Tree';
import { ITreeNode } from '@/typings';
import { TreeNodeType } from '@/constants';

interface IProps {
  className?: string;
}

export default memo<IProps>((props) => {
  const { className } = props;
  const { connectionList, groupList } = useConnectionStore((state) => ({
    connectionList: state.connectionList,
    groupList: state.groupList,
  }));
  const [searchValue, setSearchValue] = useState<string>('');
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({});
  const [editingGroupKey, setEditingGroupKey] = useState<string | null>(null);
  const [editingGroupName, setEditingGroupName] = useState('');
  const [draggingConnectionId, setDraggingConnectionId] = useState<number | null>(null);
  const [dropTargetGroupKey, setDropTargetGroupKey] = useState<string | null>(null);

  useEffect(() => {
    if (connectionList === null || groupList === null) {
      getConnectionList();
    }
  }, [connectionList, groupList]);

  const groupedTreeData = useMemo(() => {
    const groups =
      (groupList || []).map((group) => ({
        key: `group-${group.id}`,
        id: group.id,
        name: group.name,
        canManage: group.canManage,
        treeData: [] as ITreeNode[],
      })) || [];
    const groupMap = new Map(groups.map((group) => [group.key, group]));

    (connectionList || []).forEach((connection) => {
      const groupKey = connection.groupId ? `group-${connection.groupId}` : 'ungrouped';
      if (!groupMap.has(groupKey)) {
        groupMap.set(groupKey, {
          key: groupKey,
          id: connection.groupId,
          name: connection.groupName || i18n('workspace.database.ungrouped'),
          canManage: false,
          treeData: [],
        });
      }
      groupMap.get(groupKey)!.treeData.push({
        uuid: uuid(),
        key: connection.id,
        name: connection.alias,
        treeNodeType: TreeNodeType.DATA_SOURCE,
        extraParams: {
          dataSourceId: connection.id,
          dataSourceName: connection.alias,
          databaseType: connection.type,
          connectionDetail: connection,
        },
      });
    });

    return Array.from(groupMap.values()).filter((group) => group.treeData.length > 0 || group.canManage);
  }, [connectionList, groupList]);

  useEffect(() => {
    setExpandedGroups((prev) => {
      const next = { ...prev };
      groupedTreeData.forEach((group) => {
        if (next[group.key] === undefined) {
          next[group.key] = true;
        }
      });
      return next;
    });
  }, [groupedTreeData]);

  const handleRefresh = () => {
    return getConnectionList();
  };

  const buildNextGroupName = () => {
    const baseName = i18n('workspace.database.newGroup');
    const existingNames = new Set((groupList || []).map((group) => group.name));
    if (!existingNames.has(baseName)) {
      return baseName;
    }
    let index = 2;
    while (existingNames.has(`${baseName} ${index}`)) {
      index += 1;
    }
    return `${baseName} ${index}`;
  };

  const handleCreateGroup = async () => {
    const name = buildNextGroupName();
    await connectionService.createGroup({ name });
    await getConnectionList();
  };

  const startEditGroup = (group: { key: string; id?: number; name: string }) => {
    if (!group.id) {
      return;
    }
    setEditingGroupKey(group.key);
    setEditingGroupName(group.name);
  };

  const cancelEditGroup = () => {
    setEditingGroupKey(null);
    setEditingGroupName('');
  };

  const handleDeleteGroup = async (group: { id?: number }) => {
    if (!group.id) {
      return;
    }
    if (editingGroupKey) {
      cancelEditGroup();
    }
    await connectionService.deleteGroup({ id: group.id });
    message.success(i18n('common.message.deleteSuccessfully'));
    await getConnectionList();
  };

  const submitEditGroup = async (group: { id?: number; name: string }) => {
    const nextName = editingGroupName.trim();
    if (!group.id) {
      cancelEditGroup();
      return;
    }
    if (!nextName || nextName === group.name) {
      cancelEditGroup();
      return;
    }
    await connectionService.updateGroup({
      id: group.id,
      name: nextName,
    });
    message.success(i18n('common.message.modifySuccessfully'));
    cancelEditGroup();
    await getConnectionList();
  };

  const moveConnectionToGroup = async (connectionId: number, groupId?: number | null) => {
    const connection = (connectionList || []).find((item) => item.id === connectionId);
    const nextGroupId = groupId ?? null;
    if (!connection || (connection.groupId ?? null) === nextGroupId) {
      return;
    }
    const detail = await connectionService.getDetails({ id: connectionId });
    await connectionService.update({
      ...detail,
      groupId: nextGroupId as any,
    });
    await getConnectionList();
  };

  const handleConnectionDragStart = (node: ITreeNode, event: React.DragEvent<HTMLDivElement>) => {
    const connectionId = node.extraParams?.connectionDetail?.id;
    if (!connectionId) {
      return;
    }
    setDraggingConnectionId(connectionId);
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(connectionId));
  };

  const handleConnectionDragEnd = () => {
    setDraggingConnectionId(null);
    setDropTargetGroupKey(null);
  };

  const handleGroupDrop = async (group: { key: string; id?: number }) => {
    if (!draggingConnectionId) {
      return;
    }
    setDropTargetGroupKey(null);
    const targetGroupId = group.key === 'ungrouped' ? null : group.id;
    await moveConnectionToGroup(draggingConnectionId, targetGroupId);
    handleConnectionDragEnd();
  };

  return (
    <div className={classnames(styles.treeContainer, className)}>
      <OperationLine
        getTreeData={handleRefresh}
        searchValue={searchValue}
        setSearchValue={setSearchValue}
        onCreateGroup={handleCreateGroup}
      />
      {!groupedTreeData.length ? (
        <div className={styles.emptyState}>{i18n('workspace.tips.noConnection')}</div>
      ) : (
        groupedTreeData.map((group) => {
          const expanded = expandedGroups[group.key] !== false;
          const isEditing = editingGroupKey === group.key;
          return (
            <div key={group.key} className={styles.groupBlock}>
              <div
                className={classnames(styles.groupRow, {
                  [styles.groupRowDroppable]: draggingConnectionId,
                  [styles.groupRowDropActive]: dropTargetGroupKey === group.key,
                })}
                onClick={() => {
                  if (!isEditing) {
                    setExpandedGroups((prev) => ({ ...prev, [group.key]: !expanded }));
                  }
                }}
                onDragOver={(event) => {
                  if (!draggingConnectionId || isEditing) {
                    return;
                  }
                  event.preventDefault();
                  event.dataTransfer.dropEffect = 'move';
                  if (dropTargetGroupKey !== group.key) {
                    setDropTargetGroupKey(group.key);
                  }
                }}
                onDragLeave={() => {
                  if (dropTargetGroupKey === group.key) {
                    setDropTargetGroupKey(null);
                  }
                }}
                onDrop={(event) => {
                  if (!draggingConnectionId || isEditing) {
                    return;
                  }
                  event.preventDefault();
                  handleGroupDrop(group);
                }}
              >
                <div className={classnames(styles.groupArrow, { [styles.groupArrowExpanded]: expanded })}>
                  <Iconfont code="&#xe641;" />
                </div>
                <Iconfont className={styles.groupIcon} code="&#xe63f;" />
                {isEditing ? (
                  <div
                    className={styles.groupEditBox}
                    onClick={(event) => event.stopPropagation()}
                  >
                    <Input
                      autoFocus
                      size="small"
                      className={styles.groupEditInput}
                      value={editingGroupName}
                      onChange={(event) => setEditingGroupName(event.target.value)}
                      onPressEnter={() => submitEditGroup(group)}
                      onBlur={() => submitEditGroup(group)}
                    />
                    <div
                      className={styles.groupAction}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => submitEditGroup(group)}
                    >
                      <CheckOutlined />
                    </div>
                    <div
                      className={styles.groupAction}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={cancelEditGroup}
                    >
                      <CloseOutlined />
                    </div>
                  </div>
                ) : (
                  <>
                    <span className={styles.groupName} title={group.name}>
                      {group.name}
                    </span>
                    {group.id && group.canManage ? (
                      <>
                        <div
                          className={styles.groupAction}
                          title={i18n('common.button.delete')}
                          onClick={(event) => {
                            event.stopPropagation();
                            handleDeleteGroup(group);
                          }}
                        >
                          <DeleteOutlined />
                        </div>
                        <div
                          className={styles.groupAction}
                          title={i18n('common.button.edit')}
                          onClick={(event) => {
                            event.stopPropagation();
                            startEditGroup(group);
                          }}
                        >
                          <EditOutlined />
                        </div>
                      </>
                    ) : null}
                  </>
                )}
              </div>
              {expanded ? (
                <Tree
                  className={styles.treeBox}
                  searchValue={searchValue}
                  treeData={group.treeData}
                  getNodeDraggable={(node) => node.treeNodeType === TreeNodeType.DATA_SOURCE}
                  onNodeDragStart={handleConnectionDragStart}
                  onNodeDragEnd={handleConnectionDragEnd}
                />
              ) : null}
            </div>
          );
        })
      )}
    </div>
  );
});

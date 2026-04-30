import React, { memo, useEffect, useMemo, useState } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import i18n from '@/i18n';
import { v4 as uuid } from 'uuid';
import { Input, message } from 'antd';
import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';

import { getConnectionList, useConnectionStore } from '@/pages/main/store/connection';
import connectionService from '@/service/connection';

import OperationLine from '../OperationLine';
import Iconfont from '@/components/Iconfont';

import Tree from '@/blocks/Tree';
import { ITreeNode } from '@/typings';
import { TreeNodeType } from '@/constants';

interface IProps {
  className?: string;
}

interface IEnvironmentTreeBlock {
  key: string;
  id?: number;
  name: string;
  shortName?: string;
  color?: string;
  canManage?: boolean;
  treeData: ITreeNode[];
}

interface IProjectTreeBlock {
  key: string;
  id?: number;
  name: string;
  canManage?: boolean;
  environments: IEnvironmentTreeBlock[];
}

export default memo<IProps>((props) => {
  const { className } = props;
  const { connectionList, projectList, connectionEnvList } = useConnectionStore((state) => ({
    connectionList: state.connectionList,
    projectList: state.projectList,
    connectionEnvList: state.connectionEnvList,
  }));
  const [searchValue, setSearchValue] = useState<string>('');
  const [expandedProjects, setExpandedProjects] = useState<Record<string, boolean>>({});
  const [expandedEnvironments, setExpandedEnvironments] = useState<Record<string, boolean>>({});
  const [editingProjectKey, setEditingProjectKey] = useState<string | null>(null);
  const [editingProjectName, setEditingProjectName] = useState('');
  const [editingEnvironmentKey, setEditingEnvironmentKey] = useState<string | null>(null);
  const [editingEnvironmentName, setEditingEnvironmentName] = useState('');
  const [draggingConnectionId, setDraggingConnectionId] = useState<number | null>(null);
  const [dropTargetKey, setDropTargetKey] = useState<string | null>(null);

  useEffect(() => {
    if (connectionList === null || projectList === null || connectionEnvList === null) {
      getConnectionList();
    }
  }, [connectionEnvList, connectionList, projectList]);

  const groupedTreeData = useMemo<IProjectTreeBlock[]>(() => {
    const environmentList = connectionEnvList || [];
    const projects: IProjectTreeBlock[] =
      (projectList || []).map((project) => ({
        key: `project-${project.id}`,
        id: project.id,
        name: project.name,
        canManage: project.canManage,
        environments: [],
      })) || [];
    const projectMap = new Map<string, IProjectTreeBlock>(projects.map((project) => [project.key, project]));

    const ensureProject = (projectId?: number | null, projectName?: string | null) => {
      const projectKey = projectId ? `project-${projectId}` : 'unassigned-project';
      if (!projectMap.has(projectKey)) {
        projectMap.set(projectKey, {
          key: projectKey,
          id: projectId ?? undefined,
          name: projectName || i18n('workspace.database.unassignedProject'),
          canManage: false,
          environments: [],
        });
      }
      return projectMap.get(projectKey)!;
    };

    const ensureEnvironment = (project: IProjectTreeBlock, connection: any) => {
      const environment = connection.environment;
      const environmentId = environment?.id ?? connection.environmentId;
      const normalizedEnvironmentName =
        environment?.name?.trim?.()
        || environmentList.find((item) => item.id === environmentId)?.name?.trim?.()
        || i18n('workspace.database.unassignedEnvironment');
      const environmentKey = environmentId
        ? `${project.key}-environment-${environmentId}`
        : `${project.key}-environment-unassigned`;
      let targetEnvironment = project.environments.find((item) => item.key === environmentKey);
      if (!targetEnvironment) {
        const environmentMeta = environmentList.find((item) => item.id === environmentId);
        targetEnvironment = {
          key: environmentKey,
          id: environmentId,
          name: normalizedEnvironmentName,
          shortName: environment?.shortName || environmentMeta?.shortName,
          color: environment?.color || environmentMeta?.color,
          canManage: environmentMeta?.canManage,
          treeData: [],
        };
        project.environments.push(targetEnvironment);
      } else if (!targetEnvironment.name) {
        targetEnvironment.name = normalizedEnvironmentName;
      }
      return targetEnvironment;
    };

    environmentList.forEach((environment) => {
      const project = ensureProject(environment.projectId, projectMap.get(`project-${environment.projectId}`)?.name);
      const environmentKey = environment.id
        ? `${project.key}-environment-${environment.id}`
        : `${project.key}-environment-unassigned`;
      const existingEnvironment = project.environments.find((item) => item.key === environmentKey);
      if (!existingEnvironment) {
        project.environments.push({
          key: environmentKey,
          id: environment.id,
          name: environment.name || i18n('workspace.database.unassignedEnvironment'),
          shortName: environment.shortName,
          color: environment.color,
          canManage: environment.canManage,
          treeData: [],
        });
      }
    });

    (connectionList || []).forEach((connection) => {
      const projectId = connection.projectId;
      const projectName = connection.projectName;
      const project = ensureProject(projectId, projectName);
      const environment = ensureEnvironment(project, connection);
      environment.treeData.push({
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

    return Array.from(projectMap.values())
      .map((project) => ({
        ...project,
        environments: project.environments.sort((a, b) => (a.name || '').localeCompare(b.name || '')),
      }))
      .filter((project) => project.environments.length > 0 || project.canManage);
  }, [connectionEnvList, connectionList, projectList]);

  useEffect(() => {
    setExpandedProjects((prev) => {
      const next = { ...prev };
      groupedTreeData.forEach((project) => {
        if (next[project.key] === undefined) {
          next[project.key] = true;
        }
      });
      return next;
    });
    setExpandedEnvironments((prev) => {
      const next = { ...prev };
      groupedTreeData.forEach((project) => {
        project.environments.forEach((environment) => {
          if (next[environment.key] === undefined) {
            next[environment.key] = true;
          }
        });
      });
      return next;
    });
  }, [groupedTreeData]);

  const handleRefresh = () => getConnectionList();

  const buildNextProjectName = () => {
    const baseName = i18n('workspace.database.newProject');
    const existingNames = new Set((projectList || []).map((project) => project.name));
    if (!existingNames.has(baseName)) {
      return baseName;
    }
    let index = 2;
    while (existingNames.has(`${baseName} ${index}`)) {
      index += 1;
    }
    return `${baseName} ${index}`;
  };

  const buildNextEnvironmentName = () => {
    const baseName = i18n('workspace.database.newEnvironment');
    const existingNames = new Set((connectionEnvList || []).map((environment) => environment.name));
    if (!existingNames.has(baseName)) {
      return baseName;
    }
    let index = 2;
    while (existingNames.has(`${baseName} ${index}`)) {
      index += 1;
    }
    return `${baseName} ${index}`;
  };

  const handleCreateProject = async () => {
    const name = buildNextProjectName();
    await connectionService.createProject({ name });
    await getConnectionList();
  };

  const handleCreateEnvironment = async (project?: IProjectTreeBlock) => {
    const name = buildNextEnvironmentName();
    const targetProject = project || groupedTreeData.find((item) => item.id && item.canManage) || groupedTreeData.find((item) => item.id);
    if (!targetProject?.id) {
      message.warning(i18n('workspace.database.createEnvironmentNeedProject'));
      return;
    }
    await connectionService.createEnvironment({
      name,
      shortName: name.slice(0, 8).toUpperCase(),
      color: 'BLUE',
      projectId: targetProject.id,
    });
    message.success(i18n('common.tips.saveSuccessfully'));
    await getConnectionList();
  };

  const startEditProject = (project: IProjectTreeBlock) => {
    if (!project.id) {
      return;
    }
    setEditingProjectKey(project.key);
    setEditingProjectName(project.name);
  };

  const startEditEnvironment = (environment: IEnvironmentTreeBlock) => {
    if (!environment.id) {
      return;
    }
    setEditingEnvironmentKey(environment.key);
    setEditingEnvironmentName(environment.name);
  };

  const cancelEditProject = () => {
    setEditingProjectKey(null);
    setEditingProjectName('');
  };

  const cancelEditEnvironment = () => {
    setEditingEnvironmentKey(null);
    setEditingEnvironmentName('');
  };

  const handleDeleteProject = async (project: IProjectTreeBlock) => {
    if (!project.id) {
      return;
    }
    cancelEditProject();
    await connectionService.deleteProject({ id: project.id });
    message.success(i18n('common.message.deleteSuccessfully'));
    await getConnectionList();
  };

  const handleDeleteEnvironment = async (environment: IEnvironmentTreeBlock) => {
    if (!environment.id) {
      return;
    }
    cancelEditEnvironment();
    await connectionService.deleteEnvironment({ id: environment.id });
    message.success(i18n('common.message.deleteSuccessfully'));
    await getConnectionList();
  };

  const submitEditProject = async (project: IProjectTreeBlock) => {
    const nextName = editingProjectName.trim();
    if (!project.id) {
      cancelEditProject();
      return;
    }
    if (!nextName || nextName === project.name) {
      cancelEditProject();
      return;
    }
    await connectionService.updateProject({
      id: project.id,
      name: nextName,
    });
    message.success(i18n('common.message.modifySuccessfully'));
    cancelEditProject();
    await getConnectionList();
  };

  const submitEditEnvironment = async (environment: IEnvironmentTreeBlock) => {
    const nextName = editingEnvironmentName.trim();
    if (!environment.id) {
      cancelEditEnvironment();
      return;
    }
    if (!nextName || nextName === environment.name) {
      cancelEditEnvironment();
      return;
    }
    await connectionService.updateEnvironment({
      id: environment.id,
      name: nextName,
      shortName: nextName.slice(0, 8).toUpperCase(),
      color: environment.color || 'BLUE',
      projectId: (connectionEnvList || []).find((item) => item.id === environment.id)?.projectId,
    });
    message.success(i18n('common.message.modifySuccessfully'));
    cancelEditEnvironment();
    await getConnectionList();
  };

  const moveConnection = async (
    connectionId: number,
    options: { projectId?: number | null; environmentId?: number | null },
  ) => {
    const connection = (connectionList || []).find((item) => item.id === connectionId);
    const currentProjectId = connection?.projectId ?? null;
    const currentEnvironmentId = connection?.environment?.id ?? connection?.environmentId ?? null;
    const nextProjectId = options.projectId ?? currentProjectId;
    const nextEnvironmentId = options.environmentId ?? currentEnvironmentId;
    if (!connection || (currentProjectId === nextProjectId && currentEnvironmentId === nextEnvironmentId)) {
      return;
    }
    const detail = await connectionService.getDetails({ id: connectionId });
    await connectionService.update({
      ...detail,
      projectId: nextProjectId as any,
      environmentId: nextEnvironmentId as any,
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
    setDropTargetKey(null);
  };

  const handleEnvironmentDrop = async (project: IProjectTreeBlock, environment: IEnvironmentTreeBlock) => {
    if (!draggingConnectionId) {
      return;
    }
    setDropTargetKey(null);
    await moveConnection(draggingConnectionId, {
      projectId: project.key === 'unassigned-project' ? null : project.id,
      environmentId: environment.id ?? null,
    });
    handleConnectionDragEnd();
  };

  const renderEditableTitle = (
    isEditing: boolean,
    value: string,
    onChange: (value: string) => void,
    onSubmit: () => void,
    onCancel: () => void,
  ) => {
    if (!isEditing) {
      return null;
    }
    return (
      <div className={styles.groupEditBox} onClick={(event) => event.stopPropagation()}>
        <Input
          autoFocus
          size="small"
          className={styles.groupEditInput}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onPressEnter={onSubmit}
          onBlur={onSubmit}
        />
        <div className={styles.groupAction} onMouseDown={(event) => event.preventDefault()} onClick={onSubmit}>
          <CheckOutlined />
        </div>
        <div className={styles.groupAction} onMouseDown={(event) => event.preventDefault()} onClick={onCancel}>
          <CloseOutlined />
        </div>
      </div>
    );
  };

  return (
    <div className={classnames(styles.treeContainer, className)}>
      <OperationLine
        getTreeData={handleRefresh}
        searchValue={searchValue}
        setSearchValue={setSearchValue}
        onCreateProject={handleCreateProject}
      />
      {!groupedTreeData.length ? (
        <div className={styles.emptyState}>{i18n('workspace.tips.noConnection')}</div>
      ) : (
        groupedTreeData.map((project) => {
          const projectExpanded = expandedProjects[project.key] !== false;
          const isProjectEditing = editingProjectKey === project.key;
          return (
            <div key={project.key} className={styles.groupBlock}>
              <div
                className={styles.groupRow}
                onClick={() => {
                  if (!isProjectEditing) {
                    setExpandedProjects((prev) => ({ ...prev, [project.key]: !projectExpanded }));
                  }
                }}
              >
                <div className={classnames(styles.groupArrow, { [styles.groupArrowExpanded]: projectExpanded })}>
                  <Iconfont code="&#xe641;" />
                </div>
                <Iconfont className={styles.groupIcon} code="&#xe63f;" />
                {renderEditableTitle(
                  isProjectEditing,
                  editingProjectName,
                  setEditingProjectName,
                  () => submitEditProject(project),
                  cancelEditProject,
                ) || (
                  <>
                    <span className={styles.treeNodeName} title={project.name}>
                      {project.name}
                    </span>
                    {project.id && project.canManage ? (
                      <>
                        <div
                          className={styles.groupAction}
                          title={i18n('workspace.database.newEnvironment')}
                          onClick={(event) => {
                            event.stopPropagation();
                            handleCreateEnvironment(project);
                          }}
                        >
                          <PlusOutlined />
                        </div>
                        <div
                          className={styles.groupAction}
                          title={i18n('common.button.delete')}
                          onClick={(event) => {
                            event.stopPropagation();
                            handleDeleteProject(project);
                          }}
                        >
                          <DeleteOutlined />
                        </div>
                        <div
                          className={styles.groupAction}
                          title={i18n('common.button.edit')}
                          onClick={(event) => {
                            event.stopPropagation();
                            startEditProject(project);
                          }}
                        >
                          <EditOutlined />
                        </div>
                      </>
                    ) : null}
                  </>
                )}
              </div>
              {projectExpanded
                ? project.environments.map((environment) => {
                    const environmentExpanded = expandedEnvironments[environment.key] !== false;
                    const isEnvironmentEditing = editingEnvironmentKey === environment.key;
                    return (
                      <div key={environment.key} className={styles.environmentBlock}>
                        <div
                          className={classnames(styles.environmentRow, {
                            [styles.groupRowDroppable]: draggingConnectionId,
                            [styles.groupRowDropActive]: dropTargetKey === environment.key,
                          })}
                          onClick={() => {
                            if (!isEnvironmentEditing) {
                              setExpandedEnvironments((prev) => ({
                                ...prev,
                                [environment.key]: !environmentExpanded,
                              }));
                            }
                          }}
                          onDragOver={(event) => {
                            if (!draggingConnectionId || isEnvironmentEditing) {
                              return;
                            }
                            event.preventDefault();
                            event.dataTransfer.dropEffect = 'move';
                            if (dropTargetKey !== environment.key) {
                              setDropTargetKey(environment.key);
                            }
                          }}
                          onDragLeave={() => {
                            if (dropTargetKey === environment.key) {
                              setDropTargetKey(null);
                            }
                          }}
                          onDrop={(event) => {
                            if (!draggingConnectionId || isEnvironmentEditing) {
                              return;
                            }
                            event.preventDefault();
                            handleEnvironmentDrop(project, environment);
                          }}
                        >
                          <div
                            className={classnames(styles.groupArrow, {
                              [styles.groupArrowExpanded]: environmentExpanded,
                            })}
                          >
                            <Iconfont code="&#xe641;" />
                          </div>
                          <span
                            className={styles.environmentDot}
                            style={{ backgroundColor: environment.color || 'var(--color-primary)' }}
                          />
                          {renderEditableTitle(
                            isEnvironmentEditing,
                            editingEnvironmentName,
                            setEditingEnvironmentName,
                            () => submitEditEnvironment(environment),
                            cancelEditEnvironment,
                          ) || (
                            <>
                              <span className={styles.treeNodeName} title={environment.name}>
                                {environment.name}
                              </span>
                              {environment.id && environment.canManage ? (
                                <>
                                  <div
                                    className={styles.groupAction}
                                    title={i18n('common.button.delete')}
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      handleDeleteEnvironment(environment);
                                    }}
                                  >
                                    <DeleteOutlined />
                                  </div>
                                  <div
                                    className={styles.groupAction}
                                    title={i18n('common.button.edit')}
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      startEditEnvironment(environment);
                                    }}
                                  >
                                    <EditOutlined />
                                  </div>
                                </>
                              ) : null}
                            </>
                          )}
                        </div>
                        {environmentExpanded ? (
                          <Tree
                            className={styles.treeBox}
                            searchValue={searchValue}
                            treeData={environment.treeData}
                            getNodeDraggable={(node) => node.treeNodeType === TreeNodeType.DATA_SOURCE}
                            onNodeDragStart={handleConnectionDragStart}
                            onNodeDragEnd={handleConnectionDragEnd}
                          />
                        ) : null}
                      </div>
                    );
                  })
                : null}
            </div>
          );
        })
      )}
    </div>
  );
});

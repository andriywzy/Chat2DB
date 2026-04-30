import { TreeNodeType, isRedisWorkspace } from '@/constants';
import { ITreeNode } from '@/typings';

import { baseTreeConfig } from './treeConfigBase';
import { redisTreeConfig } from './redisTreeConfig';
import { sqlTreeConfig } from './sqlTreeConfig';
import { ITreeConfigItem } from './treeConfigTypes';

export type { ITreeConfig, ITreeConfigItem } from './treeConfigTypes';

export const switchIcon: Partial<{ [key in TreeNodeType]: { icon: string; unfoldIcon?: string } }> = {
  [TreeNodeType.DATABASE]: {
    icon: '\ue669',
  },
  [TreeNodeType.SCHEMAS]: {
    icon: '\ue696',
  },
  [TreeNodeType.TABLE]: {
    icon: '\ue63e',
  },
  [TreeNodeType.TABLES]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.COLUMNS]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.COLUMN]: {
    icon: '\ue611',
  },
  [TreeNodeType.KEYS]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.KEY]: {
    icon: '\ue775',
  },
  [TreeNodeType.INDEXES]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.INDEX]: {
    icon: '\ue65b',
  },
  [TreeNodeType.VIEWS]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.VIEW]: {
    icon: '\ue70c',
  },
  [TreeNodeType.FUNCTION]: {
    icon: '\ue76a',
  },
  [TreeNodeType.PROCEDURE]: {
    icon: '\ue73c',
  },
  [TreeNodeType.TRIGGER]: {
    icon: '\ue64a',
  },
  [TreeNodeType.VIEWCOLUMNS]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.VIEWCOLUMN]: {
    icon: '\ue647',
  },
  [TreeNodeType.FUNCTIONS]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.PROCEDURES]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.TRIGGERS]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.SEQUENCES]: {
    icon: '\ueabe',
    unfoldIcon: '\ueabf',
  },
  [TreeNodeType.SEQUENCE]: {
    icon: '\ue611',
  },
};

export const treeConfig = {
  ...baseTreeConfig,
  ...sqlTreeConfig,
};

export const getTreeConfigItem = (
  treeNodeData: Pick<ITreeNode, 'treeNodeType' | 'pretendNodeType' | 'extraParams'>,
): ITreeConfigItem => {
  const treeNodeType = treeNodeData.pretendNodeType || treeNodeData.treeNodeType;
  const workspaceTreeConfig = isRedisWorkspace(treeNodeData.extraParams?.databaseType)
    ? redisTreeConfig
    : sqlTreeConfig;

  return workspaceTreeConfig[treeNodeType] || baseTreeConfig[treeNodeType] || treeConfig[treeNodeType] || {};
};

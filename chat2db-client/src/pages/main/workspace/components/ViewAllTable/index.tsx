import React, { memo } from 'react';

import { getDatabaseWorkspaceKind } from '@/constants';
import RedisBrowser from '../RedisWorkspace/RedisBrowser';
import SqlTableList from '../SqlWorkspace/SqlTableList';
import { IViewAllTableProps } from './types';

const ViewAllTable = memo((props: IViewAllTableProps) => {
  const { uniqueData } = props;
  const workspaceKind = getDatabaseWorkspaceKind(uniqueData.databaseType);

  if (workspaceKind === 'redis') {
    return <RedisBrowser {...props} />;
  }

  return <SqlTableList {...props} />;
});

export default ViewAllTable;

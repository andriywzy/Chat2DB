import { DatabaseTypeCode } from './common';

export type DatabaseWorkspaceKind = 'sql' | 'redis';

export const getDatabaseWorkspaceKind = (databaseType?: DatabaseTypeCode | string): DatabaseWorkspaceKind => {
  if (databaseType === DatabaseTypeCode.REDIS) {
    return 'redis';
  }

  return 'sql';
};

export const isRedisWorkspace = (databaseType?: DatabaseTypeCode | string) =>
  getDatabaseWorkspaceKind(databaseType) === 'redis';

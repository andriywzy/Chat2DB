import { UseBoundStoreWithEqualityFn, createWithEqualityFn } from 'zustand/traditional';
import { devtools } from 'zustand/middleware';
import { shallow } from 'zustand/shallow';
import { StoreApi } from 'zustand';

import { IConnectionListItem, IConnectionEnv, IConnectionGroupItem } from '@/typings/connection';
import connectionService from '@/service/connection';

import { setCurrentConnectionDetails } from '@/pages/main/workspace/store/common';
import { useWorkspaceStore } from '@/pages/main/workspace/store';

export interface IConnectionStore {
  connectionList: IConnectionListItem[] | null;
  connectionEnvList: IConnectionEnv[] | null;
  groupList: IConnectionGroupItem[] | null;
  connectionManageActiveId: number | null;
}

export const initConnectionStore = {
  connectionList: null,
  connectionEnvList: null,
  groupList: null,
  connectionManageActiveId: null,
};

export const useConnectionStore: UseBoundStoreWithEqualityFn<StoreApi<IConnectionStore>> = createWithEqualityFn(
  devtools(() => initConnectionStore),
  shallow,
);

export const setConnectionList = (connectionList: IConnectionListItem[]) => {
  return useConnectionStore.setState({ connectionList });
};

export const setConnectionEnvList = (connectionEnvList: IConnectionEnv[]) => {
  return useConnectionStore.setState({ connectionEnvList });
};

export const setGroupList = (groupList: IConnectionGroupItem[]) => {
  return useConnectionStore.setState({ groupList });
};

export const setConnectionManageActiveId = (connectionManageActiveId: number | null) => {
  return useConnectionStore.setState({ connectionManageActiveId });
};

export const getConnectionList: () => Promise<IConnectionListItem[]> = () => {
  return new Promise((resolve, reject) => {
    const currentConnectionDetails = useWorkspaceStore.getState().currentConnectionDetails;
    Promise.all([
      connectionService.getList({
        pageNo: 1,
        pageSize: 1000,
        refresh: true,
      }),
      connectionService.getGroupList(),
    ])
      .then(([listRes, groupList]) => {
        const connectionList = listRes?.data || [];
        useConnectionStore.setState({ connectionList, groupList: groupList || [] });
        resolve(connectionList);

        if (connectionList.length === 0) {
          setCurrentConnectionDetails(null);
          return;
        }

        if (!currentConnectionDetails?.id) {
          setCurrentConnectionDetails(connectionList[0]);
          return;
        }

        const currentConnection = connectionList.find((item) => item.id === currentConnectionDetails?.id);
        if (!currentConnection) {
          setCurrentConnectionDetails(connectionList[0]);
        }
      })
      .catch(() => {
        useConnectionStore.setState({ connectionList: [], groupList: [] });
        reject([]);
      });
  });
};

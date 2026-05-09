import { Button, Drawer, Input, message, Popconfirm, Table, Tag } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { SearchOutlined, PlusOutlined } from '@ant-design/icons';
import {
  AffiliationType,
  ITeamWithProjectVO,
  ITeamVO,
  ITeamWithUserVO,
  IUserVO,
  IUserWithTeamVO,
  SearchType,
} from '@/typings/team';
import {
  deleteProjectFromTeam,
  deleteTeamListFromUser,
  deleteUserFromTeam,
  getProjectListFromTeam,
  getTeamListFromUser,
  getUserListFromTeam,
  updateProjectListFromTeam,
  updateTeamListFromUser,
  updateUserListFromTeam,
} from '@/service/team';

import UniversalAddModal from '../universal-add-modal';
import styles from './index.less';
import { ColumnsType } from 'antd/es/table';
import i18n from '@/i18n';
import { isNumber } from 'lodash';

interface IProps {
  type?: AffiliationType;
  open: boolean;
  onClose: () => void;
  byId?: number;
}

interface IModalInfo {
  open: boolean;
  type?: SearchType;
  initialValues?: {
    projectId?: number;
    permissionType?: 'VIEW' | 'TEAM_ADMIN';
    environmentIdList?: number[];
  };
}

interface IAffiliationDetail {
  type: AffiliationType;
  searchType: SearchType;
  title: string;
  byIdKey: string;
  columns: ColumnsType<any>;
  queryListApi: (params: any) => Promise<any>;
  updateListApi: (params: any) => Promise<any>;
  deleteApi: (params: { id: number }) => Promise<any>;
}

function UniversalDrawer(props: IProps) {
  const { type, open } = props;
  const [rows, setRows] = useState<Array<IUserVO | ITeamVO | ITeamWithProjectVO>>([]);
  const [modalInfo, setModalInfo] = useState<IModalInfo>({
    open: false,
  });
  const [searchInput, setSearchInput] = useState('');

  const [pagination, setPagination] = useState({
    searchKey: '',
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
  });
  const [total, setTotal] = useState(0);

  const managementMap: Record<AffiliationType, IAffiliationDetail> = useMemo(
    () => ({
      [AffiliationType.USER_TEAM]: {
        type: AffiliationType.USER_TEAM,
        searchType: SearchType.TEAM,
        title: i18n('team.team.name'),
        byIdKey: 'userId',
        queryListApi: getTeamListFromUser,
        updateListApi: updateTeamListFromUser,
        deleteApi: deleteTeamListFromUser,
        columns: [
          {
            title: i18n('team.team.addForm.code'),
            dataIndex: ['team', 'code'],
            key: 'team.code',
          },
          {
            title: i18n('team.team.addForm.name'),
            dataIndex: ['team', 'name'],
            key: 'team.name',
          },
          {
            title: i18n('common.text.action'),
            key: 'action',
            width: 100,
            render: (_: any, record: IUserWithTeamVO) => (
              <Popconfirm
                title={i18n('common.tips.delete.confirm')}
                okText={i18n('common.button.affirm')}
                cancelText={i18n('common.button.cancel')}
                onConfirm={async () => {
                  if (record.id !== undefined) {
                    await deleteTeamListFromUser({ id: record.id });
                    message.success(i18n('common.text.successfullyDelete'));
                    queryTableList();
                  }
                }}
              >
                <a href="#" onClick={(e) => e.preventDefault()}>
                  {i18n('common.button.delete')}
                </a>
              </Popconfirm>
            ),
          },
        ],
      },
      [AffiliationType.TEAM_USER]: {
        type: AffiliationType.TEAM_USER,
        searchType: SearchType.USER,
        title: i18n('team.user.name'),
        byIdKey: 'teamId',
        queryListApi: getUserListFromTeam,
        updateListApi: updateUserListFromTeam,
        deleteApi: deleteUserFromTeam,
        columns: [
          {
            title: i18n('team.user.addForm.userName'),
            dataIndex: ['user', 'userName'],
            key: 'user.userName',
          },
          {
            title: i18n('team.user.addForm.nickName'),
            dataIndex: ['user', 'nickName'],
            key: 'user.nickName',
          },
          {
            title: i18n('common.text.action'),
            key: 'action',
            width: 100,
            render: (_: any, record: ITeamWithUserVO) => (
              <Popconfirm
                title={i18n('common.tips.delete.confirm')}
                okText={i18n('common.button.affirm')}
                cancelText={i18n('common.button.cancel')}
                onConfirm={async () => {
                  if (record.id !== undefined) {
                    await deleteUserFromTeam({ id: record.id });
                    message.success(i18n('common.text.successfullyDelete'));
                    queryTableList();
                  }
                }}
              >
                <a href="#" onClick={(e) => e.preventDefault()}>
                  {i18n('common.button.delete')}
                </a>
              </Popconfirm>
            ),
          },
        ],
      },
      [AffiliationType.TEAM_PROJECT]: {
        type: AffiliationType.TEAM_PROJECT,
        searchType: SearchType.PROJECT,
        title: i18n('team.action.affiliation.project'),
        byIdKey: 'teamId',
        queryListApi: getProjectListFromTeam,
        updateListApi: updateProjectListFromTeam,
        deleteApi: deleteProjectFromTeam,
        columns: [
          {
            title: i18n('team.project.name'),
            dataIndex: ['project', 'name'],
            key: 'project.name',
          },
          {
            title: i18n('team.project.permission'),
            dataIndex: 'permissionType',
            key: 'permissionType',
            render: (permissionType: ITeamWithProjectVO['permissionType']) => (
              <Tag color={permissionType === 'TEAM_ADMIN' ? 'processing' : 'default'}>
                {permissionType === 'TEAM_ADMIN'
                  ? i18n('team.project.permission.teamAdmin')
                  : i18n('team.project.permission.view')}
              </Tag>
            ),
          },
          {
            title: i18n('team.project.environments'),
            dataIndex: 'environmentList',
            key: 'environmentList',
            render: (environmentList: ITeamWithProjectVO['environmentList']) => {
              if (!environmentList?.length) {
                return <Tag>{i18n('team.project.environments.all')}</Tag>;
              }
              return (
                <>
                  {environmentList.map((environment) => (
                    <Tag key={environment.id} color={environment.color?.toLowerCase() || 'blue'}>
                      {environment.name}
                    </Tag>
                  ))}
                </>
              );
            },
          },
          {
            title: i18n('team.project.description'),
            dataIndex: ['project', 'description'],
            key: 'project.description',
          },
          {
            title: i18n('common.text.action'),
            key: 'action',
            width: 160,
            render: (_: any, record: ITeamWithProjectVO) => (
              <>
                <Button
                  type="link"
                  onClick={() =>
                    setModalInfo({
                      open: true,
                      type: SearchType.PROJECT,
                      initialValues: {
                        projectId: record.project?.id,
                        permissionType: record.permissionType,
                        environmentIdList: record.environmentList?.map((environment) => environment.id!).filter(Boolean),
                      },
                    })
                  }
                >
                  {i18n('common.button.edit')}
                </Button>
                <Popconfirm
                  title={i18n('common.tips.delete.confirm')}
                  okText={i18n('common.button.affirm')}
                  cancelText={i18n('common.button.cancel')}
                  onConfirm={async () => {
                    if (record.id !== undefined) {
                      await deleteProjectFromTeam({ id: record.id });
                      message.success(i18n('common.text.successfullyDelete'));
                      queryTableList();
                    }
                  }}
                >
                  <a href="#" onClick={(e) => e.preventDefault()}>
                    {i18n('common.button.delete')}
                  </a>
                </Popconfirm>
              </>
            ),
          },
        ],
      },
    }),
    [props.byId, type],
  );

  const managementDataByType = type ? managementMap[type] : null;

  const searchPlaceholder = useMemo(() => {
    switch (managementDataByType?.searchType) {
      case SearchType.PROJECT:
        return i18n('team.action.addProject.placeholder');
      case SearchType.USER:
        return i18n('team.action.addUser.placeholder');
      case SearchType.TEAM:
        return i18n('team.action.addTeam.placeholder');
      default:
        return i18n('team.input.search.placeholder');
    }
  }, [managementDataByType?.searchType]);

  const addButtonLabel = useMemo(() => {
    switch (managementDataByType?.searchType) {
      case SearchType.PROJECT:
        return i18n('team.action.addProject');
      case SearchType.USER:
        return i18n('team.action.addUser');
      case SearchType.TEAM:
        return i18n('team.action.addTeam');
      default:
        return i18n('common.button.add');
    }
  }, [managementDataByType?.searchType]);

  useEffect(() => {
    if (!open) {
      return;
    }
    setSearchInput('');
    setPagination({
      searchKey: '',
      current: 1,
      pageSize: 10,
      total: 0,
      showSizeChanger: true,
      showQuickJumper: true,
    });
    setTotal(0);
    setModalInfo({
      open: false,
      type: managementDataByType?.searchType,
      initialValues: undefined,
    });
  }, [props.byId, type, open]);

  useEffect(() => {
    queryTableList();
  }, [pagination]);

  const queryTableList = async (searchKey?: string) => {
    const { current: pageNo, pageSize } = pagination;
    const requestApi = managementDataByType?.queryListApi;
    if (!requestApi || !isNumber(props.byId)) {
      return;
    }
    const res = await requestApi({
      searchKey: searchKey || pagination.searchKey,
      pageNo,
      pageSize,
      [managementDataByType?.byIdKey]: props.byId,
    });
    if (res) {
      setRows(res?.data ?? []);
      setTotal(res?.total ?? 0);
    }
  };

  const handleSearch = (searchKey: string) => {
    setPagination({
      ...pagination,
      searchKey,
    });
  };

  const handleTableChange = (p: any) => {
    setPagination({
      ...pagination,
      ...p,
    });
  };

  if (!managementDataByType) {
    return;
  }

  return (
    <Drawer open={open} width={720} title={managementDataByType?.title} onClose={props.onClose}>
      <div className={styles.tableTop}>
        <Input.Search
          style={{ width: '200px' }}
          placeholder={searchPlaceholder}
          value={searchInput}
          onChange={(v) => setSearchInput(v.target.value)}
          onSearch={handleSearch}
          enterButton={<SearchOutlined />}
        />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setModalInfo({
              ...modalInfo,
              open: true,
              type: managementDataByType.searchType,
              initialValues: undefined,
            });
          }}
        >
          {addButtonLabel}
        </Button>
      </div>
      <Table
        rowKey={'id'}
        pagination={{
          ...pagination,
          total,
        }}
        columns={managementDataByType?.columns}
        dataSource={rows}
        onChange={handleTableChange}
      />

      <UniversalAddModal
        {...modalInfo}
        onConfirm={(values) => {
          managementDataByType.updateListApi({ [managementDataByType.byIdKey]: props.byId, ...values }).then(() => {
            message.success(i18n('common.tips.updateSuccess'));
            queryTableList();
          });
        }}
        onClose={() => {
          setModalInfo({
            ...modalInfo,
            open: false,
          });
        }}
      />
    </Drawer>
  );
}

export default UniversalDrawer;

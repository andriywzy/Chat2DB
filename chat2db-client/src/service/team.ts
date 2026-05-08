import createRequest from './base';
import { IPageParams, IPageResponse } from '@/typings';
import { IAuditQuery, IAuditRecord, IProjectVO, ITeamProjectGrantPayload, ITeamVO, ITeamWithProjectVO, ITeamWithUserVO, IUserVO, IUserWithTeamVO } from '@/typings/team';
// ====================== User ======================

/** 用户-用户管理列表查询 */
const getUserManagementList = createRequest<IPageParams, IPageResponse<IUserVO>>('/api/admin/user/page', {
  method: 'get',
});

/** 创建用户 */
const createUser = createRequest<IUserVO, number>('/api/admin/user/create', {
  method: 'post',
});
/** 更新用户 */
const updateUser = createRequest<IUserVO, number>('/api/admin/user/update', {
  method: 'post',
});
/** 删除用户 */
const deleteUser = createRequest<{ id: number }, boolean>('/api/admin/user/:id', {
  method: 'delete',
});

/** 用户-用户管理中获取所属团队列表 */
const getTeamListFromUser = createRequest<IPageParams & { userId: number }, IPageResponse<IUserWithTeamVO>>(
  '/api/admin/user/team/page',
  {
    method: 'get',
  },
);
/** 用户-用户管理中更新所属团队 */
const updateTeamListFromUser = createRequest<{ userId: number; teamIdList: number[] }, number>(
  '/api/admin/user/team/batch_create',
  {
    method: 'post',
  }
);

/** 用户-用户管理中删除所属团队 */
const deleteTeamListFromUser = createRequest<{ id: number }, boolean>('/api/admin/user/team/:id', {
  method: 'delete',
});

// ======================== 团队 ======================

/** 团队-团队管理列表查询 */
const getTeamManagementList = createRequest<IPageParams, IPageResponse<ITeamVO>>('/api/admin/team/page', {
  method: 'get',
});

/** 团队-创建团队 */
const createTeam = createRequest<ITeamVO, number>('/api/admin/team/create', {
  method: 'post',
});
/** 团队-更新团队 */
const updateTeam = createRequest<ITeamVO, number>('/api/admin/team/update', {
  method: 'post',
});
/** 团队-删除团队 */
const deleteTeam = createRequest<{ id: number }, boolean>('/api/admin/team/:id', {
  method: 'delete',
});

/** 团队-团队管理中获取包含用户列表 */
const getUserListFromTeam = createRequest<IPageParams & { teamId: number }, IPageResponse<ITeamWithUserVO>>(
  '/api/admin/team/user/page',
  {
    method: 'get',
  },
);
/** 团队-团队管理中更新包含用户列表 */
const updateUserListFromTeam = createRequest<{ teamId: number; userIdList: number[] }, number>(
  '/api/admin/team/user/batch_create',
  {
    method: 'post',
  },
);
/** 团队-团队管理中删除包含用户列表 */
const deleteUserFromTeam = createRequest<{ id: number }, boolean>('/api/admin/team/user/:id', {
  method: 'delete',
});

/** 团队-团队管理中获取归属项目列表 */
const getProjectListFromTeam = createRequest<
  IPageParams & { teamId: number },
  IPageResponse<ITeamWithProjectVO>
>('/api/admin/team/project/page', {
  method: 'get',
});

/** 团队-团队管理中更新归属项目 */
const updateProjectListFromTeam = createRequest<
  { teamId: number; projectIdList?: number[]; projectGrantList?: ITeamProjectGrantPayload[] }, number
>('/api/admin/team/project/batch_create', {
  method: 'post',
});

/** 团队-团队管理中删除归属项目 */
const deleteProjectFromTeam = createRequest<{ id: number }, boolean>('/api/admin/team/project/:id', {
  method: 'delete',
});

// ======================= 通用列表 =====================
/** 通用-获取user列表 */
const getCommonUserList = createRequest<{ searchKey: string }, IUserVO[]>('/api/admin/common/user/list', {
  method: 'get',
});
/** 通用-获取team列表 */
const getCommonTeamList = createRequest<{ searchKey: string }, ITeamVO[]>('/api/admin/common/team/list', {
  method: 'get',
});
/** 通用-获取Project列表 */
const getCommonProjectList = createRequest<{ searchKey: string }, IProjectVO[]>(
  '/api/admin/common/project/list',
  {
    method: 'get',
  },
);

const getAuditList = createRequest<IAuditQuery, IPageResponse<IAuditRecord>>('/api/admin/audit/page', {
  method: 'get',
});

const getAuditDetail = createRequest<{ id: string }, IAuditRecord>('/api/admin/audit/:id', {
  method: 'get',
});

export {
  // user
  getUserManagementList,
  createUser,
  updateUser,
  deleteUser,
  getTeamListFromUser,
  updateTeamListFromUser,
  deleteTeamListFromUser,
  // team
  getTeamManagementList,
  createTeam,
  updateTeam,
  deleteTeam,
  getUserListFromTeam,
  updateUserListFromTeam,
  deleteUserFromTeam,
  getProjectListFromTeam,
  updateProjectListFromTeam,
  deleteProjectFromTeam,
  // common
  getCommonUserList,
  getCommonTeamList,
  getCommonProjectList,
  // audit
  getAuditList,
  getAuditDetail,
};

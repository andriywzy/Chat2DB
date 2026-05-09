import { IPageParams } from '@/typings';

// ===================== Common ==================
export enum ManagementType {
  DATASOURCE = 'DATASOURCE',
  PROJECT = 'PROJECT',
  TEAM = 'TEAM',
  USER = 'USER',
}

export enum AuditCategory {
  CONSOLE = 'CONSOLE',
  DATABASE = 'DATABASE',
}

export enum AuditActionType {
  CREATE = 'CREATE',
  UPDATE = 'UPDATE',
  DELETE = 'DELETE',
  GRANT = 'GRANT',
  EXECUTE = 'EXECUTE',
}

export enum AuditResourceType {
  USER = 'USER',
  TEAM = 'TEAM',
  PROJECT = 'PROJECT',
  ENVIRONMENT = 'ENVIRONMENT',
  DATASOURCE = 'DATASOURCE',
  TEAM_USER = 'TEAM_USER',
  TEAM_PROJECT = 'TEAM_PROJECT',
  DATABASE = 'DATABASE',
}

export enum AffiliationType {
  'USER_TEAM' = 'USER_TEAM',
  'TEAM_USER' = 'TEAM_USER',
  'TEAM_PROJECT' = 'TEAM_PROJECT'
}

export enum SearchType {
  PROJECT = 'PROJECT',
  TEAM = 'TEAM',
  USER = 'USER'
}

export enum StatusType {
  INVALID = 'INVALID',
  VALID = 'VALID',
}

export enum RoleType {
  ADMIN = 'ADMIN',
  USER = 'USER',
}

export enum MemberType {
  TEAM = 'TEAM',
  USER = 'USER'
}

export interface IProjectVO {
  id?: number;
  name?: string;
  description?: string;
}

export interface IEnvironmentVO {
  /**
   * 主键
   */
  id?: number;
  /**
   * 环境名称
   */
  name?: string;
  /**
   * 环境缩写
   */
  shortName?: string;
  /**
   * 样式类型
   */
  style?: string;

  color?: string;

  projectId?: number;
}
// ===================== User ======================

export interface IUserVO {
  /**
 * 主键
 */
  id: number;

  /**
   * 邮箱
   */
  email: string;
  /**
   * 昵称
   */
  nickName: string;
  /**
   * 密码
   */
  password: string;
  /**
   * 角色编码
   */
  roleCode: RoleType;
  /**
   * 用户状态
   */
  status: StatusType;
  /**
   * 用户名
   */
  userName: string;

  oidcBound?: boolean;

  authSource?: string;

  oidcIssuer?: string;

  ssoStatus?: string;

  lastSsoLoginAt?: string;
}


export interface IUserWithTeamVO {
  /**
   * 主键
   */
  id?: number;
  /**
   * 团队
   */
  team?: ITeamVO;
  /**
   * user id
   */
  userId?: number;
}

// ===================== Team =====================

export interface ITeamVO {
  id?: number;
  /**
   * 团队编码
   */
  code: string;
  /**
   * 团队描述
   */
  description?: string;
  /**
   * 团队名称
   */
  name: string;
  /**
   * 团队状态
   */
  status: StatusType;
}

export interface ITeamWithUserVO {
  id: number;
  teamId: number;
  user: IUserVO;
}

export interface ITeamWithProjectVO {
  id: number;
  teamId?: number;
  project?: IProjectVO;
  permissionType?: 'VIEW' | 'TEAM_ADMIN';
  environmentList?: IEnvironmentVO[];
}

export interface ITeamProjectGrantPayload {
  projectId: number;
  permissionType?: 'VIEW' | 'TEAM_ADMIN';
  environmentIdList?: number[];
}

export interface IAuditQuery extends IPageParams {
  category?: AuditCategory;
  resourceType?: AuditResourceType;
  status?: string;
  searchKey?: string;
  operatorUserId?: number;
  targetId?: string;
  dataSourceId?: number;
  startTime?: number;
  endTime?: number;
}

export interface IAuditRecord {
  id: string;
  category: AuditCategory;
  actionType: AuditActionType;
  resourceType: AuditResourceType;
  operatorUserId?: number;
  operatorUserName?: string;
  roleCode?: string;
  targetId?: string;
  targetName?: string;
  status?: string;
  occurredAt?: string | number;
  detailSummary?: string;
  detailPayload?: string;
  requestId?: string;
  requestPath?: string;
  requestMethod?: string;
  clientIp?: string;
  userAgent?: string;
  dataSourceId?: number;
  dataSourceName?: string;
  sqlType?: string;
  durationMs?: number;
  operationRows?: number;
  errorMessage?: string;
}

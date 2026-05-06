import { DatabaseTypeCode } from '@/constants';

// 连接 高级配置列表的信息
export interface IConnectionExtendInfoItem {
  key: string;
  value: string;
}

// 连接的环境信息
export interface IConnectionEnv {
  id: number;
  name: string;
  shortName: string;
  color: string;
  canManage?: boolean;
  scopeType?: string;
  scopeId?: number;
  projectId?: number;
}

export interface IConnectionProjectItem {
  id: number;
  name: string;
  canManage?: boolean;
  description?: string;
}

// 连接列表的信息
export interface IConnectionListItem {
  id: number;
  alias: string;
  environment: IConnectionEnv;
  environmentId?: number;
  type: DatabaseTypeCode;
  supportDatabase: boolean;
  supportSchema: boolean;
  user: string;
  projectId?: number;
  projectName?: string;
  accessScope?: 'PERSONAL' | 'PROJECT';
  canManage?: boolean;
}


export interface IConnectionDetails {
  id: number;
  alias: string;
  environment: IConnectionEnv;
  type: DatabaseTypeCode;

  isAdmin?: boolean;
  canManage?: boolean;
  url: string;
  user: string;
  password: string;
  ConsoleOpenedStatus: 'y' | 'n';
  extendInfo: IConnectionExtendInfoItem[];
  environmentId: number;
  projectId?: number;
  projectName?: string;
  accessScope?: 'PERSONAL' | 'PROJECT';
  ssh: any;
  driverConfig: {
    jdbcDriver: string;
    jdbcDriverClass: string;
  };
  [key: string]: any;
}

export type ICreateConnectionDetails = Omit<IConnectionDetails, 'id'>

export interface IConnectionTemplateItem {
  alias?: string;
  url?: string;
  user?: string;
  password?: string;
  type?: DatabaseTypeCode | string;
  host?: string;
  port?: string;
  ssh?: any;
  sid?: string;
  driver?: string;
  jdbc?: string;
  extendInfo?: IConnectionExtendInfoItem[];
  driverConfig?: {
    jdbcDriver?: string;
    jdbcDriverClass?: string;
  };
  environmentId?: number;
  environmentName?: string;
  environmentShortName?: string;
  projectId?: number;
  projectName?: string;
  serviceName?: string;
  serviceType?: string;
}

export interface IConnectionTemplate {
  version: string;
  template: string;
  exportedAt: string;
  connections: IConnectionTemplateItem[];
}

export interface IConnectionImportResult {
  total: number;
  successCount: number;
  failureCount: number;
  createdIds: number[];
  errors: string[];
}

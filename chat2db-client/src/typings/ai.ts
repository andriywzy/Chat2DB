export enum AIType {
  CHAT2DBAI = 'CHAT2DBAI',
  ZHIPUAI = 'ZHIPUAI',
  BAICHUANAI='BAICHUANAI',
  WENXINAI='WENXINAI',
  TONGYIQIANWENAI='TONGYIQIANWENAI',
  OPENAI = 'OPENAI',
  AZUREAI = 'AZUREAI',
  RESTAI = 'RESTAI',
}

export interface IRemainingUse {
  key: string;
  wechatMpUrl: string;
  expiry: number;
  remainingUses: number;
}

export interface ILoginAndQrCode {
  token: string;
  wechatQrCodeUrl: string;
  apiKey: string;
  tip: string;
}

export interface IInviteQrCode {
  wechatQrCodeUrl: string;
  tip: string;
}

export type IAiScopeEntityType = 'database' | 'table';

export interface IAiScopeEntity {
  entityType: IAiScopeEntityType;
  name: string;
  dataSourceId?: number;
  dataSourceAlias?: string;
  databaseName?: string;
  schemaName?: string;
  currentDataSource?: boolean;
  sourceType?: string;
}

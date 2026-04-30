import { IPageParams, IPageResponse } from './common';
import { ITeamVO } from './team';

export type SsoAuthMode = 'LOCAL_ONLY' | 'LOCAL_AND_OIDC' | 'OIDC_ONLY';

export interface IOidcPublicConfig {
  enabled: boolean;
  authMode: SsoAuthMode;
  allowLocalLogin: boolean;
  authorizePath: string;
  buttonText: string;
}

export interface ISsoAdminConfig {
  enabled?: boolean;
  authMode?: SsoAuthMode;
  issuerUri?: string;
  clientId?: string;
  clientSecret?: string;
  scopes?: string;
  authorizationUri?: string;
  tokenUri?: string;
  userInfoUri?: string;
  jwkSetUri?: string;
  redirectUri?: string;
  usernameClaim?: string;
  emailClaim?: string;
  nameClaim?: string;
  groupsClaim?: string;
  logoutRedirectUri?: string;
}

export interface ISsoGroupMapping {
  id?: number;
  providerType?: string;
  issuer?: string;
  externalGroupCode?: string;
  syncMode?: string;
  team?: ITeamVO;
  gmtModified?: string;
}

export interface ISsoGroupMappingRequest {
  id?: number;
  issuer: string;
  externalGroupCode: string;
  teamId: number;
}

export interface ISsoGroupMappingPageParams extends IPageParams {}

export interface ISsoGroupMappingPageResponse extends IPageResponse<ISsoGroupMapping> {}

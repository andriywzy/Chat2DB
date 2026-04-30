import createRequest from './base';
import { ISsoAdminConfig, ISsoGroupMappingPageResponse, ISsoGroupMappingRequest, IOidcPublicConfig, IPageParams } from '@/typings';

const getOidcPublicConfig = createRequest<void, IOidcPublicConfig>('/api/oauth/oidc/config', {
  method: 'get',
  errorLevel: false,
});

const getSsoAdminConfig = createRequest<void, ISsoAdminConfig>('/api/admin/sso/config', {
  method: 'get',
});

const saveSsoAdminConfig = createRequest<ISsoAdminConfig, void>('/api/admin/sso/config', {
  method: 'post',
});

const testSsoConnection = createRequest<ISsoAdminConfig | void, any>('/api/admin/sso/test-connection', {
  method: 'post',
});

const getSsoGroupMappingPage = createRequest<IPageParams, ISsoGroupMappingPageResponse>('/api/admin/sso/group-mapping/page', {
  method: 'get',
});

const createSsoGroupMapping = createRequest<ISsoGroupMappingRequest, void>('/api/admin/sso/group-mapping/create', {
  method: 'post',
});

const updateSsoGroupMapping = createRequest<ISsoGroupMappingRequest, void>('/api/admin/sso/group-mapping/update', {
  method: 'post',
});

const deleteSsoGroupMapping = createRequest<{ id: number }, void>('/api/admin/sso/group-mapping/:id', {
  method: 'delete',
});

export default {
  getOidcPublicConfig,
  getSsoAdminConfig,
  saveSsoAdminConfig,
  testSsoConnection,
  getSsoGroupMappingPage,
  createSsoGroupMapping,
  updateSsoGroupMapping,
  deleteSsoGroupMapping,
};

import axios from '@/utils/request';

export const queryOrgList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/org', { params })).data;
export const queryOrg = async (id: string): Promise<any> => (await axios.get(`/backend/core/org/${id}`)).data;
export const createOrg = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/org', data)).data;
export const updateOrg = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/org?_method=put', data)).data;
export const updateOrgPermission = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/org/permission?_method=put', data)).data;
export const moveOrg = async (fromId: string, toId: string, type: 'inner' | 'before' | 'after'): Promise<any> =>
  (await axios.post('/backend/core/org/move?_method=put', { fromId, toId, type })).data;
export const tidyTreeOrg = async (): Promise<any> => (await axios.post('/backend/core/org/tidy-tree?_method=put')).data;
export const deleteOrg = async (data: string[]): Promise<any> => (await axios.post('/backend/core/org?_method=delete', data)).data;
export const queryOrgPermissions = async (): Promise<any> => (await axios.get('/backend/core/org/permissions')).data;
export const queryOrgArticlePermissions = async (orgId: string, siteId?: string): Promise<any> =>
  (await axios.get('/backend/core/org/article-permissions', { params: { orgId, siteId } })).data;
export const queryOrgChannelPermissions = async (orgId: string, siteId?: string): Promise<any> =>
  (await axios.get('/backend/core/org/channel-permissions', { params: { orgId, siteId } })).data;
export const queryOrgPermPermissions = async (orgId: string, global: boolean): Promise<any> =>
  (await axios.get('/backend/core/org/org-permissions', { params: { orgId, global } })).data;

export const queryRoleList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/role', { params })).data;
export const queryRole = async (id: string): Promise<any> => (await axios.get(`/backend/core/role/${id}`)).data;
export const createRole = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/role', data)).data;
export const updateRole = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/role?_method=put', data)).data;
export const updateRolePermission = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/role/permission?_method=put', data)).data;
export const updateRoleOrder = async (data: string[]): Promise<any> => (await axios.post('/backend/core/role/order?_method=put', data)).data;
export const deleteRole = async (data: string[]): Promise<any> => (await axios.post('/backend/core/role?_method=delete', data)).data;
export const queryRoleArticlePermissions = async (roleId: string, siteId?: string): Promise<any> =>
  (await axios.get('/backend/core/role/article-permissions', { params: { roleId, siteId } })).data;
export const queryRoleChannelPermissions = async (roleId: string, siteId?: string): Promise<any> =>
  (await axios.get('/backend/core/role/channel-permissions', { params: { roleId, siteId } })).data;
export const queryRoleOrgPermissions = async (roleId: string, siteId?: string): Promise<any> =>
  (await axios.get('/backend/core/role/org-permissions', { params: { roleId, siteId } })).data;
export const roleScopeNotAllowed = async (scope: number, roleId: string): Promise<any> =>
  (await axios.get('/backend/core/role/scope-not-allowed', { params: { scope, roleId } })).data;

export const queryGroupList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/group', { params })).data;
export const queryGroup = async (id: string): Promise<any> => (await axios.get(`/backend/core/group/${id}`)).data;
export const createGroup = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/group', data)).data;
export const updateGroup = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/group?_method=put', data)).data;
export const updateGroupPermission = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/group/permission?_method=put', data)).data;
export const updateGroupOrder = async (data: string[]): Promise<any> => (await axios.post('/backend/core/group/order?_method=put', data)).data;
export const deleteGroup = async (data: string[]): Promise<any> => (await axios.post('/backend/core/group?_method=delete', data)).data;
export const groupAccessPermissions = async (groupId: string, siteId?: string): Promise<any> =>
  (await axios.get('/backend/core/group/access-permissions', { params: { groupId, siteId } })).data;

export const queryUserPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/user', { params })).data;
export const queryUserList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/user/list', { params })).data;
export const queryUser = async (id: string): Promise<any> => (await axios.get(`/backend/core/user/${id}`)).data;
export const createUser = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/user', data)).data;
export const updateUser = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/user?_method=put', data)).data;
export const updateUserPermission = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/user/permission?_method=put', data)).data;
export const updateUserPassword = async (id: string, password: string): Promise<any> => (await axios.post('/backend/core/user/password?_method=put', { id, password })).data;
export const updateUserStatus = async (ids: string[], status: number): Promise<any> => (await axios.post('/backend/core/user/status?_method=put', { ids, status })).data;
export const deleteUser = async (data: string[]): Promise<any> => (await axios.post('/backend/core/user?_method=delete', data)).data;
export const usernameExist = async (username?: string): Promise<any> => (await axios.get('/backend/core/user/username-exist', { params: { username } })).data;
export const emailExist = async (email?: string): Promise<any> => (await axios.get('/backend/core/user/email-exist', { params: { email } })).data;
export const mobileExist = async (mobile?: string): Promise<any> => (await axios.get('/backend/core/user/mobile-exist', { params: { mobile } })).data;

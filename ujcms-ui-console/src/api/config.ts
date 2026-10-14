import axios from '@/utils/request';

export const imageUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/image-upload`;
export const avatarUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/avatar-upload`;
export const videoUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/video-upload`;
export const audioUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/audio-upload`;
export const mediaUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/media-upload`;
export const docUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/doc-upload`;
export const fileUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/file-upload`;
export const managementUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/management-upload`;

export const cropImage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/image-crop', data)).data;
export const cropAvatar = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/avatar-crop', data)).data;
export const fetchImage = async (url: string): Promise<any> => (await axios.post('/backend/image-fetch', url, { headers: { 'Content-Type': 'text/plain' } })).data;

export const queryConfigModel = async (): Promise<any> => (await axios.get('/backend/core/config/model')).data;
export const queryConfig = async (): Promise<any> => (await axios.get('/backend/core/config')).data;
export const queryConfigGrey = async (): Promise<any> => (await axios.get('/backend/core/config/grey')).data;
export const queryConfigOauth = async (): Promise<any> => (await axios.get('/backend/core/config/oauth')).data;
export const queryConfigSms = async (): Promise<any> => (await axios.get('/backend/core/config/sms')).data;
export const queryConfigEmail = async (): Promise<any> => (await axios.get('/backend/core/config/email')).data;
export const queryUploadStorage = async (): Promise<any> => (await axios.get('/backend/core/config/upload-storage')).data;
export const queryHtmlStorage = async (): Promise<any> => (await axios.get('/backend/core/config/html-storage')).data;
export const queryTemplateStorage = async (): Promise<any> => (await axios.get('/backend/core/config/template-storage')).data;
export const queryPackageStorage = async (): Promise<any> => (await axios.get('/backend/core/config/package-storage')).data;
export const updateConfigBase = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/base?_method=put', data)).data;
export const updateConfigCustoms = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/customs?_method=put', data)).data;
export const updateConfigUpload = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/upload?_method=put', data)).data;
export const updateConfigGrey = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/grey?_method=put', data)).data;
export const updateConfigOauth = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/oauth?_method=put', data)).data;
export const updateConfigRegister = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/register?_method=put', data)).data;
export const updateConfigSecurity = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/security?_method=put', data)).data;
export const applyConfigSecurityL3 = async (): Promise<any> => (await axios.post('/backend/core/config/security/apply-l3')).data;
export const queryTwoFactorReadiness = async (): Promise<any> => (await axios.get('/backend/core/config/security/two-factor-readiness')).data;
export const updateConfigSms = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/sms?_method=put', data)).data;
export const sendTestSms = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/sms/send', data)).data;
export const updateConfigEmail = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/email?_method=put', data)).data;
export const sendTestEmail = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/email/send', data)).data;
export const updateUploadStorage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/upload-storage?_method=put', data)).data;
export const updateHtmlStorage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/html-storage?_method=put', data)).data;
export const updateTemplateStorage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/template-storage?_method=put', data)).data;
export const updatePackageStorage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/package-storage?_method=put', data)).data;
export const storagePathAllowed = async (path: string): Promise<any> => (await axios.get('/backend/core/config/storage-path-allowed', { params: { path } })).data;

export const querySiteSettings = async (): Promise<any> => (await axios.get('/backend/core/site-settings')).data;
export const updateSiteBaseSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/base?_method=put', data)).data;
export const updateSiteCustomsSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/customs?_method=put', data)).data;
export const updateSiteWatermarkSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/watermark?_method=put', data)).data;
export const updateSiteEditorSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/editor?_method=put', data)).data;
export const updateSiteMessageBoardSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/message-board?_method=put', data)).data;
export const updateSiteCommentSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/comment?_method=put', data)).data;
export const updateSiteDisclosureSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/disclosure?_method=put', data)).data;
export const updateSiteMailboxSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/mailbox?_method=put', data)).data;
export const updateSiteInterviewSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/interview?_method=put', data)).data;
export const updateSiteAccessibilitySettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/accessibility?_method=put', data)).data;
export const updateSitePermalinkSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/permalink?_method=put', data)).data;
export const updateSiteCensusSettings = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/site-settings/census?_method=put', data)).data;
export const updateSiteGeoSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/geo?_method=put', data)).data;
// GEO 辅助接口（ext）：robots.txt 预览由后端按表单当前值生成；推送状态/推送测试针对当前站点已保存的设置
export const previewRobotsTxt = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/geo/robots-preview', data)).data;
export const querySearchEnginePushStatus = async (): Promise<any> => (await axios.get('/backend/ext/geo/push-status')).data;
export const testSearchEnginePush = async (engine: string): Promise<any> => (await axios.post('/backend/ext/geo/push-test', null, { params: { engine } })).data;
export const querySiteHtmlSettings = async (): Promise<any> => (await axios.get('/backend/core/site-settings/html')).data;
export const updateSiteHtmlSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/html?_method=put', data)).data;
export const queryCurrentSiteThemeList = async (): Promise<any> => (await axios.get('/backend/core/site/theme')).data;

export const queryModelList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/model', { params })).data;
export const queryModel = async (id: string): Promise<any> => (await axios.get(`/backend/core/model/${id}`)).data;
export const createModel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/model', data)).data;
export const updateModel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/model?_method=put', data)).data;
export const updateModelOrder = async (data: string[]): Promise<any> => (await axios.post('/backend/core/model/order?_method=put', data)).data;
export const deleteModel = async (data: string[]): Promise<any> => (await axios.post('/backend/core/model?_method=delete', data)).data;
// 写接口各管一段（model-management M2）：字段集不随名称/共享范围一起回传
export const updateModelSystemFields = async (id: string, data: { mains?: string | null; asides?: string | null }): Promise<any> =>
  (await axios.post(`/backend/core/model/${id}/system-fields?_method=put`, data)).data;
export const updateModelCustomFields = async (id: string, data: { customs?: string | null }): Promise<any> =>
  (await axios.post(`/backend/core/model/${id}/custom-fields?_method=put`, data)).data;
// quiet：跳过全局错误处理——使用统计是列表的附加信息，失败时静默降级
export const queryModelUsages = async (type: string, quiet = false): Promise<any> =>
  (await axios.get('/backend/core/model/usages', { params: { type }, skipErrorHandler: quiet } as any)).data;

export const queryDictTypeList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/dict-type', { params })).data;
export const queryDictType = async (id: string): Promise<any> => (await axios.get(`/backend/core/dict-type/${id}`)).data;
export const createDictType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/dict-type', data)).data;
export const updateDictType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/dict-type?_method=put', data)).data;
export const updateDictTypeOrder = async (data: string[]): Promise<any> => (await axios.post('/backend/core/dict-type/order?_method=put', data)).data;
export const deleteDictType = async (data: string[]): Promise<any> => (await axios.post('/backend/core/dict-type?_method=delete', data)).data;
export const dictTypeAliasExist = async (alias: string, scope: number): Promise<any> => (await axios.get('/backend/core/dict-type/alias-exist', { params: { alias, scope } })).data;
// 绑定该类型的模型自定义字段。quiet：使用情况是辅助信息，取不到（如后端尚未升级）不弹错
export const queryDictTypeUsages = async (id: string, quiet = false): Promise<any> => (await axios.get(`/backend/core/dict-type/${id}/usages`, { skipErrorHandler: quiet } as any)).data;

export const queryDictList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/dict', { params })).data;
// quiet：跳过全局错误处理（如站点语言下拉——缺权限/失败时静默降级为自由输入）
export const queryDictListByAlias = async (alias: string, name?: string, quiet = false): Promise<any> =>
  (await axios.get('/backend/core/dict/list-by-alias', { params: { alias, name }, skipErrorHandler: quiet } as any)).data;
export const queryDict = async (id: string): Promise<any> => (await axios.get(`/backend/core/dict/${id}`)).data;
export const createDict = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/dict', data)).data;
export const updateDict = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/dict?_method=put', data)).data;
export const updateDictOrder = async (data: string[]): Promise<any> => (await axios.post('/backend/core/dict/order?_method=put', data)).data;
export const deleteDict = async (data: string[]): Promise<any> => (await axios.post('/backend/core/dict?_method=delete', data)).data;
export const dictValueExist = async (typeId: string, value: string): Promise<any> => (await axios.get('/backend/core/dict/value-exist', { params: { typeId, value } })).data;

export const queryBlockList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/block', { params })).data;
export const queryBlockCategories = async (): Promise<string[]> => (await axios.get('/backend/core/block/categories')).data;
export const queryBlock = async (id: string): Promise<any> => (await axios.get(`/backend/core/block/${id}`)).data;
export const createBlock = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/block', data)).data;
export const updateBlock = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/block?_method=put', data)).data;
export const updateBlockOrder = async (data: string[]): Promise<any> => (await axios.post('/backend/core/block/order?_method=put', data)).data;
export const deleteBlock = async (data: string[]): Promise<any> => (await axios.post('/backend/core/block?_method=delete', data)).data;
export const blockAliasExist = async (alias: string, scope: number): Promise<any> => (await axios.get('/backend/core/block/alias-exist', { params: { alias, scope } })).data;
export const blockScopeNotAllowed = async (scope: number, blockId: string): Promise<any> =>
  (await axios.get('/backend/core/block/scope-not-allowed', { params: { scope, blockId } })).data;

export const queryMessageBoardTypeList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/message-board-type', { params })).data;
export const queryMessageBoardType = async (id: string): Promise<any> => (await axios.get(`/backend/ext/message-board-type/${id}`)).data;
export const createMessageBoardType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/message-board-type', data)).data;
export const updateMessageBoardType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/message-board-type?_method=put', data)).data;
export const updateMessageBoardTypeOrder = async (fromId: string, toId: string): Promise<any> =>
  (await axios.post('/backend/ext/message-board-type/update-order', { fromId, toId })).data;
export const deleteMessageBoardType = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/message-board-type?_method=delete', data)).data;

export const queryMailboxTypeList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/mailbox-type', { params })).data;
export const queryMailboxType = async (id: string): Promise<any> => (await axios.get(`/backend/ext/mailbox-type/${id}`)).data;
export const createMailboxType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/mailbox-type', data)).data;
export const updateMailboxType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/mailbox-type?_method=put', data)).data;
export const updateMailboxTypeOrder = async (fromId: string, toId: string): Promise<any> =>
  (await axios.post('/backend/ext/mailbox-type/update-order', { fromId, toId })).data;
export const deleteMailboxType = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/mailbox-type?_method=delete', data)).data;

export const queryPerformanceTypeList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/performance-type', { params })).data;
export const queryPerformanceType = async (id: string): Promise<any> => (await axios.get(`/backend/core/performance-type/${id}`)).data;
export const createPerformanceType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/performance-type', data)).data;
export const updatePerformanceType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/performance-type?_method=put', data)).data;
export const updatePerformanceTypeOrder = async (fromId: string, toId: string): Promise<any> =>
  (await axios.post('/backend/core/performance-type/update-order', { fromId, toId })).data;
export const deletePerformanceType = async (data: string[]): Promise<any> => (await axios.post('/backend/core/performance-type?_method=delete', data)).data;

export const queryFormTypeList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/form-type', { params })).data;
export const queryFormType = async (id: string): Promise<any> => (await axios.get(`/backend/ext/form-type/${id}`)).data;
export const createFormType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/form-type', data)).data;
export const updateFormType = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/form-type?_method=put', data)).data;
export const updateFormTypeOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/form-type/update-order', { fromId, toId })).data;
export const deleteFormType = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/form-type?_method=delete', data)).data;
export const queryFormListTemplates = async (): Promise<any> => (await axios.get('/backend/ext/form-type/list-templates')).data;
export const queryFormItemTemplates = async (): Promise<any> => (await axios.get('/backend/ext/form-type/item-templates')).data;

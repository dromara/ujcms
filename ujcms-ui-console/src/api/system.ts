import axios from '@/utils/request';
import { parseAttachmentFilename } from '@/api/theme';

export const queryCurrentSite = async (): Promise<any> => (await axios.get('/backend/core/site/current')).data;

export const querySiteList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/site', { params })).data;
export const querySite = async (id: string): Promise<any> => (await axios.get(`/backend/core/site/${id}`)).data;
export const createSite = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site', data)).data;
export const updateSite = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site?_method=put', data)).data;
export const openSite = async (data: string[]): Promise<any> => (await axios.post('/backend/core/site/open?_method=put', data)).data;
export const closeSite = async (data: string[]): Promise<any> => (await axios.post('/backend/core/site/close?_method=put', data)).data;
export const moveSite = async (fromId: string, toId: string, type: 'inner' | 'before' | 'after'): Promise<any> =>
  (await axios.post('/backend/core/site/move?_method=put', { fromId, toId, type })).data;
export const tidyTreeSite = async (): Promise<any> => (await axios.post('/backend/core/site/tidy-tree?_method=put')).data;
export const deleteSite = async (data: string[]): Promise<any> => (await axios.post('/backend/core/site?_method=delete', data)).data;
export const querySiteThemeList = async (id: string): Promise<any> => (await axios.get(`/backend/core/site/${id}/theme`)).data;

export const queryAttachmentPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/attachment', { params })).data;
export const queryAttachment = async (id: string): Promise<any> => (await axios.get(`/backend/core/attachment/${id}`)).data;
export const createAttachment = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/attachment', data)).data;
export const updateAttachment = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/attachment?_method=put', data)).data;
export const deleteAttachment = async (data: string[]): Promise<any> => (await axios.post('/backend/core/attachment?_method=delete', data)).data;
export const shareAttachment = async (data: { id: string; categoryId?: string | null }): Promise<any> =>
  (await axios.post('/backend/core/attachment/share?_method=put', data)).data;
export const unshareAttachment = async (data: string[]): Promise<any> => (await axios.post('/backend/core/attachment/unshare?_method=put', data)).data;
export const favoriteAttachment = async (data: string[]): Promise<any> => (await axios.post('/backend/core/attachment/favorite?_method=put', data)).data;
export const unfavoriteAttachment = async (data: string[]): Promise<any> => (await axios.post('/backend/core/attachment/unfavorite?_method=put', data)).data;
export const categorizeFavoriteAttachment = async (data: { ids: string[]; categoryId?: string | null }): Promise<any> =>
  (await axios.post('/backend/core/attachment/favorite-category?_method=put', data)).data;
export const importWebFileAttachment = async (data: { id: string; scope?: string; categoryId?: string | null }): Promise<any> =>
  (await axios.post('/backend/core/attachment/import-web-file', data)).data;
export const existingAttachmentUrls = async (data: string[]): Promise<string[]> =>
  (await axios.post('/backend/core/attachment/existing-urls', data)).data;
export const deleteUnusedAttachment = async (): Promise<any> => (await axios.post('/backend/core/attachment/unused?_method=delete')).data;

export const queryResourceCategoryList = async (params?: Record<string, any>): Promise<any> =>
  (await axios.get('/backend/core/resource-category', { params })).data;
export const queryResourceCategory = async (id: string): Promise<any> => (await axios.get(`/backend/core/resource-category/${id}`)).data;
export const createResourceCategory = async (data: Record<string, any>, params?: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/resource-category', data, { params })).data;
export const updateResourceCategory = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/resource-category?_method=put', data)).data;
export const deleteResourceCategory = async (data: string[]): Promise<any> => (await axios.post('/backend/core/resource-category?_method=delete', data)).data;

export const queryTaskPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/task', { params })).data;
export const queryTask = async (id: string): Promise<any> => (await axios.get(`/backend/core/task/${id}`)).data;
export const createTask = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/task', data)).data;
export const updateTask = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/task?_method=put', data)).data;
export const deleteTask = async (data: string[]): Promise<any> => (await axios.post('/backend/core/task?_method=delete', data)).data;

export const queryScheduleTaskList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/schedule-task', { params })).data;
export const queryScheduleTask = async (id: string): Promise<any> => (await axios.get(`/backend/core/schedule-task/${id}`)).data;
export const queryScheduleTaskTypes = async (): Promise<any> => (await axios.get('/backend/core/schedule-task/types')).data;
export const scheduleTaskCronValid = async (cron: string): Promise<any> => (await axios.get('/backend/core/schedule-task/cron-valid', { params: { cron } })).data;
export const createScheduleTask = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/schedule-task', data)).data;
export const updateScheduleTask = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/schedule-task?_method=put', data)).data;
export const updateScheduleTaskOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/core/schedule-task/update-order', { fromId, toId })).data;
export const deleteScheduleTask = async (data: string[]): Promise<any> => (await axios.post('/backend/core/schedule-task?_method=delete', data)).data;
export const runScheduleTask = async (data: string[]): Promise<any> => (await axios.post('/backend/core/schedule-task/run', data)).data;

export const queryProcessModelList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/process-model', { params })).data;
export const queryProcessModel = async (id: string): Promise<any> => (await axios.get(`/backend/core/process-model/${id}`)).data;
export const queryProcessModelXml = async (id: string): Promise<any> => (await axios.get(`/backend/core/process-model/xml/${id}`)).data;
export const createProcessModel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/process-model', data)).data;
export const updateProcessModel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/process-model?_method=put', data)).data;
export const updateProcessModelXml = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/process-model/xml?_method=put', data)).data;
export const validateProcessModelXml = async (xml: string): Promise<any> =>
  (await axios.post('/backend/core/process-model/xml/validate', xml, { headers: { 'Content-Type': 'text/plain' } })).data;
export const deployProcessModel = async (id: string): Promise<any> => (await axios.post(`/backend/core/process-model/deploy/${id}`)).data;
export const deleteProcessModel = async (data: string[]): Promise<any> => (await axios.post('/backend/core/process-model?_method=delete', data)).data;

export const queryProcessDefinitionList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/process-definition', { params })).data;
export const queryProcessDefinitionXml = async (id: string): Promise<any> => (await axios.get(`/backend/core/process-definition/xml/${id}`)).data;
export const deleteProcessDefinition = async (data: string[]): Promise<any> => (await axios.post('/backend/core/process-definition?_method=delete', data)).data;

export const queryProcessInstanceList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/process-instance', { params })).data;
export const queryProcessTaskList = async (instanceId: string): Promise<any> => (await axios.get(`/backend/core/process-instance/task/${instanceId}`)).data;
export const queryProcessBackActivityList = async (taskId: string): Promise<any> => (await axios.get(`/backend/core/process-instance/back-activity/${taskId}`)).data;
export const deleteProcessInstance = async (data: string[]): Promise<any> => (await axios.post('/backend/core/process-instance?_method=delete', data)).data;

export const queryProcessHistoryList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/process-history', { params })).data;
export const deleteProcessHistory = async (data: string[]): Promise<any> => (await axios.post('/backend/core/process-history?_method=delete', data)).data;

export const queryTaskFormExists = async (taskIds: string[]): Promise<any> => (await axios.post('/backend/core/process-form/task-form-exists', taskIds)).data;
export const queryTaskFormProperties = async (taskId: string): Promise<any> => (await axios.get('/backend/core/process-form/task-form-properties', { params: { taskId } })).data;
export const queryStartFormProperties = async (processKey: string): Promise<any> =>
  (await axios.get('/backend/core/process-form/start-form-properties', { params: { processKey } })).data;

export const querySensitiveWordPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/sensitive-word', { params })).data;
export const querySensitiveWord = async (id: string): Promise<any> => (await axios.get(`/backend/core/sensitive-word/${id}`)).data;
export const createSensitiveWord = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/sensitive-word', data)).data;
export const updateSensitiveWord = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/sensitive-word?_method=put', data)).data;
export const deleteSensitiveWord = async (data: string[]): Promise<any> => (await axios.post('/backend/core/sensitive-word?_method=delete', data)).data;
/** 单次导入的词条上限（服务端 SensitiveWordController / ErrorWordController 的 IMPORT_MAX_SIZE） */
export const WORD_IMPORT_MAX_SIZE = 5000;
export const importSensitiveWord = async (words: string[]): Promise<any> => (await axios.post('/backend/core/sensitive-word/import', { words })).data;
export const exportSensitiveWord = async (params?: Record<string, any>): Promise<any[]> => (await axios.get('/backend/core/sensitive-word/export', { params })).data;

// 布局变体目录：活动主题清单声明目录（构建器模板轨道变体切换器口径，标签来自主题）。
// 快照直读，仅下拉用。
export const queryLayoutVariantBySurface = async (surface: string): Promise<any> =>
  (await axios.get(`/backend/core/layout-variant/by-surface/${surface}`)).data;

export const queryErrorWordPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/error-word', { params })).data;
export const queryErrorWord = async (id: string): Promise<any> => (await axios.get(`/backend/core/error-word/${id}`)).data;
export const createErrorWord = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/error-word', data)).data;
export const updateErrorWord = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/error-word?_method=put', data)).data;
export const deleteErrorWord = async (data: string[]): Promise<any> => (await axios.post('/backend/core/error-word?_method=delete', data)).data;
export const importErrorWord = async (words: { name: string; correct: string }[]): Promise<any> => (await axios.post('/backend/core/error-word/import', { words })).data;
export const exportErrorWord = async (params?: Record<string, any>): Promise<any[]> => (await axios.get('/backend/core/error-word/export', { params })).data;

// 数据迁移（docs/spec/data-migration/plan.md）：连接测试、预览是同步只读请求；迁入是后台任务，import-* 返回 taskId，task/{id} 轮询
export const queryImportDataMeta = async (): Promise<any> => (await axios.get('/backend/ext/import-data/meta')).data;
export const queryImportDataSummary = async (): Promise<any> => (await axios.get('/backend/ext/import-data/summary')).data;
export const importDataTestConnection = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/import-data/test-connection', data)).data;
export const previewImportData = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/import-data/preview', data, { timeout: 120000 })).data;
export const importDataChannel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/import-data/import-channel', data)).data;
export const importDataArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/import-data/import-article', data)).data;
export const queryImportDataTask = async (id: string): Promise<any> => (await axios.get(`/backend/ext/import-data/task/${id}`)).data;
export const stopImportDataTask = async (id: string): Promise<any> => (await axios.post(`/backend/ext/import-data/task/${id}/stop`)).data;
export const importDataDeleteCorrespond = async (): Promise<any> => (await axios.post('/backend/ext/import-data/delete-correspond?_method=delete')).data;
export const exportImportDataCorrespond = async (): Promise<{ blob: Blob; filename: string }> => {
  const response = await axios.get('/backend/ext/import-data/correspond/export', { responseType: 'blob', skipErrorHandler: true } as any);
  const filename = parseAttachmentFilename(response.headers?.['content-disposition'] ?? '') ?? 'import-data.csv';
  return { blob: response.data, filename };
};

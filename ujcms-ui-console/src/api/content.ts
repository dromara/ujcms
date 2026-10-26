import axios from '@/utils/request';
import { parseAttachmentFilename } from '@/api/theme';

export const jodConvertDocUrl = `${import.meta.env.VITE_BASE_API}/backend/core/jod-convert/doc`;
export const jodConvertLibraryUrl = `${import.meta.env.VITE_BASE_API}/backend/core/jod-convert/library`;
/** 当前安装能导入什么：.docx/.doc/.wps 由内置引擎读取；office 为 LibreOffice 是否可用（素材库转 PDF、.rtf/.odt 的导入） */
export interface DocImportCapabilities {
  docx: boolean;
  doc: boolean;
  office: boolean;
  /** DOC导入按钮接受的扩展名（已与上传设置的文档类型取交集） */
  extensions: string[];
}
export interface DocImportResult {
  html: string;
  title?: string | null;
  author?: string | null;
  warnings: { code: string; count: number }[];
  engine: string;
}
export const queryDocImportCapabilities = async (): Promise<DocImportCapabilities> => (await axios.get('/backend/core/jod-convert/capabilities')).data;

export const queryChannelList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/channel', { params })).data;
export const queryChannel = async (id: string): Promise<any> => (await axios.get(`/backend/core/channel/${id}`)).data;
export const createChannel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/channel', data)).data;
export const updateChannel = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/channel?_method=put', data)).data;
/** 批量新增单次上限（与服务端 ChannelService.BATCH_MAX_SIZE 一致）：操作日志记录整个请求体，且一棵树无法拆成多次提交 */
export const CHANNEL_BATCH_MAX_SIZE = 500;
/** 批量新增栏目：items 中上级先于下级，parent 为上级在 items 中的序号，-1 表示直接建在 parentId（为空则根）之下。全有或全无 */
export const batchCreateChannel = async (data: {
  parentId?: string;
  channelModelId: string;
  articleModelId: string;
  items: { name: string; slug?: string; parent: number }[];
}): Promise<{ created: number }> => (await axios.post('/backend/core/channel/batch-create', data)).data;
/** 可批量设置的属性（与服务端 ChannelService.BATCH_UPDATE_FIELDS 一致） */
export type ChannelBatchField = 'articleModelId' | 'channelTemplate' | 'articleTemplate' | 'processKey' | 'nav' | 'real' | 'allowSearch' | 'pageSize';
/** 批量设置栏目：只改写 fields 列出的属性；模板、审核流程列出而取值为空即清空。includeChildren 连同所选栏目的全部下级。全有或全无 */
export const batchUpdateChannel = async (data: {
  ids: string[];
  includeChildren: boolean;
  fields: ChannelBatchField[];
  articleModelId?: string;
  channelTemplate?: string | null;
  articleTemplate?: string | null;
  processKey?: string | null;
  nav?: boolean;
  real?: boolean;
  allowSearch?: boolean;
  pageSize?: number;
}): Promise<{ updated: number }> => (await axios.post('/backend/core/channel/batch-update?_method=put', data)).data;
export const updateChannelNav = async (id: string, nav: boolean): Promise<any> => (await axios.post('/backend/core/channel/nav?_method=put', { id, nav })).data;
export const updateChannelReal = async (id: string, real: boolean): Promise<any> => (await axios.post('/backend/core/channel/real?_method=put', { id, real })).data;
export const moveChannel = async (fromId: string, toId: string, type: 'inner' | 'before' | 'after'): Promise<any> =>
  (await axios.post('/backend/core/channel/move?_method=put', { fromId, toId, type })).data;
export const batchMoveChannel = async (fromIds: string[], toId: string, type: 'inner' | 'before' | 'after'): Promise<any> =>
  (await axios.post('/backend/core/channel/batch-move?_method=put', { fromIds, toId, type })).data;
export const batchMergeChannel = async (fromIds: string[], toId: string): Promise<any> =>
  (await axios.post('/backend/core/channel/batch-merge?_method=put', { fromIds, toId })).data;
export const tidyTreeChannel = async (): Promise<any> => (await axios.post('/backend/core/channel/tidy-tree?_method=put')).data;
export const deleteChannel = async (data: string[]): Promise<any> => (await axios.post('/backend/core/channel?_method=delete', data)).data;
export const queryChannelPermissions = async (): Promise<any> => (await axios.get('/backend/core/channel/channel-permissions')).data;
export const queryChannelTemplates = async (): Promise<any> => (await axios.get('/backend/core/channel/channel-templates')).data;
export const queryArticleTemplates = async (): Promise<any> => (await axios.get('/backend/core/channel/article-templates')).data;
export const channelSlugExist = async (slug?: string): Promise<any> => (await axios.get('/backend/core/channel/slug-exist', { params: { slug } })).data;
/** 按栏目名称建议别名（拼音/转写规则由站点“固定链接”设置决定）；返回空串表示保存时回退为 ID */
export const channelSlugSuggest = async (name: string, id?: string): Promise<string> =>
  (await axios.get('/backend/core/channel/slug-suggest', { params: { name, id } })).data?.slug ?? '';

export const queryTopicList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/topic', { params })).data;
export const queryTopic = async (id: string): Promise<any> => (await axios.get(`/backend/core/topic/${id}`)).data;
export const createTopic = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/topic', data)).data;
export const updateTopic = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/topic?_method=put', data)).data;
export const moveTopic = async (fromId: string, toId: string, type: 'inner' | 'before' | 'after'): Promise<any> =>
  (await axios.post('/backend/core/topic/move?_method=put', { fromId, toId, type })).data;
export const tidyTreeTopic = async (): Promise<any> => (await axios.post('/backend/core/topic/tidy-tree?_method=put')).data;
export const deleteTopic = async (data: string[]): Promise<any> => (await axios.post('/backend/core/topic?_method=delete', data)).data;
export const queryTopicTemplates = async (): Promise<any> => (await axios.get('/backend/core/topic/topic-templates')).data;
export const topicSlugExist = async (slug?: string): Promise<any> => (await axios.get('/backend/core/topic/slug-exist', { params: { slug } })).data;
export const topicSlugSuggest = async (title: string, id?: string): Promise<string> =>
  (await axios.get('/backend/core/topic/slug-suggest', { params: { title, id } })).data?.slug ?? '';

export const queryArticlePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/article', { params })).data;
/** 导出的一列：键与表头文字 */
export interface ArticleExportColumn {
  key: string;
  label: string;
}
/**
 * 导出文章列表为 .xlsx。params 与列表相同（查询键、所选栏目）；ids 只导出勾选的行；labels 是状态、类型、是否的文字。
 * 响应是文件（blob），出错时错误体同为 Blob：跳过全局弹窗，调用方 catch 后交给 handleBlobError
 */
export const exportArticles = async (
  params: Record<string, any>,
  body: { ids?: string[]; columns: ArticleExportColumn[]; labels: Record<string, string> },
): Promise<{ blob: Blob; filename: string }> => {
  const response = await axios.post('/backend/core/article/export', body, { params, responseType: 'blob', skipErrorHandler: true, timeout: 600000 } as any);
  const filename = parseAttachmentFilename(response.headers?.['content-disposition'] ?? '') ?? 'articles.xlsx';
  return { blob: response.data, filename };
};
/** 文章批量导入（T1）：下载模板。给了栏目时，模板带上该栏目文章模型的自定义字段列。响应是文件，出错时交给 handleBlobError */
export const downloadArticleImportTemplate = async (channelId?: string): Promise<{ blob: Blob; filename: string }> => {
  const response = await axios.get('/backend/core/article-import/template', { params: { channelId }, responseType: 'blob', skipErrorHandler: true } as any);
  const filename = parseAttachmentFilename(response.headers?.['content-disposition'] ?? '') ?? 'article-import-template.xlsx';
  return { blob: response.data, filename };
};
const articleImportForm = (file: File, channelId: string | undefined, skipDuplicates: boolean): FormData => {
  const form = new FormData();
  form.append('file', file);
  if (channelId != null) {
    form.append('channelId', channelId);
  }
  form.append('skipDuplicates', String(skipDuplicates));
  return form;
};
/** 预检：只读，返回可导入的行数与有问题的行 */
export const previewArticleImport = async (file: File, channelId: string | undefined, skipDuplicates: boolean): Promise<any> =>
  (await axios.post('/backend/core/article-import/preview', articleImportForm(file, channelId, skipDuplicates), { timeout: 120000 })).data;
/** 执行：启动后台任务，返回 { taskId } */
export const startArticleImport = async (file: File, channelId: string | undefined, skipDuplicates: boolean): Promise<any> =>
  (await axios.post('/backend/core/article-import', articleImportForm(file, channelId, skipDuplicates), { timeout: 120000 })).data;
export const queryArticleImportTask = async (taskId: string): Promise<any> => (await axios.get(`/backend/core/article-import/task/${taskId}`)).data;
export const stopArticleImportTask = async (taskId: string): Promise<any> => (await axios.post(`/backend/core/article-import/task/${taskId}/stop`)).data;
/** 各状态的文章数（状态页签上的计数）：参数与列表相同，服务端忽略其中的状态条件与排序。静默失败——计数出不来不该挡住列表 */
export const queryArticleStatusCounts = async (params?: Record<string, any>): Promise<{ status: number; total: number }[]> =>
  (await axios.get('/backend/core/article/status-counts', { params, skipErrorHandler: true } as any)).data;
export const queryArticleRejectCount = async (params?: Record<string, any>, quiet = false): Promise<any> => (await axios.get('/backend/core/article/reject-count', { params, skipErrorHandler: quiet } as any)).data;
export const queryArticle = async (id: string): Promise<any> => (await axios.get(`/backend/core/article/${id}`)).data;
/** 文章别名是否已被占用（id 排除自身） */
export const articleSlugExist = async (slug?: string, id?: string): Promise<any> => (await axios.get('/backend/core/article/slug-exist', { params: { slug, id } })).data;
/** 文章保存前的词库检查：一次返回各字段的全部命中。失败不弹全局错误（skipErrorHandler）——调用方放行保存，敏感词由服务端保存时拦截 */
export const checkArticleWords = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/word-check', data, { skipErrorHandler: true } as any)).data;
/** 按标题建议文章别名；返回空串表示保存时回退为文章 ID（如纯中文标题且未开启转拼音） */
export const articleSlugSuggest = async (title: string, id?: string): Promise<string> =>
  (await axios.get('/backend/core/article/slug-suggest', { params: { title, id } })).data?.slug ?? '';
export const queryArticleTitleSimilarity = async (similarity: number, title: string, excludeId?: string): Promise<any> =>
  (await axios.get('/backend/core/article/title-similarity', { params: { similarity, title, excludeId } })).data;
/** 相关文章启发式候选（docs/spec/article-related/plan.md §3）：id 为已保存文章；新文章传 title/channelId/tagNames */
export const queryArticleRelatedCandidates = async (params: Record<string, any>): Promise<any[]> =>
  (await axios.get('/backend/core/article/related-candidates', { params })).data;
export const createArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/article', data)).data;
export const updateArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/article?_method=put', data)).data;
export const updateArticleOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/core/article/update-order', { fromId, toId })).data;
export const internalPushArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/article/internal-push', data)).data;
export const externalPushArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/article/external-push', data)).data;
export const stickyArticle = async (ids: string[], sticky: number, stickyDate?: Date): Promise<any> =>
  (await axios.post('/backend/core/article/sticky?_method=put', { ids, sticky, stickyDate })).data;
export const deleteArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article/delete?_method=put', data)).data;
export const submitArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article/submit?_method=put', data)).data;
export const archiveArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article/archive?_method=put', data)).data;
export const offlineArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article/offline?_method=put', data)).data;
export const completelyDeleteArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article?_method=delete', data)).data;
/** 从回收站恢复为草稿 */
export const restoreArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article/restore?_method=put', data)).data;
/** 批量移动栏目：不合条件的文章（审核中、不可编辑、文章模型不同、目标栏目里已有同一篇）略过，返回移动与略过的篇数 */
export const moveArticle = async (ids: string[], channelId: string): Promise<{ moved: number; skipped: number }> =>
  (await axios.post('/backend/core/article/move?_method=put', { ids, channelId })).data.result;

// 文章翻译等价集（multilingual P5；译文草稿与过期状态见 translation-workflow.md）
export const queryArticleI18n = async (articleId: string): Promise<any> => (await axios.get(`/backend/core/article-i18n/${articleId}`)).data;
export const updateArticleI18n = async (articleId: string, data: Record<string, string | null>): Promise<any> =>
  (await axios.post(`/backend/core/article-i18n/${articleId}?_method=put`, data)).data;
export const queryArticleI18nCandidates = async (params: Record<string, any>): Promise<any> =>
  (await axios.get('/backend/core/article-i18n/candidates', { params })).data;
export const createArticleTranslation = async (articleId: string, data: Record<string, any>): Promise<any> =>
  (await axios.post(`/backend/core/article-i18n/${articleId}/translation`, data)).data;
export const queryArticleI18nStatus = async (ids: string[]): Promise<any> =>
  (await axios.get('/backend/core/article-i18n/status', { params: { ids: ids.join(',') } })).data;

// 批量新增译文（商业版 T2，docs/spec/ai/article-translate.md §7；控制台原“批量翻译”）：预览计数 → 提交后台任务 → 轮询进度/停止
export const previewArticleTranslateBatch = async (params: Record<string, any>): Promise<any> =>
  (await axios.get('/backend/ext/ai/translate-batch/preview', { params })).data;
export const startArticleTranslateBatch = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/ext/ai/translate-batch', data)).data;
export const queryArticleTranslateBatchTask = async (taskId: string): Promise<any> =>
  (await axios.get(`/backend/ext/ai/translate-batch/task/${taskId}`)).data;
export const stopArticleTranslateBatchTask = async (taskId: string): Promise<any> =>
  (await axios.post(`/backend/ext/ai/translate-batch/task/${taskId}/stop`)).data;

// 栏目批量新增译文（商业版 T2，docs/spec/ai/article-translate.md §7.1）：在源语言站点比对目标站点 → 新建缺少的同别名栏目，
// 翻译新建栏目与仍相同的名称、SEO 字段与正文（可仅新建）
export const previewChannelTranslateBatch = async (params: Record<string, any>): Promise<any> =>
  (await axios.get('/backend/ext/ai/translate-channels/preview', { params })).data;
// 名称与 SEO 字段一次调用数秒到数十秒；正文逐栏目调用并在请求内完成，单页栏目多时以分钟计（axios 默认 30s 远不够）
export const executeChannelTranslateBatch = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/ext/ai/translate-channels', data, { timeout: 600_000 })).data;

export const queryArticleTopicList = async (topicId: string): Promise<any> => (await axios.get('/backend/core/article-topic', { params: { topicId } })).data;
export const replaceArticleTopicList = async (topicId: string, articleIds: string[], orders?: number[]): Promise<any> =>
  (await axios.post(`/backend/core/article-topic/by-topic/${topicId}?_method=put`, { articleIds, orders })).data;

export const queryArticleReviewPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/article-review', { params })).data;
export const queryArticlePendingCount = async (params?: Record<string, any>, quiet = false): Promise<any> => (await axios.get('/backend/core/article-review/pending-count', { params, skipErrorHandler: quiet } as any)).data;
export const queryArticleReview = async (id: string): Promise<any> => (await axios.get(`/backend/core/article-review/${id}`)).data;
export const passArticles = async (data: string[]): Promise<any> => (await axios.post('/backend/core/article-review/pass?_method=put', data)).data;
export const passArticle = async (taskId: string, properties: Record<string, string>, comment: string): Promise<any> =>
  (await axios.post(`/backend/core/article-review/pass/${taskId}?_method=put`, { properties, comment })).data;
export const delegateArticle = async (taskId: string, toUserId: string, comment: string): Promise<any> =>
  (await axios.post(`/backend/core/article-review/delegate?_method=put`, { taskId, toUserId, comment })).data;
export const transferArticle = async (taskId: string, toUserId: string, comment: string): Promise<any> =>
  (await axios.post(`/backend/core/article-review/transfer?_method=put`, { taskId, toUserId, comment })).data;
export const backArticle = async (taskId: string, activityId: string, comment: string): Promise<any> =>
  (await axios.post(`/backend/core/article-review/back?_method=put`, { taskId, activityId, comment })).data;
export const rejectArticle = async (taskIds: string[], reason: string): Promise<any> =>
  (await axios.post('/backend/core/article-review/reject?_method=put', { taskIds, reason })).data;

export const queryBlockItemList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/block-item', { params })).data;
export const queryBlockItem = async (id: string): Promise<any> => (await axios.get(`/backend/core/block-item/${id}`)).data;
export const createBlockItem = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/block-item', data)).data;
export const updateBlockItem = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/block-item?_method=put', data)).data;
export const updateBlockItemOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/core/block-item/update-order', { fromId, toId })).data;
export const deleteBlockItem = async (data: string[]): Promise<any> => (await axios.post('/backend/core/block-item?_method=delete', data)).data;

export const queryLinkList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/link', { params })).data;
export const queryLink = async (id: string): Promise<any> => (await axios.get(`/backend/ext/link/${id}`)).data;
export const createLink = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/link', data)).data;
export const updateLink = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/link?_method=put', data)).data;
export const updateLinkOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/link/update-order', { fromId, toId })).data;
export const deleteLink = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/link?_method=delete', data)).data;

export const queryLinkGroupList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/link-group', { params })).data;
export const queryLinkGroup = async (id: string): Promise<any> => (await axios.get(`/backend/ext/link-group/${id}`)).data;
export const createLinkGroup = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/link-group', data)).data;
export const updateLinkGroup = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/link-group?_method=put', data)).data;
export const updateLinkGroupOrder = async (fromId: string, toId: string): Promise<any> =>
  (await axios.post('/backend/ext/link-group/update-order', { fromId, toId })).data;
export const deleteLinkGroup = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/link-group?_method=delete', data)).data;
export const linkGroupAliasExist = async (alias: string): Promise<any> => (await axios.get('/backend/ext/link-group/alias-exist', { params: { alias } })).data;
export const queryLinkGroupSlots = async (): Promise<any> => (await axios.get('/backend/ext/link-group/slots')).data;

export const fulltextReindexAll = async (): Promise<any> => (await axios.post('/backend/core/generator/fulltext-reindex-all')).data;
export const fulltextReindexSite = async (): Promise<any> => (await axios.post('/backend/core/generator/fulltext-reindex-site')).data;
export const htmlAll = async (): Promise<any> => (await axios.post('/backend/core/generator/html-all')).data;
export const htmlAllHome = async (): Promise<any> => (await axios.post('/backend/core/generator/html-all-home')).data;
export const htmlHome = async (): Promise<any> => (await axios.post('/backend/core/generator/html-home')).data;
export const htmlChannel = async (): Promise<any> => (await axios.post('/backend/core/generator/html-channel')).data;
export const htmlArticle = async (): Promise<any> => (await axios.post('/backend/core/generator/html-article')).data;

export const queryTagPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/tag', { params })).data;
export const queryTagList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/tag/list', { params })).data;
export const queryTag = async (id: string): Promise<any> => (await axios.get(`/backend/core/tag/${id}`)).data;
export const createTag = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/tag', data)).data;
export const updateTag = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/tag?_method=put', data)).data;
export const deleteTag = async (data: string[]): Promise<any> => (await axios.post('/backend/core/tag?_method=delete', data)).data;

export const queryFormPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/form', { params })).data;
export const queryForm = async (id: string): Promise<any> => (await axios.get(`/backend/ext/form/${id}`)).data;
export const queryFormRejectCount = async (params?: Record<string, any>, quiet = false): Promise<any> => (await axios.get('/backend/ext/form/reject-count', { params, skipErrorHandler: quiet } as any)).data;
export const createForm = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/form', data)).data;
export const updateForm = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/form?_method=put', data)).data;
export const updateFormOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/form/update-order', { fromId, toId })).data;
export const deleteForm = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/form/delete?_method=put', data)).data;
export const submitForm = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/form/submit?_method=put', data)).data;
export const completelyDeleteForm = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/form?_method=delete', data)).data;

export const queryFormReviewPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/form-review', { params })).data;
export const queryFormReviewTypeList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/form-review/type-list', { params })).data;
export const queryFormPendingCount = async (params?: Record<string, any>, quiet = false): Promise<any> => (await axios.get('/backend/ext/form-review/pending-count', { params, skipErrorHandler: quiet } as any)).data;
export const queryFormReview = async (id: string): Promise<any> => (await axios.get(`/backend/ext/form-review/${id}`)).data;
export const passForms = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/form-review/pass?_method=put', data)).data;
export const passForm = async (taskId: string, properties: Record<string, string>, comment: string): Promise<any> =>
  (await axios.post(`/backend/ext/form-review/pass/${taskId}?_method=put`, { properties, comment })).data;
export const delegateForm = async (taskId: string, toUserId: string, comment: string): Promise<any> =>
  (await axios.post(`/backend/ext/form-review/delegate?_method=put`, { taskId, toUserId, comment })).data;
export const transferForm = async (taskId: string, toUserId: string, comment: string): Promise<any> =>
  (await axios.post(`/backend/ext/form-review/transfer?_method=put`, { taskId, toUserId, comment })).data;
export const backForm = async (taskId: string, activityId: string, comment: string): Promise<any> =>
  (await axios.post(`/backend/ext/form-review/back?_method=put`, { taskId, activityId, comment })).data;
export const rejectForm = async (taskIds: string[], reason: string): Promise<any> => (await axios.post('/backend/ext/form-review/reject?_method=put', { taskIds, reason })).data;

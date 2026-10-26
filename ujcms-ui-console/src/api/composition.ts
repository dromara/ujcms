import axios from '@/utils/request';

// 组件（Shape，只读目录）
export const queryShapeList = async (): Promise<any> => (await axios.get('/backend/core/shape')).data;
export const queryShape = async (name: string): Promise<any> => (await axios.get(`/backend/core/shape/${name}`)).data;
// 静默查询 Shape（跳过全局错误弹窗）：先按命名空间链解析（与区域保存/渲染同口径——PanelForm
// 渲染的设置结构必与保存校验所用为同一 Shape，component-settings §8 Phase C），链上无命中再回退
// 任意命名空间。确实不可解析（404）或瞬时失败时 PanelForm 降级为设置不可编辑（仅启用开关），
// 不打扰操作员。
export const queryShapeSilent = async (name: string): Promise<any> =>
  (await axios.get(`/backend/core/shape/${name}`, { skipErrorHandler: true } as any)).data;
// 使用情况报告（§11.3）：allowedShapes 引用该组件的区域 + 当前站点对应面板数
export const queryShapeUsage = async (name: string): Promise<any> =>
  (await axios.get(`/backend/core/shape/${encodeURIComponent(name)}/usage`)).data;
// 活动主题合并设置（§8.2 公开接口）：themeColor 字段的调色板色板取 palette 分组。
// 跳过全局错误弹窗——站点未解析（无 siteId 且公开端点无当前站点）/ 主题无设置 时静默降级为无色板
// 令牌下拉（§11.6 文档所述的优雅降级），不打扰操作员。
export const queryThemeConfig = async (siteId?: string): Promise<any> =>
  (await axios.get('/theme-config', { params: siteId != null ? { siteId } : {}, skipErrorHandler: true } as any)).data;

// 构成健康（§11.5）
export const queryCompositionHealth = async (): Promise<any> => (await axios.get('/backend/core/composition/health')).data;
// 修复种子令牌（theme-seed-export R1）：对 D9 之前导入遗留的未展开 channel/article/asset/theme
// 令牌字面量按导入约定就地重跑展开；显式操作员动作（绝不启动自愈）
export const repairSeedTokens = async (): Promise<any> => (await axios.post('/backend/core/composition/repair-seed-tokens')).data;

// 页面目录（页面构成：活动主题的页面清单聚合）
export const queryCompositionPageList = async (): Promise<any> => (await axios.get('/backend/core/composition/page')).data;
export const rescanComposition = async (): Promise<any> => (await axios.post('/backend/core/composition/rescan')).data;

// 面板反向查询（theme-marketplace §7.2 来源筛选片：origin 如 theme:default）
export const queryPanelsByOrigin = async (origin: string): Promise<any> =>
  (await axios.get('/backend/core/page-region/panel', { params: { origin } })).data;
// 健康页一键恢复：取消归档面板（§11.5）
export const restorePanel = async (id: string): Promise<any> =>
  (await axios.post(`/backend/core/page-region/panel/${id}/restore`)).data;
// 健康页逐行删除归档面板（仅限已归档行；主题切换孤立的面板不再需要时清理）
export const deleteArchivedPanel = async (id: string): Promise<any> =>
  (await axios.post(`/backend/core/page-region/panel/${id}?_method=delete`)).data;
// 健康页清空归档面板（D21）：树先行剔除引用后删除本站全部已归档行，返回 {deleted}
export const deleteAllArchivedPanels = async (): Promise<any> =>
  (await axios.post('/backend/core/page-region/panel/archived?_method=delete')).data;
// 健康页清理悬空引用（显式修复动作）：剔除结构树中指向已不存在投放行的引用，返回 {pruned}
export const pruneDanglingRefs = async (): Promise<any> =>
  (await axios.post('/backend/core/page-region/prune-dangling')).data;

// 页面区域（page-regions §9）：行/列/面板的二维结构编排。面板即投放（Panel = Placement），随结构树一并保存。
// 当前站点已声明的全部区域（PageList「编辑布局」入口）
export const queryRegionList = async (): Promise<any> => (await axios.get('/backend/core/page-region/list')).data;
// 区域详情：已发布树 + 草稿树 + 本区域全部面板（含配置与卡片ID）；scope 指定覆盖范围（visual-builder §9，缺省页面默认）
export const queryRegion = async (page: string, region: string, scope?: string): Promise<any> =>
  (await axios.get('/backend/core/page-region', { params: { page, region, scope } })).data;
// 页面聚合详情（visual-builder §7.1）：页面全部区域详情 + 调色板 Shape 目录，一次调用完成构建器装载；
// scope 指定覆盖范围（§9），响应的 scopes 为本页已存在的覆盖范围目录（范围切换器）
export const queryPageDetail = async (page: string, scope?: string): Promise<any> =>
  (await axios.get('/backend/core/page-region/page-detail', { params: { page, scope } })).data;
// 行布局目录（§6）：固定代码内置枚举
export const queryRowLayouts = async (): Promise<any> => (await axios.get('/backend/core/page-region/row-layouts')).data;
// 保存草稿：事务内 upsert 面板投放 + 写 draft_layout_json_（面板ID/卡片ID 保持字符串——雪花ID超过 JS 安全整数）。
// 跳过全局错误弹窗——409 乐观锁冲突由编辑器提供 覆盖/刷新 选择（与 updateCardRaw 同型）
export const saveRegionDraft = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/page-region?_method=put', data, { skipErrorHandler: true } as any)).data;
// 发布：草稿树 → 已发布树。响应 {version}（发布追加历史快照并递增版本，region-history）；
// 可选 label 为历史条目备注
export const publishRegion = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/page-region/publish', data)).data;
// 丢弃草稿
export const discardRegion = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/page-region/discard', data)).data;
// 区域发布快照历史（region-history §7）：坐标下条目新→旧（不含物化内容）
export const queryRegionHistory = async (page: string, region: string, scope?: string): Promise<any> =>
  (await axios.get('/backend/core/page-region/history', { params: { page, region, scope } })).data;
// 恢复历史快照：恢复 = 一次新发布；存在草稿时须显式 discardDraft: true；响应 {version}
export const restoreRegionSnapshot = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/page-region/restore', data)).data;
// 删除覆盖行（visual-builder §9 revert-to-default）：该范围恢复为页面默认；{ page, region, scope, version }
export const deleteRegionOverride = async (data: Record<string, any>): Promise<any> =>
  (await axios.post('/backend/core/page-region/delete-override', data)).data;

// 区域预览令牌（Phase 7 / §11.2）：签发短时令牌，构造 iframe 预览 URL 的 ?_preview=draft&_ptoken=
export interface PreviewTokenResult {
  token: string;
  expiresIn: number;
}
export const previewToken = async (): Promise<PreviewTokenResult> =>
  (await axios.get('/backend/core/composition/preview-token')).data;

// 代表性预览地址（preview-url-resolver §4.1）：给定（页面, [变体], [栏目], [q]）产出候选 URL。
// 构建器默认路径 / 路径选择器 / 范围自动寻址的数据源。
// page 与 template 二选一：template 按模板名反查（P3 模板列表预览按钮——清单正向绑定 + 内置固定路由表
// + 实例页来源 + FormType 反查；P4 无候选时带原因码 emptyReason 与主体 emptySubject）
export type PreviewUrlKind =
  | 'fixed'
  | 'channel'
  | 'article'
  | 'topic'
  | 'form'
  | 'tag'
  | 'vote'
  | 'messageBoard'
  | 'user'
  | 'survey'
  | 'mailbox'
  | 'interview';
export interface PreviewUrlCandidate {
  kind: PreviewUrlKind;
  label: string;
  url: string;
  entityId: string | null;
  template: string | null;
  variant: string | null;
  /** 页面需会员登录（isAuthenticated 门禁）：匿名打开会被送去登录页，须以预览令牌身份打开 */
  loginRequired: boolean;
}
/**
 * 空态原因：no-content 缺内容（emptySubject 说明缺哪类）；no-manifest 活动主题未声明该页面（仅页面名解析）；
 * tier-locked 页面由更高版本渲染；disabled 所属功能未启用（emptySubject 说明哪项）；unsupported 没有页面渲染该模板
 */
export type PreviewEmptyReason = 'no-content' | 'no-manifest' | 'tier-locked' | 'disabled' | 'unsupported';
export interface PreviewUrlsResult {
  candidates: PreviewUrlCandidate[];
  emptyReason: PreviewEmptyReason | null;
  emptySubject: string | null;
}
export const queryPreviewUrls = async (params: {
  page?: string;
  template?: string;
  variant?: string;
  channelId?: string;
  q?: string;
  limit?: number;
}): Promise<PreviewUrlsResult> => (await axios.get('/backend/core/composition/preview-urls', { params })).data;

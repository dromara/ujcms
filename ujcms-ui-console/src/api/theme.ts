import axios from '@/utils/request';

/**
 * Phase 5 marketplace installer admin API.
 *
 * Backend mapping: `com.ujcms.cms.core.web.backendapi.ThemeController`.
 * Endpoints expect the operator to be authenticated as a backend user; per-action permissions
 * gate `theme:list`, `theme:install`, `theme:uninstall`, `theme:activate` (all bypassed by `*`).
 */

export interface ThemeMetadataAuthor {
  name?: string;
  email?: string;
  url?: string;
}

export interface ThemeMetadata {
  name?: string;
  themeName?: string;
  version?: string;
  templateSpecVersion?: string;
  /** 包类型（manifest 2.0 §8）：theme（缺省）| data-only（纯数据包，A5 起控制台隐藏激活） */
  kind?: string;
  author?: ThemeMetadataAuthor;
  description?: string;
  license?: string;
  homepage?: string;
  changelogUrl?: string;
  preview?: string;
  screenshots?: string[];
  tags?: string[];
  compatibleSiteTypes?: string[];
  minUjcmsVersion?: string;
}

export interface ThemeInfo {
  themeName: string;
  displayName: string;
  active: boolean;
  activeMobile: boolean;
  version?: string;
  metadata?: ThemeMetadata;
  hasManifest: boolean;
  /** 主题包内是否带可导入种子（seed/ 目录存在） */
  seedAvailable: boolean;
  /** 历史标记：种子是否导入过（ujcms_theme_state.seed_imported_，移除演示数据不重置） */
  seedImported: boolean;
  /** 在库实况：本主题的本源行（内容或构成任一表）是否仍在库 */
  demoDataPresent: boolean;
  /** 是否已登记（install-flow §14：theme_state 行在场）。未登记目录功能完好，缺的是来历与包库可移植性 */
  registered: boolean;
}

export interface ThemeInstallReport {
  templateFileCount: number;
  totalUncompressedSize: number;
  hasManifest: boolean;
  metadata?: ThemeMetadata;
  warnings: string[];
  /** 上传 ZIP 的 SHA-256（十六进制小写；P3-1/H3——供与发布方公示值带外比对，无签名机制） */
  sha256?: string;
  /** 随安装执行的种子导入结果（seedScope=none 时为 null；失败明细走 seed.content.failures，§8.1/D22） */
  seed?: SeedRunResult | null;
}

export interface ThemeLimits {
  maxSizeMb: number;
  /** 模板存储公开 URL 前缀：画廊按 {templatesUrl}/{siteId}/{themeName}/{preview} 拼截图地址 */
  templatesUrl?: string;
}

/** 主题页面清单摘要（A4 详情页「页面与插槽」页签，只读） */
export interface ThemePageSlotInfo {
  name: string;
  /** slot（经典插槽）| region（区域） */
  kind: string;
  shape: string;
  cardinality: string;
  required: boolean;
}

export interface ThemePageInfo {
  name: string;
  label: Record<string, string>;
  templates: string[];
  route?: string | null;
  slots: ThemePageSlotInfo[];
  /** 页面清单是否声明 settings 块（§4.2） */
  hasSettings: boolean;
}

/** 激活目标（theme-marketplace §3 R8，替代 alsoMobile 布尔）：桌面槽位 / 手机槽位 / 两者；仅手机不动桌面主题 */
export type ActivateTarget = 'desktop' | 'mobile' | 'both';
export type UninstallDataPolicy = 'KEEP' | 'ARCHIVE' | 'DELETE';
/** 种子导入范围（R5，替代 importSeed 布尔）：none | composition | all（all 随内容种子 A2 生效） */
export type SeedScope = 'none' | 'composition' | 'all';
/** 种子覆盖策略（§6.1，A3）：只填空（缺省）/ 仅刷新未改动的本源行 / 重置全部本源行 */
export type SeedOverwrite = 'fillOnly' | 'syncUnmodified' | 'resetAllOrigin';

/** 单类别种子干跑计数（§6.2） */
export interface SeedCategoryPlan {
  new: number;
  linked: number;
  existing: number;
  operatorModified: number;
  seedChanged: number;
}

/** 种子导入干跑计划（§6.2 seed/preview——各确认对话框的计数来源）。长整型经 JSON 序列化为字符串。 */
export interface SeedPlan {
  channels: SeedCategoryPlan;
  articles: SeedCategoryPlan;
  topics: SeedCategoryPlan;
  /** 区块定义计划（E4a / D25——仅定义组；条目组是 A6 填空形不入计划） */
  blocks: SeedCategoryPlan;
  /** 投票定义计划（E4b / D29——文章式，沿用既有恒为 0） */
  votes: SeedCategoryPlan;
  /** 问卷定义计划（E4d / D35——文章式；干跑只读，低配站点照常对比） */
  surveys: SeedCategoryPlan;
  /** 表单类型定义计划（E4e / D38——文章式；干跑只读，低配站点照常对比） */
  forms: SeedCategoryPlan;
  /** 友情链接分组计划（E4f / D42——区块式，别名撞既有分组 → linked；链接随定义不入计划） */
  linkGroups: SeedCategoryPlan;
  /** 模型定义计划（model-seeding D1——(type, name) 撞可见集 → linked，出厂同名即常态） */
  models: SeedCategoryPlan;
  /** 字典类型定义计划（model-seeding D8——别名撞可见集 → linked；条目随类型） */
  dicts: SeedCategoryPlan;
  panels: SeedCategoryPlan;
  regions: SeedCategoryPlan;
  assetBytes: string | number;
  collisions: { category: string; originId: string; alias: string; existingId: string }[];
}

/** 单类内容导入计数（imported/skipped/linked/failed + A3 updated） */
export interface SeedCategoryResult {
  imported: number;
  skipped: number;
  linked: number;
  failed: number;
  updated: number;
}

/** 实体级失败明细（theme-seed-export §8.1 / D22）：reason 为可译原因码（theme.seed.failure.*） */
export interface SeedImportFailure {
  /** 复数类别键（channels|articles|topics|blockItems——直接复用 theme.seed.cat.* 文案） */
  category: string;
  /** 失败实体的引用（originId / 别名 / 别名+文章originId） */
  ref: string;
  /** 原因码（topicModelMissing 等；未知异常为 unknown） */
  reason: string;
  /** 英文详情（未登记原因码时的展示回退） */
  detail: string;
}

/** 种子导入合并结果（内容 + 构成，§5.4 v2；blockItems 为 A6 权宜类别：恒只填空） */
export interface SeedRunResult {
  content: {
    channels: SeedCategoryResult;
    articles: SeedCategoryResult;
    topics: SeedCategoryResult;
    /** 区块定义计数（E4a：来源三元组 upsert，别名冲突 → linked） */
    blocks: SeedCategoryResult;
    /** 投票定义计数（E4b：文章式来源三元组 upsert，无别名） */
    votes: SeedCategoryResult;
    /** 问卷定义计数（E4d：写入经核心 SurveyService，任何版本都落库——D46） */
    surveys: SeedCategoryResult;
    /** 表单类型定义计数（E4e：写入经核心 FormTypeService，任何版本都落库——D46） */
    forms: SeedCategoryResult;
    /** 友情链接分组计数（E4f：来源三元组 upsert，别名撞既有分组 → linked；链接随定义） */
    linkGroups: SeedCategoryResult;
    /** 模型定义计数（model-seeding：字典 → 模型先于一切内容导入，(type, name) 冲突 → linked） */
    models: SeedCategoryResult;
    /** 字典类型定义计数（model-seeding：别名冲突 → linked；条目随类型） */
    dicts: SeedCategoryResult;
    blockItems: SeedCategoryResult;
    /** 友情链接条目计数（E4f：随分组定义出入；linked 分组按 (typeId, url) 填空） */
    linkItems: SeedCategoryResult;
    assetsCopied: number;
    /** 失败明细（至多 50 条；计数以各类别 failed 为准） */
    failures: SeedImportFailure[];
  };
  composition: {
    panelsImported: number;
    panelsSkipped: number;
    panelsUpdated: number;
    /** 只填空下修复失效引用的既有本源面板数（D24：散列证明未动过、引用目标已删——如 移除→导入 后的死引用，按种子重展开） */
    panelsRepaired: number;
    regionsImported: number;
    /** 结构重建的既有本源区域数（D22：还原恒重建，更新未修改项在散列证明结构未改且种子有变时重建） */
    regionsReset: number;
    regionsSkipped: number;
    /** 因区域插槽未在对应清单声明而跳过的区域（page#region；计入 regionsSkipped，H5） */
    undeclaredSlots: string[];
    /** 因区域为全站共享引用而跳过的区域（page#region->owner；种子应放在宿主页，page-regions §15） */
    sharedSlots: string[];
    /** 面板级失败明细（R2 通道对构成的延伸：引用令牌不可解析 → 整区域跳过/刷新面板跳过，至多 50 条） */
    failures: SeedImportFailure[];
  };
}

/** 演示数据移除结果 / 干跑计数（§6.1；移除即物理删除，回收模式已裁撤） */
export interface SeedRemoveResult {
  dryRun: boolean;
  /** 物理删除的文章数（含回收站中的本源文章） */
  articlesDeleted: number;
  /** 失败文章数（来源标记保留，重跑可续） */
  articlesFailed: number;
  channelsDeleted: number;
  channelsOriginCleared: number;
  topicsDeleted: number;
  topicsOriginCleared: number;
  /** 删除的区块定义数（守卫通过：本次处置后无任何条目，E4a / D26） */
  blocksDeleted: number;
  /** 清除来源标记的区块定义数（守卫不过：仍有条目，转为普通操作员行） */
  blocksOriginCleared: number;
  /** 删除的投票定义数（守卫通过：无选票记录，E4b / D31） */
  votesDeleted: number;
  /** 清除来源标记的投票定义数（守卫不过：已有用户参与，转为普通操作员行） */
  votesOriginCleared: number;
  /** 删除的问卷定义数（守卫通过：无反馈记录，E4d / D35） */
  surveysDeleted: number;
  /** 清除来源标记的问卷定义数（守卫不过或低配跳过） */
  surveysOriginCleared: number;
  /** 删除的表单类型定义数（守卫通过：无用户提交，E4e / D40） */
  formsDeleted: number;
  /** 清除来源标记的表单类型定义数（守卫不过或低配跳过） */
  formsOriginCleared: number;
  /** 删除的友情链接分组数（守卫通过：分组连同链接仍为种子形，E4f / D44；链接随分组删除） */
  linkGroupsDeleted: number;
  /** 清除来源标记的友情链接分组数（守卫不过：操作员改过分组或链接，转为普通操作员行） */
  linkGroupsOriginCleared: number;
  /** 删除的模型定义数（守卫通过：本次处置后无任何幸存行绑定，model-seeding D9） */
  modelsDeleted: number;
  /** 清除来源标记的模型定义数（守卫不过：仍被幸存栏目/专题/区块绑定，转为普通操作员行） */
  modelsOriginCleared: number;
  /** 删除的字典类型定义数（守卫通过：无幸存模型引用；条目随定义清空，D8/D9） */
  dictsDeleted: number;
  /** 清除来源标记的字典类型定义数（守卫不过：仍被幸存模型字段引用） */
  dictsOriginCleared: number;
  /** 设置里钉住了将删实体（栏目/文章/区块/投票）ID 的在用页面组件数（D24：面板不动，只告知——移除后在健康页列为失效引用，导入演示数据对未改动的本源面板自动修复） */
  panelRefsBroken: number;
}

export interface ThemeImpactReport {
  resultingThemes: string[];
  orphanedPlacements: { id: number; pageRef: string; slotName: string; component: string | null; title: string | null }[];
  /** 将自动恢复的归档组件数（页面/区域重新被声明，激活时取消归档——D21） */
  restorablePlacements: number;
  /** 孤立模板选择数（显式 *_template_ 选择在目标主题对中无对应模板文件；只计数不改写，template-track-variants D7） */
  orphanedTemplateSelections: number;
  seedAvailable: boolean;
  seedImported: boolean;
  /** 种子声明的内容条目数（三态提示的展示计数，§6.2/A3） */
  seedContent: {
    channels: number;
    articles: number;
    topics: number;
    blocks: number;
    votes: number;
    surveys: number;
    forms: number;
    linkGroups: number;
    models: number;
    dicts: number;
  };
  /** 本主题演示数据（内容+构成本源行）是否在库——重新激活时提示「刷新未修改的演示数据」 */
  demoDataPresent: boolean;
  themeShapes: number;
  themePages: number;
  settingsGroups: number;
}

/** 页面作用域设置（manifest 2.0 §4.2）：值键 pages.<name> */
export interface ThemePageSettings {
  name: string;
  label: Record<string, string>;
  groups: Record<string, any>[];
  values: Record<string, Record<string, any>>;
}

/** 变体作用域设置（§4.1/§4.2）：值键 variants.<surface>:<value> */
export interface ThemeVariantSettings {
  surface: string;
  value: string;
  label: Record<string, string>;
  groups: Record<string, any>[];
  values: Record<string, Record<string, any>>;
}

/** 主题设置保存结果（H5）：未声明键弃警不拒，dropped 列明被忽略的点路径键 */
export interface ThemeSettingsSaveResult {
  dropped: string[];
}

export interface ThemeSettingsResult {
  /** Settings group definitions (raw field defs; rendered via ShapeFieldItem). */
  groups: Record<string, any>[];
  /** Merged values: group -> field -> value. */
  values: Record<string, Record<string, any>>;
  /** Page-scope settings (pages declaring a settings block; A1). */
  pages: ThemePageSettings[];
  /** Variant-scope settings (manifest-declared layout variants with settings; A1). */
  variants: ThemeVariantSettings[];
}

export interface ThemeUpdateReport {
  themeShapes: number;
  themePages: number;
  settingsGroups: number;
  /** 逐类别种子差异（换包后、导种子前：new / seedChanged / operatorModified，§6.2/A3——取代 operatorEditedCards） */
  seedDiff: SeedPlan;
  /** 本次种子导入结果（seedScope=none 时为 null） */
  seed?: SeedRunResult | null;
  /** 上传 ZIP 的 SHA-256（十六进制小写；P3-1/H3——供与发布方公示值带外比对，无签名机制） */
  sha256?: string;
}

export const themeInstallUrl = `${import.meta.env.VITE_BASE_API}/backend/core/theme/install`;
export const themeUpdateUrl = (siteId: string | number, themeName: string) =>
  `${import.meta.env.VITE_BASE_API}/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/update`;

export const queryThemeLimits = async (): Promise<ThemeLimits> => (await axios.get('/backend/core/theme/limits')).data;

export const queryThemeList = async (siteId: string | number): Promise<ThemeInfo[]> => (await axios.get(`/backend/core/theme/${siteId}`)).data;

/** 主题页面清单（A4 详情页，只读；文件即事实——非活动主题同样可查） */
export const queryThemePages = async (siteId: string | number, themeName: string): Promise<ThemePageInfo[]> =>
  (await axios.get(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/pages`)).data;

/** 目录登记报告（install-flow §14）：入库行摘要 + 幂等跳过标记 + 校验器警告 */
export interface ThemeDirRegisterReport {
  packageId: string;
  name: string;
  version: string;
  sha256: string;
  size: string;
  /** 幂等跳过：库行 + ZIP + 状态行俱在且未漂移，本次零写入 */
  alreadyRegistered: boolean;
  warnings: string[];
}

/**
 * 目录登记（install-flow §14 / D41-D43）：把无来历的已装主题目录确定性重打包过真校验器后入库
 * （来源 registered）并补 theme_state 行。种子从不随登记导入；权限沿用 theme:install。
 */
export const registerThemeDir = async (siteId: string | number, themeName: string): Promise<ThemeDirRegisterReport> =>
  (await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/register`, undefined, { timeout: 120000 })).data;

export const uninstallTheme = async (siteId: string | number, themeName: string, dataPolicy: UninstallDataPolicy = 'KEEP'): Promise<any> =>
  (await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}?_method=delete&dataPolicy=${dataPolicy}`)).data;

export const previewActivateTheme = async (siteId: string | number, themeName: string, target: ActivateTarget): Promise<ThemeImpactReport> =>
  (await axios.post(`/backend/core/theme/${siteId}/preview-activate`, { themeName, target })).data;

/** 激活返回（§8.1/D22）：随激活执行的种子导入结果（seedScope=none 时 seed 为 null） */
export interface ActivateResult {
  seed?: SeedRunResult | null;
}

// 切换产生的孤立投放一律归档（可在健康页逐行恢复或删除），不再传处置策略
export const activateTheme = async (
  siteId: string | number,
  themeName: string,
  target: ActivateTarget,
  seedScope: SeedScope = 'none',
  overwrite: SeedOverwrite = 'fillOnly',
): Promise<ActivateResult> =>
  (
    await axios.post(`/backend/core/theme/${siteId}/active?_method=put`, {
      themeName,
      target,
      seedScope,
      overwrite,
    })
  ).data;

// ----- 种子生命周期（§6.2，A3）。ThemeDetail 的完整「演示数据」页签随 A4 落地，这里先提供 API 层。 -----

/** 种子导入干跑计划（只读，各确认对话框的计数来源） */
export const previewThemeSeed = async (siteId: string | number, themeName: string): Promise<SeedPlan> =>
  (await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/seed/preview`)).data;

/** 手动种子导入（与安装/激活解耦：先装码后导数、专题类失败续传） */
export const importThemeSeed = async (siteId: string | number, themeName: string, scope: SeedScope = 'all', overwrite: SeedOverwrite = 'fillOnly'): Promise<SeedRunResult> =>
  (
    await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/seed/import`, {
      scope,
      overwrite,
    })
  ).data;

/** 移除演示数据（内容本源行限定，物理删除不进回收站；dryRun=true 只返回计数——破坏性操作总是先干跑，§6.1） */
export const removeThemeSeed = async (siteId: string | number, themeName: string, dryRun = false): Promise<SeedRemoveResult> =>
  (await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/seed?_method=delete&dryRun=${dryRun}`)).data;

// ----- 种子导出（theme-seed-export §8，E2 端点 / E3 向导）。权限码 theme:export（核心全版本，D5）。 -----

/** 导出范围（§8）：内容 / 构成 / 全部（缺省） */
export type ThemeExportScope = 'content' | 'composition' | 'all';
/** 导出方式（§7.1）：就地刷新 seed/ / 纯数据包 ZIP / 完整主题包 ZIP */
export type ThemeExportMode = 'refreshSeedDir' | 'dataOnlyZip' | 'themeZip';

/** 导出选择（§3）：channelIds/topicIds 为 null 时按默认选择（本主题来源行 + 构成引用 + 闭包） */
export interface ThemeExportSelection {
  channelIds?: (string | number)[] | null;
  topicIds?: (string | number)[] | null;
  /** 问卷ID多选（E4d / D35：问卷无引用面，显式多选是操作员问卷入包的唯一通道；null = 本主题来源） */
  surveyIds?: (string | number)[] | null;
  /** 表单类型ID多选（E4e / D40：表单无引用面，显式多选是操作员表单入包的唯一通道；null = 本主题来源） */
  formTypeIds?: (string | number)[] | null;
  /** 友情链接分组ID多选（E4f / D43：友链无引用面，显式多选是操作员分组入包的唯一通道；null = 本主题来源） */
  linkGroupIds?: (string | number)[] | null;
  /** 每栏目最新发布文章上限（缺省 20；构成引用与本主题来源文章不受限，D6） */
  articleCapPerChannel?: number;
  includeDrafts?: boolean;
}

export interface ThemeExportParams {
  selection?: ThemeExportSelection | null;
  scope?: ThemeExportScope;
  mode?: ThemeExportMode;
  /** 认领：把铸造的来源三元组写回操作员行（§4.2，写库——向导须明示警告） */
  adoptOrigin?: boolean;
  /** 设置快照：把已存主题设置值写入发射清单已声明字段的 default（§7.4，只写导出产物、不写库） */
  snapshotSettings?: boolean;
  /** 包版本（缺省沿用清单当前值，从不自动递增，§7.1） */
  version?: string | null;
  /** 刷新活动主题的 seed/ 须显式确认（D12，服务端 400 兜底） */
  confirmActive?: boolean;
  /** ZIP 模式去向（install-flow §6 P3）：download（缺省，浏览器附件）/ library（存入主题包库） */
  destination?: 'download' | 'library';
}

/** destination=library 的导出响应（install-flow §6 P3：入库行摘要） */
export interface ThemeExportToLibraryResult {
  packageId: string;
  name: string;
  version: string;
  sha256: string;
  size: string;
  plan: ExportPlan;
  adopted: number;
}

/** 单类别来源分类计数（§4 四类 + linked 跳过；linked 不导出） */
export interface ExportCategoryCounts {
  originUnmodified: number;
  originModified: number;
  operatorNew: number;
  foreignOrigin: number;
  linkedSkipped: number;
}

/** 设置快照报告（§7.4 / D21）：预览恒附——「若快照会写什么」，第 3 步开关只决定是否落笔 */
export interface ExportSettingsSnapshot {
  /** 可写入的已声明字段值数 */
  fields: number;
  /** 未声明即丢弃的已存键（点路径，H5 口径） */
  dropped: string[];
  /** 涉及的清单文件（theme.manifest.json / pages/<file>） */
  manifests: string[];
  /** 可移植性提示（本站存储 URL / 站内ID引用——原样快照，跨站降级） */
  issues: { category: string; ref: string; detail: string; severity?: string }[];
}

/** 导出干跑计划（§8，镜像 SeedPlan）。长整型经 JSON 序列化为字符串。 */
export interface ExportPlan {
  channels: ExportCategoryCounts;
  articles: ExportCategoryCounts;
  topics: ExportCategoryCounts;
  /** 区块定义分类计数（E4a / D27：本主题来源 + blockRef 引用闭包） */
  blocks: ExportCategoryCounts;
  /** 投票定义分类计数（E4b / D30：本主题来源 + voteRef 引用闭包） */
  votes: ExportCategoryCounts;
  /** 问卷定义分类计数（E4d / D35：本主题来源 + 向导显式多选——无引用闭包） */
  surveys: ExportCategoryCounts;
  /** 表单类型定义分类计数（E4e / D40：本主题来源 + 向导显式多选——无引用闭包） */
  forms: ExportCategoryCounts;
  /** 友情链接分组分类计数（E4f / D43：本主题来源 + 向导显式多选——无引用闭包） */
  linkGroups: ExportCategoryCounts;
  /** 模型定义分类计数（model-seeding D6：本主题来源 + 内容绑定闭包——恒不 linked 跳过） */
  models: ExportCategoryCounts;
  /** 字典类型定义分类计数（model-seeding D8：本主题来源 + 导出模型的 dictTypeId 绑定闭包） */
  dicts: ExportCategoryCounts;
  panels: ExportCategoryCounts;
  regions: ExportCategoryCounts;
  blockItems: number;
  /** 导出的友情链接条数（E4f：链接随分组定义、无独立三元组，只计条数） */
  linkItems: number;
  assetBytes: string | number;
  largestAssets: { relpath: string; bytes: string | number }[];
  externalUrls: string[];
  refIssues: { category: string; ref: string; detail: string }[];
  totalBytes: string | number;
  maxBytes: string | number;
  oversize: boolean;
  settingsSnapshot?: ExportSettingsSnapshot | null;
}

/** refreshSeedDir 报告（§8：export 端点的 refresh 形响应） */
export interface ThemeExportRefreshReport {
  written: number;
  removed: number;
  manifestUpdated: boolean;
  adopted: number;
  plan: ExportPlan;
}

/** ZIP 附件下载结果（文件名取自 Content-Disposition：<themeName>-<version>.zip） */
export interface ThemeExportDownload {
  blob: Blob;
  filename: string;
}

/** 导出干跑预览（只读，向导第一步） */
export const previewThemeExport = async (siteId: string | number, themeName: string, params: ThemeExportParams): Promise<ExportPlan> =>
  (await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/export/preview`, params)).data;

/** 就地刷新主题目录 seed/（§7.1；活动主题须 confirmActive=true，D12） */
export const refreshThemeSeedDir = async (siteId: string | number, themeName: string, params: ThemeExportParams): Promise<ThemeExportRefreshReport> =>
  (
    await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/export`, {
      ...params,
      mode: 'refreshSeedDir',
    })
  ).data;

/** Content-Disposition 附件文件名解析：优先 RFC 5987 filename*，回退 filename */
export const parseAttachmentFilename = (disposition: string): string | undefined => {
  const star = /filename\*=utf-8''([^;]+)/i.exec(disposition);
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim());
    } catch {
      // 编码异常时回退 filename
    }
  }
  return /filename="?([^";]+)"?/i.exec(disposition)?.[1]?.trim();
};

/**
 * ZIP 导出下载（dataOnlyZip / themeZip）。responseType 为 blob：错误响应体同为 Blob，
 * 跳过全局弹窗（skipErrorHandler），调用方解析后自行经 handleError 提示。
 */
export const downloadThemeExport = async (siteId: string | number, themeName: string, params: ThemeExportParams): Promise<ThemeExportDownload> => {
  const response = await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/export`, params, {
    responseType: 'blob',
    skipErrorHandler: true,
    timeout: 300000,
  } as any);
  const filename = parseAttachmentFilename(response.headers?.['content-disposition'] ?? '') ?? `${themeName}.zip`;
  return { blob: response.data, filename };
};

/**
 * ZIP 导出存入主题包库（install-flow §6 P3）：JSON 响应（入库行摘要），非附件下载。
 * 与 downloadThemeExport 同一错误契约：跳过全局弹窗，调用方经 catchExportError 提示。
 */
export const exportThemeToLibrary = async (siteId: string | number, themeName: string, params: ThemeExportParams): Promise<ThemeExportToLibraryResult> =>
  (
    await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/export`, { ...params, destination: 'library' }, {
      skipErrorHandler: true,
      timeout: 300000,
    } as any)
  ).data;

export const queryThemeSettings = async (siteId: string | number, themeName: string): Promise<ThemeSettingsResult> =>
  (await axios.get(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/settings`)).data;

export const saveThemeSettings = async (
  siteId: string | number,
  themeName: string,
  values: Record<string, Record<string, any>>,
  pages?: Record<string, Record<string, Record<string, any>>>,
  variants?: Record<string, Record<string, Record<string, any>>>,
): Promise<ThemeSettingsSaveResult> =>
  (
    await axios.post(`/backend/core/theme/${siteId}/${encodeURIComponent(themeName)}/settings?_method=put`, {
      values,
      pages,
      variants,
    })
  ).data;

// ----- 主题沙箱预览站（sandbox-preview-site §8，T2）。单一权限码 theme:sandbox（D3 组合权能）；
// 源站点恒为 ujcms-site-id 头解析的当前站点——画廊须在选中站点与当前站点一致时才展示沙箱面。 -----

/** 沙箱注册行（GET /theme-sandbox；年龄可见是反孤儿面，§3.4） */
export interface ThemeSandboxInfo {
  siteId: string | number;
  themeName: string;
  /** 沙箱站点名称（【沙箱】前缀，D13） */
  name: string;
  /** 沙箱动态地址（根相对 /sb-…；多域名部署含域名） */
  url?: string | null;
  userId: string | number;
  /** 创建用户名（用户已删除时为 null） */
  userName?: string | null;
  created: string;
}

/** 复合创建结果（POST /theme-sandbox；行级种子失败明细在 seed.content.failures，R2 通道） */
export interface ThemeSandboxResult {
  siteId: string | number;
  name: string;
  url: string;
  seed?: SeedRunResult | null;
  warnings: string[];
}

/** 一键创建沙箱（§3.2 复合动作：建裸子站 + 复制主题实例 + 全量种子——耗时随种子规模，放宽超时） */
export const createThemeSandbox = async (themeName: string): Promise<ThemeSandboxResult> =>
  (await axios.post('/backend/core/theme-sandbox', { themeName }, { timeout: 300000 })).data;

export const queryThemeSandboxes = async (): Promise<ThemeSandboxInfo[]> => (await axios.get('/backend/core/theme-sandbox')).data;

/** 清理沙箱（§3.4 注册表守卫的整站删除级联，非当前站点的注册沙箱一律 404） */
export const discardThemeSandbox = async (siteId: string | number): Promise<void> =>
  (await axios.post(`/backend/core/theme-sandbox/${siteId}?_method=delete`, undefined, { timeout: 120000 })).data;

/**
 * 市场试装到沙箱（S3 / D12：建裸沙箱站 + 商店包经市场安装管线装入并激活 + 全量种子）。
 * 权限组合 theme:sandbox 与 marketplace:install 兼备（D16）；耗时含商店下载，超时同复合创建。
 */
export const createThemeSandboxFromStore = async (params: { slug: string; version?: string; themeName?: string }): Promise<ThemeSandboxResult> =>
  (await axios.post('/backend/core/theme-sandbox/from-store', params, { timeout: 300000 })).data;

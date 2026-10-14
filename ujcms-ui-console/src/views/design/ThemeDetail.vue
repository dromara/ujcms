<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Picture, Refresh } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { currentUser, isCommercialLocked, isShowPerm, perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { handleError } from '@/utils/request';
import { toTree } from '@/utils/tree';
import { enumParam, useRouteState } from '@/composables/useRouteState';
import { resumePath } from '@/layout/components/useViewTabs';
import ShapeFieldItem from '@/views/design/fields/ShapeFieldItem.vue';
import { queryPanelsByOrigin } from '@/api/composition';
import { queryChannelList, queryTopicList } from '@/api/content';
import { querySurveyPage } from '@/api/interaction';
import { queryFormTypeList } from '@/api/config';
import { queryLinkGroupList } from '@/api/content';
import {
  queryThemeLimits,
  queryThemeList,
  queryThemePages,
  queryThemeSandboxes,
  previewActivateTheme,
  previewThemeSeed,
  importThemeSeed,
  removeThemeSeed,
  queryThemeSettings,
  saveThemeSettings,
  previewThemeExport,
  refreshThemeSeedDir,
  downloadThemeExport,
  exportThemeToLibrary,
  type ThemeInfo,
  type ThemeImpactReport,
  type ThemeLimits,
  type ThemePageInfo,
  type ThemePageSettings,
  type ThemeVariantSettings,
  type SeedScope,
  type SeedPlan,
  type SeedRunResult,
  type SeedRemoveResult,
  type ThemeExportScope,
  type ThemeExportMode,
  type ThemeExportParams,
  type ExportPlan,
  type ThemeExportRefreshReport,
} from '@/api/theme';
import { queryCurrentSite } from '@/api/system';
import {
  defaultActivateTarget,
  localized,
  notifySeedFailures,
  openThemePreviewAt,
  resolveThemePagePath,
  SEED_CATEGORIES,
  seedDiffTotal,
  seedRunSummary,
  themeAssetUrl,
  themePreviewUrl,
} from './themeUi';

defineOptions({
  name: 'ThemeDetail',
});

const { t } = useI18n();
// 清单的 license 为自由文本：SPDX 标识（MIT）原样显示，官方付费主题的 LicenseRef-UJCMS-Theme-<版本> 显示为可读名称
const licenseLabel = (license: string | undefined): string => {
  if (!license) return '—';
  const ujcmsTheme = /^LicenseRef-UJCMS-Theme-(\d+\.\d+)$/.exec(license);
  return ujcmsTheme ? t('theme.license.ujcmsTheme', { version: ujcmsTheme[1] }) : license;
};
const route = useRoute();
const router = useRouter();
const currentSiteStore = useCurrentSiteStore();

const siteId = computed<string>(() => String(route.query.site ?? ''));
const themeName = computed<string>(() => String(route.query.theme ?? ''));
// 主题来源标记（构成/内容六表的 origin_ 值）
const origin = computed<string>(() => `theme:${themeName.value}`);
// 投放/卡片端点走「当前站点」头；详情站点与之不一致时隐藏跨界动作（来源链接、投放重置）。
// 有效当前站点取 AppHeader 解析的 currentSite（未显式切换过站点时 currentSiteId 为 null）
const siteMatchesContext = computed<boolean>(() => {
  const effective = currentSiteStore.currentSite?.id ?? currentSiteStore.currentSiteId;
  return effective != null && String(effective) === siteId.value;
});

// 当前页签存于 query.tab（docs/spec/console-view-state）；site / theme 是视图的身份键，不属于状态
const { tab: activeTab } = useRouteState({ tab: enumParam('overview', ['overview', 'settings', 'demo', 'export', 'pages']) });
const theme = ref<ThemeInfo>();
// 本站全部主题（激活影响预演的缺省槽位口径需要知道两槽位现状，见 defaultActivateTarget）
const siteThemes = ref<ThemeInfo[]>([]);
const limits = ref<ThemeLimits>({ maxSizeMb: 50 });
const loading = ref<boolean>(false);
const notFound = ref<boolean>(false);

const fetchTheme = async () => {
  // 缺少身份 query（直接输入地址等）时不发请求：`/theme/` 空站点地址在后端是 404 静态资源错误
  if (!siteId.value || !themeName.value) {
    notFound.value = true;
    return;
  }
  loading.value = true;
  try {
    const list = await queryThemeList(siteId.value);
    siteThemes.value = list;
    theme.value = list.find((item) => item.themeName === themeName.value);
    notFound.value = theme.value == null;
  } finally {
    loading.value = false;
  }
};

// 纯数据包（§8 kind: data-only）：无引擎徽标、无激活影响预演（后端 preview-activate 对其 400）
const isDataOnly = computed<boolean>(() => theme.value?.metadata?.kind === 'data-only');

const previewImage = computed<string | undefined>(() => (theme.value ? themePreviewUrl(limits.value.templatesUrl, siteId.value, theme.value) : undefined));
const screenshotUrls = computed<string[]>(() =>
  (theme.value?.metadata?.screenshots ?? []).map((rel) => themeAssetUrl(limits.value.templatesUrl, siteId.value, themeName.value, rel)).filter((url): url is string => url != null),
);
// 灯箱图集：封面（0 号）+ 截图，供 el-image 预览翻阅
const galleryImages = computed<string[]>(() => [...(previewImage.value ? [previewImage.value] : []), ...screenshotUrls.value]);

// 返回画廊：画廊的页签仍开着就回到它记录的地址（所选站点、页签），而不是以裸地址重置回当前站点
const goBack = () => {
  const gallery = router.resolve({ name: 'ThemeGallery' });
  router.push(resumePath(gallery.meta.title, gallery.fullPath));
};

// ----- Overview：激活影响预演（§7.2 Overview tab；只读，theme:activate 可见） -----
const impact = ref<ThemeImpactReport>();
const impactLoading = ref<boolean>(false);
const canPreviewActivate = computed<boolean>(() => !perm('theme:activate') && !isDataOnly.value);

const fetchImpact = async () => {
  if (!canPreviewActivate.value || theme.value == null) return;
  impactLoading.value = true;
  try {
    impact.value = await previewActivateTheme(siteId.value, themeName.value, defaultActivateTarget(theme.value, siteThemes.value));
  } finally {
    impactLoading.value = false;
  }
};

const impactOrphanCount = computed<number>(() => impact.value?.orphanedPlacements.length ?? 0);

// ----- 沙箱指针（sandbox-preview-site §7）：该主题在当前站点有在册沙箱时展示「打开沙箱」指针
// 芯片——详情页只读惯例（生命周期动作在画廊），多沙箱时指最新一个。端点以当前站点头为源。 -----
const sandboxUrl = ref<string | null>(null);
const fetchSandboxPointer = async () => {
  if (!siteMatchesContext.value || perm('theme:sandbox')) return;
  try {
    const rows = (await queryThemeSandboxes()).filter((row) => row.themeName === themeName.value);
    rows.sort((a, b) => (a.created < b.created ? 1 : -1));
    sandboxUrl.value = rows[0]?.url ?? null;
  } catch {
    sandboxUrl.value = null;
  }
};
const openSandboxPointer = () => {
  if (sandboxUrl.value) window.open(sandboxUrl.value, '_blank');
};

// ----- Settings tab（§4.2 三作用域，共享 ShapeFieldItem 渲染器；自 ThemeList 抽屉移植） -----
const settingsLoaded = ref<boolean>(false);
const settingsGroups = ref<Record<string, any>[]>([]);
const settingsValues = ref<Record<string, Record<string, any>>>({});
const settingsPages = ref<ThemePageSettings[]>([]);
const settingsVariants = ref<ThemeVariantSettings[]>([]);
// 页面/变体作用域的编辑值：作用域键（页面名 / surface:value）→ 分组 → 字段 → 值
const settingsPageValues = ref<Record<string, Record<string, Record<string, any>>>>({});
const settingsVariantValues = ref<Record<string, Record<string, Record<string, any>>>>({});
const settingsLoading = ref<boolean>(false);
const settingsSaving = ref<boolean>(false);

const hasAnySettings = computed<boolean>(() => settingsGroups.value.length > 0 || settingsPages.value.length > 0 || settingsVariants.value.length > 0);
const variantKey = (v: ThemeVariantSettings) => `${v.surface}:${v.value}`;

const fetchSettings = async () => {
  settingsLoading.value = true;
  try {
    const result = await queryThemeSettings(siteId.value, themeName.value);
    settingsGroups.value = result.groups ?? [];
    settingsValues.value = result.values ?? {};
    settingsPages.value = result.pages ?? [];
    settingsVariants.value = result.variants ?? [];
    settingsPageValues.value = Object.fromEntries((result.pages ?? []).map((p) => [p.name, p.values ?? {}]));
    settingsVariantValues.value = Object.fromEntries((result.variants ?? []).map((v) => [variantKey(v), v.values ?? {}]));
    settingsLoaded.value = true;
  } finally {
    settingsLoading.value = false;
  }
};

const setSettingValue = (group: string, field: string, val: any) => {
  settingsValues.value = {
    ...settingsValues.value,
    [group]: { ...settingsValues.value[group], [field]: val },
  };
};

const setScopedValue = (scoped: Record<string, Record<string, Record<string, any>>>, scope: string, group: string, field: string, val: any) => {
  scoped[scope] = { ...scoped[scope], [group]: { ...scoped[scope]?.[group], [field]: val } };
};

const handleSaveSettings = async () => {
  settingsSaving.value = true;
  try {
    const result = await saveThemeSettings(siteId.value, themeName.value, settingsValues.value, settingsPageValues.value, settingsVariantValues.value);
    // 未声明键弃警不拒（H5）：清单结构漂移（改名/删除的分组、字段、页面/变体作用域）时列明被忽略的键
    if (result.dropped?.length) {
      ElMessage.warning(t('theme.settings.droppedWarning', { keys: result.dropped.join(t('punct.enum')) }));
    }
    ElMessage.success(t('success'));
  } finally {
    settingsSaving.value = false;
  }
};

// ----- Demo data tab（§7.2：seed_imported_ 状态 + SeedPlan 实时计数 + 四动作） -----
const seedPlan = ref<SeedPlan>();
const seedPlanLoading = ref<boolean>(false);
const lastRun = ref<SeedRunResult>();

const fetchSeedPlan = async () => {
  if (theme.value?.seedAvailable !== true) return;
  seedPlanLoading.value = true;
  try {
    seedPlan.value = await previewThemeSeed(siteId.value, themeName.value);
  } finally {
    seedPlanLoading.value = false;
  }
};

const planRows = computed(() => SEED_CATEGORIES.map((key) => ({ category: t(`theme.seed.cat.${key}`), ...seedPlan.value?.[key] })));
const formatBytes = (value: string | number | undefined): string => {
  const bytes = Number(value ?? 0);
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

// 演示数据动作权限：导入/同步/重置走 theme:seed；移除 theme:seed 或 theme:uninstall 皆可（卸载者必可干跑）
const seedActionDenied = computed<boolean>(() => perm('theme:seed'));
const removeActionDenied = computed<boolean>(() => perm('theme:seed') && perm('theme:uninstall'));

const refreshAfterSeedAction = async () => {
  await Promise.all([fetchTheme(), fetchSeedPlan()]);
};

// 未声明区域插槽的跳过明细（H5）：种子声明与清单不一致时给操作员信号（弃警不拒——导入照常完成）；
// 实体级失败明细（§8.1/D22）：常驻警示通知，含可操作的补救指引（如专题模型缺失 → 建模后重导）
const warnSeedRunIssues = (run: SeedRunResult): void => {
  const slots = run.composition.undeclaredSlots;
  if (slots?.length) {
    ElMessage.warning(t('theme.seed.run.undeclaredSlots', { slots: slots.join(t('punct.enum')) }));
  }
  const shared = run.composition.sharedSlots;
  if (shared?.length) {
    ElMessage.warning(t('theme.seed.run.sharedSlots', { slots: shared.join(t('punct.enum')) }));
  }
  notifySeedFailures(run, t);
};

// 导入（fillOnly，只填空）
const importVisible = ref<boolean>(false);
const importScope = ref<SeedScope>('all');
const importing = ref<boolean>(false);

const openImport = () => {
  importScope.value = 'all';
  importVisible.value = true;
};

const confirmImport = async () => {
  importing.value = true;
  try {
    lastRun.value = await importThemeSeed(siteId.value, themeName.value, importScope.value, 'fillOnly');
    warnSeedRunIssues(lastRun.value);
    ElMessage.success(seedRunSummary(lastRun.value, t));
    importVisible.value = false;
    await refreshAfterSeedAction();
  } finally {
    importing.value = false;
  }
};

// 更新未修改项（syncUnmodified：仅覆盖散列证明未被编辑过的本源行）
const syncing = ref<boolean>(false);
const handleSync = async () => {
  try {
    await ElMessageBox.confirm(t('theme.seed.sync.confirm'), t('theme.seed.op.sync'), { type: 'warning' });
  } catch {
    return;
  }
  syncing.value = true;
  try {
    lastRun.value = await importThemeSeed(siteId.value, themeName.value, 'all', 'syncUnmodified');
    warnSeedRunIssues(lastRun.value);
    ElMessage.success(seedRunSummary(lastRun.value, t));
    await refreshAfterSeedAction();
  } finally {
    syncing.value = false;
  }
};

// 还原演示数据（resetAllOrigin：覆盖全部本源行——红色警示 + 干跑计数先行，§6.1 不变量 2）
const resetting = ref<boolean>(false);
const handleReset = async () => {
  const affected = seedPlan.value ? seedDiffTotal(seedPlan.value, 'existing') : 0;
  const modified = seedPlan.value ? SEED_CATEGORIES.reduce((sum, key) => sum + (seedPlan.value?.[key].operatorModified ?? 0), 0) : 0;
  try {
    await ElMessageBox.confirm(t('theme.seed.reset.confirm', { count: affected, modified }), t('theme.seed.op.reset'), {
      type: 'warning',
      confirmButtonText: t('theme.seed.op.reset'),
      confirmButtonClass: 'el-button--danger',
    });
  } catch {
    return;
  }
  resetting.value = true;
  try {
    lastRun.value = await importThemeSeed(siteId.value, themeName.value, 'all', 'resetAllOrigin');
    warnSeedRunIssues(lastRun.value);
    ElMessage.success(seedRunSummary(lastRun.value, t));
    await refreshAfterSeedAction();
  } finally {
    resetting.value = false;
  }
};

// 移除演示数据（物理删除不进回收站；干跑计数先行，删除/保留分列——守卫保留的行绝不报成「已移除」）
const removeVisible = ref<boolean>(false);
const removeCounts = ref<SeedRemoveResult>();
const removeCountsLoading = ref<boolean>(false);
const removing = ref<boolean>(false);

// 逐类别「删除 / 保留（转为操作员行）」计数对（文章无保留态：守卫只作用于容器类）
const removeCountPairs = (r: SeedRemoveResult): Array<{ key: string; deleted: number; kept: number }> => [
  { key: 'articles', deleted: r.articlesDeleted, kept: 0 },
  { key: 'channels', deleted: r.channelsDeleted, kept: r.channelsOriginCleared },
  { key: 'topics', deleted: r.topicsDeleted, kept: r.topicsOriginCleared },
  { key: 'blocks', deleted: r.blocksDeleted, kept: r.blocksOriginCleared },
  { key: 'votes', deleted: r.votesDeleted, kept: r.votesOriginCleared },
  { key: 'surveys', deleted: r.surveysDeleted, kept: r.surveysOriginCleared },
  { key: 'forms', deleted: r.formsDeleted, kept: r.formsOriginCleared },
  { key: 'linkGroups', deleted: r.linkGroupsDeleted, kept: r.linkGroupsOriginCleared },
  { key: 'models', deleted: r.modelsDeleted, kept: r.modelsOriginCleared },
  { key: 'dicts', deleted: r.dictsDeleted, kept: r.dictsOriginCleared },
];
const removeRows = computed(() => {
  const counts = removeCounts.value;
  if (counts == null) return [];
  return removeCountPairs(counts).map((p) => ({ category: t(`theme.seed.cat.${p.key}`), deleted: p.deleted, kept: p.kept }));
});

const fetchRemoveCounts = async () => {
  removeCountsLoading.value = true;
  try {
    removeCounts.value = await removeThemeSeed(siteId.value, themeName.value, true);
  } finally {
    removeCountsLoading.value = false;
  }
};

const openRemove = async () => {
  removeCounts.value = undefined;
  removeVisible.value = true;
  await fetchRemoveCounts();
};

const confirmRemove = async () => {
  removing.value = true;
  try {
    const result = await removeThemeSeed(siteId.value, themeName.value, false);
    const pairs = removeCountPairs(result);
    const deletedParts = pairs.filter((p) => p.deleted > 0).map((p) => `${t(`theme.seed.cat.${p.key}`)} ${p.deleted}`);
    const keptTotal = pairs.reduce((sum, p) => sum + p.kept, 0);
    ElMessage.success(deletedParts.length ? t('theme.seed.remove.done', { detail: deletedParts.join(' / ') }) : t('theme.seed.remove.none'));
    if (keptTotal > 0) {
      ElMessage.warning(t('theme.seed.remove.kept', { count: keptTotal }));
    }
    if (result.panelRefsBroken > 0) {
      ElMessage.warning(t('theme.seed.remove.panelRefs.done', { count: result.panelRefsBroken }));
    }
    removeVisible.value = false;
    await refreshAfterSeedAction();
  } finally {
    removing.value = false;
  }
};

// ----- Export tab（theme-seed-export §8 向导，E3：选择范围 → 干跑预演 → 方式 → 下载/刷新） -----
const exportStep = ref<number>(0);
const exportScope = ref<ThemeExportScope>('all');
// 栏目/专题各自独立：默认选择（selection 字段为 null——本主题来源行 + 构成引用 + 闭包）或显式指定
const exportChannelMode = ref<'default' | 'custom'>('default');
const exportTopicMode = ref<'default' | 'selected'>('default');
const exportChannelTree = ref<any>();
const exportChannelData = ref<any[]>([]);
const exportChannelDefaultChecked = ref<(string | number)[]>([]);
const exportChannelsLoaded = ref<boolean>(false);
const exportChannelsLoading = ref<boolean>(false);
const exportTopicList = ref<any[]>([]);
const exportTopicsLoaded = ref<boolean>(false);
const exportTopicsLoading = ref<boolean>(false);
const exportTopicIds = ref<(string | number)[]>([]);
// 问卷（E4d / D35）：无引用面——显式多选是操作员问卷入包的唯一通道（T2 功能，低配列表拉取失败即置空）
const exportSurveyMode = ref<'default' | 'selected'>('default');
const exportSurveyList = ref<any[]>([]);
const exportSurveysLoaded = ref<boolean>(false);
const exportSurveysLoading = ref<boolean>(false);
const exportSurveyIds = ref<(string | number)[]>([]);
// 表单（E4e / D40）：无引用面——显式多选是操作员表单入包的唯一通道（T2 功能，低配列表拉取失败即置空）
const exportFormMode = ref<'default' | 'selected'>('default');
const exportFormList = ref<any[]>([]);
const exportFormsLoaded = ref<boolean>(false);
const exportFormsLoading = ref<boolean>(false);
const exportFormTypeIds = ref<(string | number)[]>([]);
// 友情链接（E4f / D43）：无引用面——显式多选是操作员分组入包的唯一通道（社区功能，拉取失败即置空）
const exportLinkGroupMode = ref<'default' | 'selected'>('default');
const exportLinkGroupList = ref<any[]>([]);
const exportLinkGroupsLoaded = ref<boolean>(false);
const exportLinkGroupsLoading = ref<boolean>(false);
const exportLinkGroupIds = ref<(string | number)[]>([]);
const exportArticleCap = ref<number>(20);
const exportIncludeDrafts = ref<boolean>(false);
const exportPlan = ref<ExportPlan>();
const exportPlanLoading = ref<boolean>(false);
const exportMode = ref<ThemeExportMode>('refreshSeedDir');
const exportVersion = ref<string>('');
// ZIP 去向（install-flow §6 P3）：浏览器下载（缺省）/ 存入主题包库
const exportDestination = ref<'download' | 'library'>('download');
const exportAdopt = ref<boolean>(false);
// 设置快照（§7.4）：把已存设置值写入发射清单声明默认值——只写导出产物，不写库（无需确认框）
const exportSnapshot = ref<boolean>(false);
const exporting = ref<boolean>(false);
const lastRefreshReport = ref<ThemeExportRefreshReport>();
const exportDenied = computed<boolean>(() => perm('theme:export'));
// 干跑快照报告（预览恒附）：有可写字段/丢弃键/提示才显示卡片
const exportSnapshotInfo = computed(() => {
  const snapshot = exportPlan.value?.settingsSnapshot;
  if (snapshot == null || (snapshot.fields === 0 && snapshot.dropped.length === 0 && snapshot.issues.length === 0)) {
    return undefined;
  }
  return snapshot;
});

// 版本输入缺省沿用清单当前值（§7.1，从不自动递增）；仅首次进入页签时初始化
const exportInited = ref<boolean>(false);
const initExportTab = () => {
  if (exportInited.value || theme.value == null) return;
  exportInited.value = true;
  exportVersion.value = theme.value.version ?? '';
};

// 自定义子树/指定专题的候选数据取当前站点（栏目/专题接口按站点头作用域），跨站详情禁用自定义
const fetchExportChannels = async () => {
  if (exportChannelsLoaded.value) return;
  exportChannelsLoading.value = true;
  try {
    const list = await queryChannelList();
    // 起点全选（供剪除子树）；默认选择模式才是「来源 + 引用 + 闭包」的推荐口径
    exportChannelDefaultChecked.value = list.map((item: any) => item.id);
    exportChannelData.value = toTree(list);
    exportChannelsLoaded.value = true;
  } finally {
    exportChannelsLoading.value = false;
  }
};
// 专题为 T2 功能（2026-09-15）：低于 T2 接口 404，不请求、列表置空（默认模式仍由服务端导出既有专题）
const fetchExportTopics = async () => {
  if (exportTopicsLoaded.value || currentUser.commercialTier < 2) return;
  exportTopicsLoading.value = true;
  try {
    exportTopicList.value = await queryTopicList();
    exportTopicsLoaded.value = true;
  } finally {
    exportTopicsLoading.value = false;
  }
};
watch(exportChannelMode, (mode) => {
  if (mode === 'custom') fetchExportChannels();
});
watch(exportTopicMode, (mode) => {
  if (mode === 'selected') fetchExportTopics();
});
// 问卷/表单为 T2 权限码，不进 theme:export 叶的权限包（否则社区版整叶被 filterExcludePerms 隐藏）；无权限时降级为空列表
const fetchExportSurveys = async () => {
  if (exportSurveysLoaded.value || perm('survey:list')) return;
  exportSurveysLoading.value = true;
  try {
    exportSurveyList.value = (await querySurveyPage({ page: 1, pageSize: 500 }))?.content ?? [];
    exportSurveysLoaded.value = true;
  } catch {
    exportSurveyList.value = [];
  } finally {
    exportSurveysLoading.value = false;
  }
};
watch(exportSurveyMode, (mode) => {
  if (mode === 'selected') fetchExportSurveys();
});
const fetchExportForms = async () => {
  if (exportFormsLoaded.value || perm('formType:list')) return;
  exportFormsLoading.value = true;
  try {
    exportFormList.value = (await queryFormTypeList()) ?? [];
    exportFormsLoaded.value = true;
  } catch {
    exportFormList.value = [];
  } finally {
    exportFormsLoading.value = false;
  }
};
watch(exportFormMode, (mode) => {
  if (mode === 'selected') fetchExportForms();
});
const fetchExportLinkGroups = async () => {
  if (exportLinkGroupsLoaded.value) return;
  exportLinkGroupsLoading.value = true;
  try {
    exportLinkGroupList.value = (await queryLinkGroupList()) ?? [];
    exportLinkGroupsLoaded.value = true;
  } catch {
    exportLinkGroupList.value = [];
  } finally {
    exportLinkGroupsLoading.value = false;
  }
};
watch(exportLinkGroupMode, (mode) => {
  if (mode === 'selected') fetchExportLinkGroups();
});

const buildExportParams = (): ThemeExportParams => ({
  selection: {
    channelIds: exportChannelMode.value === 'custom' ? (exportChannelTree.value?.getCheckedKeys(false) ?? []) : null,
    topicIds: exportTopicMode.value === 'selected' ? exportTopicIds.value : null,
    surveyIds: exportSurveyMode.value === 'selected' ? exportSurveyIds.value : null,
    formTypeIds: exportFormMode.value === 'selected' ? exportFormTypeIds.value : null,
    linkGroupIds: exportLinkGroupMode.value === 'selected' ? exportLinkGroupIds.value : null,
    articleCapPerChannel: exportArticleCap.value,
    includeDrafts: exportIncludeDrafts.value,
  },
  scope: exportScope.value,
});

// 导出错误统一提示：已知 i18n 码（theme.export.error.* / theme.install.error.*）用控制台文案，
// 其余走全局 handleError；ZIP 下载的错误体为 Blob，先解析再分发。
// 语言包为带点扁平键（te 按层级查找恒 false），故以「译文 ≠ 键」判定是否已登记文案。
const catchExportError = async (e: any) => {
  let data = e?.response?.data;
  if (data instanceof Blob) {
    try {
      data = JSON.parse(await data.text());
    } catch {
      data = undefined;
    }
  }
  if (data != null && typeof data === 'object') {
    const message = (data as any).message;
    const translated = typeof message === 'string' ? t(message) : undefined;
    if (translated != null && translated !== message) {
      ElMessageBox.alert(translated, t('error.title'), { type: 'warning' });
      return;
    }
    handleError(data);
    return;
  }
  handleError({ status: e?.response?.status });
};

const runExportPreview = async () => {
  exportPlanLoading.value = true;
  try {
    exportPlan.value = await previewThemeExport(siteId.value, themeName.value, buildExportParams());
    exportStep.value = 1;
  } catch (e) {
    await catchExportError(e);
  } finally {
    exportPlanLoading.value = false;
  }
};

const exportPlanRows = computed(() =>
  SEED_CATEGORIES.map((key) => {
    const c = exportPlan.value?.[key];
    return {
      category: t(`theme.seed.cat.${key}`),
      originUnmodified: c?.originUnmodified ?? 0,
      originModified: c?.originModified ?? 0,
      operatorNew: c?.operatorNew ?? 0,
      foreignOrigin: c?.foreignOrigin ?? 0,
      linkedSkipped: c?.linkedSkipped ?? 0,
      exported: (c?.originUnmodified ?? 0) + (c?.originModified ?? 0) + (c?.operatorNew ?? 0) + (c?.foreignOrigin ?? 0),
    };
  }),
);
// 诊断类别为单数形（channel / article / …），有对应类别文案则译出（扁平键，同上以译文判定）
const refIssueCategoryLabel = (category: string): string => {
  const key = `theme.seed.cat.${category}s`;
  const translated = t(key);
  return translated !== key ? translated : category;
};

const saveBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
};

const handleExport = async () => {
  const params: ThemeExportParams = {
    ...buildExportParams(),
    mode: exportMode.value,
    adoptOrigin: exportAdopt.value,
    // 纯数据包合成清单无设置结构（§7.4）——开关对该模式禁用，参数一并归位
    snapshotSettings: exportSnapshot.value && exportMode.value !== 'dataOnlyZip',
    version: exportVersion.value.trim() || null,
  };
  if (exportMode.value === 'refreshSeedDir') {
    // D12：刷新活动主题的 seed/ 须显式确认——确认框即 confirmActive=true（服务端 400 兜底）
    if (theme.value?.active || theme.value?.activeMobile) {
      try {
        await ElMessageBox.confirm(t('theme.export.activeConfirm', { theme: themeName.value }), t('theme.export.mode.refreshSeedDir'), {
          type: 'warning',
        });
      } catch {
        return;
      }
      params.confirmActive = true;
    }
    exporting.value = true;
    try {
      const report = await refreshThemeSeedDir(siteId.value, themeName.value, params);
      lastRefreshReport.value = report;
      ElMessage.success(
        t('theme.export.refresh.done', { written: report.written, removed: report.removed }) +
          (report.manifestUpdated ? t('theme.export.refresh.manifestUpdated') : '') +
          (report.adopted > 0 ? t('theme.export.refresh.adoptedN', { count: report.adopted }) : '') +
          ((report.plan?.settingsSnapshot?.fields ?? 0) > 0 ? t('theme.export.refresh.snapshotN', { count: report.plan!.settingsSnapshot!.fields }) : ''),
      );
      // 刷新可能补出 seed/（seedAvailable 翻转）；认领改写来源行——种子状态与干跑计数一并刷新
      await refreshAfterSeedAction();
    } catch (e) {
      await catchExportError(e);
    } finally {
      exporting.value = false;
    }
    return;
  }
  exporting.value = true;
  try {
    // 存入包库（install-flow §6 P3）：JSON 响应（入库行摘要），不触发浏览器下载
    if (exportDestination.value === 'library') {
      const saved = await exportThemeToLibrary(siteId.value, themeName.value, params);
      ElMessage.success(t('themePackage.export.saved', { name: saved.name, version: saved.version }));
    } else {
      const { blob, filename } = await downloadThemeExport(siteId.value, themeName.value, params);
      saveBlob(blob, filename);
      ElMessage.success(t('theme.export.download.done', { filename }));
    }
    if (exportAdopt.value) {
      await refreshAfterSeedAction();
    }
  } catch (e) {
    await catchExportError(e);
  } finally {
    exporting.value = false;
  }
};

// 来源可见性（§7.2 origin chips）：跳转页面列表并带上 origin 筛选片
const gotoPages = () => router.push({ name: 'PageList', query: { origin: origin.value } });

// 模板深链（template-manager P2）：设计›模板 选中本主题。模板管理器以当前站点为作用域，
// 与其他跨界动作同口径按 siteMatchesContext 显隐
const gotoTemplates = () => router.push({ name: 'TemplateManager', query: { theme: themeName.value } });

// ----- Pages & regions tab（只读页面清单 + 本主题来源面板） -----
const pages = ref<ThemePageInfo[]>([]);
const pagesLoading = ref<boolean>(false);
const pagesLoaded = ref<boolean>(false);
// 页面名 → 本主题来源面板（懒加载于展开行）
const pagePlacements = ref<Record<string, any[]>>({});
const pagePlacementsLoading = ref<Record<string, boolean>>({});

const fetchPages = async () => {
  pagesLoading.value = true;
  try {
    pages.value = await queryThemePages(siteId.value, themeName.value);
    pagesLoaded.value = true;
  } finally {
    pagesLoading.value = false;
  }
};

// element-plus 2.14.5 起 expand-change 的类型同时覆盖普通表格（第二参数为已展开行数组）与树形表格
// （第二参数为布尔值），处理器签名需兼容两种形态；本表非树形，实际只会收到数组。
const handlePageExpand = async (row: ThemePageInfo, expanded: ThemePageInfo[] | boolean) => {
  const isExpanded = Array.isArray(expanded) ? expanded.includes(row) : expanded;
  if (!isExpanded || pagePlacements.value[row.name] != null || !siteMatchesContext.value) return;
  pagePlacementsLoading.value = { ...pagePlacementsLoading.value, [row.name]: true };
  try {
    const list = await queryPanelsByOrigin(origin.value);
    pagePlacements.value = {
      ...pagePlacements.value,
      [row.name]: (list ?? []).filter((item: any) => item.pageRef === row.name),
    };
  } finally {
    pagePlacementsLoading.value = { ...pagePlacementsLoading.value, [row.name]: false };
  }
};

const gotoPageList = () => router.push({ name: 'PageList' });
const gotoBuilder = (page: ThemePageInfo) => router.push({ name: 'Builder', query: { page: page.name } });
const hasRegion = (page: ThemePageInfo) => page.slots.length > 0;

// 页面级渲染试看（preview-url-resolver P3）：清单 route / 解析端点候选 → ?_theme= 试看该页。
// 解析端点为当前站点作用域，故与来源面板同样以 siteMatchesContext 界定可见性。
const sitePreviewDy = ref<string>();
const pagePreviewLoading = ref<Record<string, boolean>>({});
const openPagePreview = async (row: ThemePageInfo) => {
  pagePreviewLoading.value = { ...pagePreviewLoading.value, [row.name]: true };
  try {
    if (sitePreviewDy.value == null) {
      sitePreviewDy.value = (await queryCurrentSite()).dy || '/';
    }
    let path = await resolveThemePagePath(row, sitePreviewDy.value);
    if (path == null) {
      // 无候选（实体页无内容 / 活动主题未声明同名页面）：回退站点首页，与既有整站试看一致
      ElMessage.info(t('theme.pages.previewFallback'));
      path = sitePreviewDy.value || '/';
    }
    try {
      await openThemePreviewAt(themeName.value, path);
    } catch {
      ElMessage.error(t('theme.preview.tokenError'));
    }
  } finally {
    pagePreviewLoading.value = { ...pagePreviewLoading.value, [row.name]: false };
  }
};

const handleTabChange = (tab: string | number) => {
  if (tab === 'settings' && !settingsLoaded.value) fetchSettings();
  if (tab === 'pages' && !pagesLoaded.value) fetchPages();
  if (tab === 'demo' && seedPlan.value == null) fetchSeedPlan();
  if (tab === 'export') initExportTab();
};

onMounted(async () => {
  try {
    limits.value = await queryThemeLimits();
  } catch {
    limits.value = { maxSizeMb: 50 };
  }
  await fetchTheme();
  if (theme.value != null) {
    fetchImpact();
    fetchSandboxPointer();
    if (activeTab.value === 'settings') fetchSettings();
    if (activeTab.value === 'pages') fetchPages();
    if (activeTab.value === 'demo' || theme.value.seedAvailable) fetchSeedPlan();
    if (activeTab.value === 'export') initExportTab();
  }
});
</script>

<template>
  <div v-loading="loading">
    <el-empty v-if="notFound" class="app-block py-12" :description="$t('theme.install.error.notFound')" />

    <!-- 主题识别横幅：以主题自身预览图做柔化底纹（无图回退主色淡染），身份信息与模板入口前置 -->
    <div v-else-if="theme" class="app-block relative mb-3 overflow-hidden">
      <div v-if="previewImage" class="absolute inset-0 scale-110 bg-cover bg-center blur-2xl" :style="{ backgroundImage: `url(${previewImage})` }"></div>
      <div class="absolute inset-0" :class="previewImage ? 'bg-gradient-to-r from-white/95 via-white/90 to-white/70' : 'bg-gradient-to-r from-primary-lighter to-white'"></div>
      <div class="relative flex flex-wrap items-center gap-x-4 gap-y-3 px-6 py-5">
        <el-button :icon="ArrowLeft" circle :title="$t('back')" @click="goBack" />
        <div class="min-w-0">
          <div class="flex flex-wrap items-baseline gap-x-2">
            <h1 class="text-2xl font-semibold tracking-tight text-gray-primary">{{ theme.displayName }}</h1>
            <span class="text-xs text-gray-secondary">{{ theme.themeName }}</span>
            <span v-if="theme.version" class="text-xs text-gray-secondary">v{{ theme.version }}</span>
          </div>
          <div class="mt-1.5 flex flex-wrap items-center gap-1">
            <el-tag v-if="isDataOnly" type="warning" size="small">{{ $t('theme.kind.dataOnly') }}</el-tag>
            <el-tag v-if="theme.active" type="success" size="small">{{ $t('theme.active.desktop') }}</el-tag>
            <el-tag v-if="theme.activeMobile" type="warning" size="small">{{ $t('theme.active.mobile') }}</el-tag>
            <!-- 未入主题包库（install-flow §14）：来历缺失提示（info 口吻）；存入动作在画廊卡片 -->
            <el-tag v-if="!theme.registered" type="info" size="small" effect="plain">{{ $t('theme.unregistered') }}</el-tag>
            <el-tag v-for="tag in theme.metadata?.tags ?? []" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
        </div>
        <!-- 主题的代码入口（template-manager P2）：设计›模板 选中本主题 -->
        <div v-if="siteMatchesContext && !isDataOnly" class="ml-auto">
          <el-button :disabled="perm('webFileTemplate:page')" @click="gotoTemplates">{{ $t('menu.design.template') }}</el-button>
        </div>
      </div>
    </div>

    <el-tabs v-if="theme" v-model="activeTab" class="app-block px-4 pb-2" @tab-change="handleTabChange">
      <!-- Overview：元数据、兼容性、激活影响预演 -->
      <el-tab-pane :label="$t('theme.tab.overview')" name="overview">
        <div class="flex flex-col gap-6 py-2 md:flex-row">
          <!-- 封面 + 截图：点击进 el-image 灯箱翻阅 -->
          <div class="shrink-0 md:w-96">
            <el-image
              v-if="previewImage"
              :src="previewImage"
              :alt="theme.displayName"
              fit="cover"
              class="block aspect-[16/10] w-full cursor-zoom-in rounded-lg ring-1 ring-black/5"
              :preview-src-list="galleryImages"
              :initial-index="0"
              preview-teleported
              :z-index="3000"
            />
            <div v-else class="flex aspect-[16/10] w-full items-center justify-center rounded-lg bg-gray-100 text-gray-300">
              <el-icon :size="48"><Picture /></el-icon>
            </div>
            <div v-if="screenshotUrls.length" class="mt-2 flex gap-2 overflow-x-auto pb-1">
              <el-image
                v-for="(url, index) in screenshotUrls"
                :key="url"
                :src="url"
                fit="cover"
                class="block h-20 w-32 shrink-0 cursor-zoom-in rounded-md ring-1 ring-black/5"
                :preview-src-list="galleryImages"
                :initial-index="index + (previewImage ? 1 : 0)"
                preview-teleported
                :z-index="3000"
              />
            </div>
          </div>
          <div class="min-w-0 max-w-4xl grow">
            <!-- 沙箱指针芯片（sandbox-preview-site §7）：只指不动——动作在画廊 -->
            <div v-if="sandboxUrl" class="mb-3">
              <el-tag type="success" effect="plain" class="cursor-pointer" @click="openSandboxPointer">{{ $t('theme.sandbox.open') }}</el-tag>
            </div>
            <p v-if="theme.metadata?.description" class="mb-4 text-sm leading-relaxed text-gray-regular">
              {{ theme.metadata.description }}
            </p>
            <dl class="grid grid-cols-1 gap-x-8 text-sm md:grid-cols-2">
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.themeName') }}</dt>
                <dd class="min-w-0 break-all text-right text-gray-primary">{{ theme.themeName }}</dd>
              </div>
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.version') }}</dt>
                <dd class="min-w-0 text-right text-gray-primary">{{ theme.version || '—' }}</dd>
              </div>
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.kind') }}</dt>
                <dd class="min-w-0 text-right text-gray-primary">
                  {{ theme.metadata?.kind === 'data-only' ? $t('theme.kind.dataOnly') : $t('theme.kind.theme') }}
                </dd>
              </div>
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.minUjcmsVersion') }}</dt>
                <dd class="min-w-0 text-right text-gray-primary">{{ theme.metadata?.minUjcmsVersion || '—' }}</dd>
              </div>
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.license') }}</dt>
                <dd class="min-w-0 text-right text-gray-primary">{{ licenseLabel(theme.metadata?.license) }}</dd>
              </div>
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.author') }}</dt>
                <dd class="min-w-0 break-all text-right text-gray-primary">
                  {{ theme.metadata?.author?.name || '—' }}
                  <span v-if="theme.metadata?.author?.email" class="text-gray-placeholder">({{ theme.metadata.author.email }})</span>
                </dd>
              </div>
              <div class="flex items-baseline justify-between gap-4 border-b border-gray-100 py-2 md:col-span-2">
                <dt class="shrink-0 text-gray-secondary">{{ $t('theme.homepage') }}</dt>
                <dd class="min-w-0 break-all text-right">
                  <a v-if="theme.metadata?.homepage" :href="theme.metadata.homepage" target="_blank" rel="noopener" class="text-primary">
                    {{ theme.metadata.homepage }}
                  </a>
                  <span v-else class="text-gray-primary">—</span>
                </dd>
              </div>
            </dl>

            <!-- 激活影响预演（只读；激活动作在画廊）：干跑计数以指标块呈现，孤立项非零时琥珀警示 -->
            <div v-if="canPreviewActivate" v-loading="impactLoading" class="mt-6">
              <div class="mb-2 flex items-center gap-2">
                <span class="text-sm font-semibold text-gray-primary">{{ $t('theme.overview.impact') }}</span>
                <el-button :icon="Refresh" link size="small" @click="fetchImpact" />
              </div>
              <template v-if="impact">
                <div class="grid grid-cols-2 gap-2 md:grid-cols-5">
                  <div class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2.5">
                    <div class="text-xl font-semibold text-gray-primary">{{ impact.themeShapes }}</div>
                    <div class="mt-0.5 text-xs text-gray-secondary">{{ $t('theme.overview.themeShapes') }}</div>
                  </div>
                  <div class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2.5">
                    <div class="text-xl font-semibold text-gray-primary">{{ impact.themePages }}</div>
                    <div class="mt-0.5 text-xs text-gray-secondary">{{ $t('theme.overview.themePages') }}</div>
                  </div>
                  <div class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2.5">
                    <div class="text-xl font-semibold text-gray-primary">{{ impact.settingsGroups }}</div>
                    <div class="mt-0.5 text-xs text-gray-secondary">{{ $t('theme.overview.settingsGroups') }}</div>
                  </div>
                  <div class="rounded-lg border px-3 py-2.5" :class="impactOrphanCount > 0 ? 'border-warning-light bg-warning-lighter' : 'border-gray-100 bg-gray-50'">
                    <div class="text-xl font-semibold" :class="impactOrphanCount > 0 ? 'text-warning' : 'text-gray-primary'">
                      {{ impactOrphanCount }}
                    </div>
                    <div class="mt-0.5 text-xs" :class="impactOrphanCount > 0 ? 'text-warning' : 'text-gray-secondary'">
                      {{ $t('theme.overview.orphans') }}
                    </div>
                  </div>
                  <div
                    class="rounded-lg border px-3 py-2.5"
                    :class="impact.orphanedTemplateSelections > 0 ? 'border-warning-light bg-warning-lighter' : 'border-gray-100 bg-gray-50'"
                  >
                    <div class="text-xl font-semibold" :class="impact.orphanedTemplateSelections > 0 ? 'text-warning' : 'text-gray-primary'">
                      {{ impact.orphanedTemplateSelections }}
                    </div>
                    <div class="mt-0.5 text-xs" :class="impact.orphanedTemplateSelections > 0 ? 'text-warning' : 'text-gray-secondary'">
                      {{ $t('theme.activate.orphanTemplateSelections.label') }}
                    </div>
                  </div>
                </div>
                <div class="mt-2 rounded-lg border border-gray-100 bg-gray-50 px-4 py-3">
                  <div class="text-xs text-gray-secondary">{{ $t('theme.seed.declaredContent') }}</div>
                  <div class="mt-1 text-sm text-gray-primary">
                    {{
                      $t('theme.seed.declaredContent.counts', {
                        channels: impact.seedContent.channels,
                        articles: impact.seedContent.articles,
                        topics: impact.seedContent.topics,
                        blocks: impact.seedContent.blocks,
                        votes: impact.seedContent.votes,
                        surveys: impact.seedContent.surveys,
                        forms: impact.seedContent.forms,
                        linkGroups: impact.seedContent.linkGroups,
                        models: impact.seedContent.models,
                        dicts: impact.seedContent.dicts,
                      })
                    }}
                  </div>
                </div>
                <div class="mt-2 text-xs text-gray-placeholder">{{ $t('theme.overview.impact.tip') }}</div>
              </template>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Settings：全局 + 页面/变体作用域（§4.2） -->
      <el-tab-pane :label="$t('theme.tab.settings')" name="settings">
        <div v-loading="settingsLoading" class="max-w-2xl">
          <el-empty v-if="settingsLoaded && !hasAnySettings" :description="$t('theme.settings.empty')" />
          <el-form v-else label-position="top">
            <div v-for="group in settingsGroups" :key="group.name" class="mb-4">
              <div class="font-medium mb-2 pb-1 border-b">{{ localized(group.label, group.name) }}</div>
              <el-form-item v-for="(fieldDef, fieldName) in group.fields ?? {}" :key="fieldName">
                <template #label>{{ localized((fieldDef as any).label, String(fieldName)) }}</template>
                <shape-field-item
                  :def="fieldDef"
                  :model-value="settingsValues[group.name]?.[fieldName]"
                  @update:model-value="(val: any) => setSettingValue(group.name, String(fieldName), val)"
                ></shape-field-item>
              </el-form-item>
            </div>

            <!-- 页面作用域（每个声明了 settings 的页面清单一节，值键 pages.<name>） -->
            <div v-for="page in settingsPages" :key="'page:' + page.name" class="mb-4">
              <div class="font-medium mb-2 pb-1 border-b">
                <el-tag size="small" class="mr-1">{{ $t('theme.settings.pageScope') }}</el-tag>
                {{ localized(page.label, page.name) }}
              </div>
              <template v-for="group in page.groups" :key="page.name + ':' + group.name">
                <div class="text-sm text-gray-500 mb-1">{{ localized(group.label, group.name) }}</div>
                <el-form-item v-for="(fieldDef, fieldName) in group.fields ?? {}" :key="fieldName">
                  <template #label>{{ localized((fieldDef as any).label, String(fieldName)) }}</template>
                  <shape-field-item
                    :def="fieldDef"
                    :model-value="settingsPageValues[page.name]?.[group.name]?.[fieldName]"
                    @update:model-value="(val: any) => setScopedValue(settingsPageValues, page.name, group.name, String(fieldName), val)"
                  ></shape-field-item>
                </el-form-item>
              </template>
            </div>

            <!-- 变体作用域（每个声明了 settings 的布局变体一节，值键 variants.<surface>:<value>） -->
            <div v-for="variant in settingsVariants" :key="'variant:' + variantKey(variant)" class="mb-4">
              <div class="font-medium mb-2 pb-1 border-b">
                <el-tag size="small" type="warning" class="mr-1">{{ $t('theme.settings.variantScope') }}</el-tag>
                {{ localized(variant.label, variant.value) }}
                <span class="text-xs text-gray-400 ml-1">{{ variantKey(variant) }}</span>
              </div>
              <template v-for="group in variant.groups" :key="variantKey(variant) + ':' + group.name">
                <div class="text-sm text-gray-500 mb-1">{{ localized(group.label, group.name) }}</div>
                <el-form-item v-for="(fieldDef, fieldName) in group.fields ?? {}" :key="fieldName">
                  <template #label>{{ localized((fieldDef as any).label, String(fieldName)) }}</template>
                  <shape-field-item
                    :def="fieldDef"
                    :model-value="settingsVariantValues[variantKey(variant)]?.[group.name]?.[fieldName]"
                    @update:model-value="(val: any) => setScopedValue(settingsVariantValues, variantKey(variant), group.name, String(fieldName), val)"
                  ></shape-field-item>
                </el-form-item>
              </template>
            </div>

            <el-button v-if="hasAnySettings" type="primary" :loading="settingsSaving" :disabled="perm('theme:settings')" @click="handleSaveSettings">
              {{ $t('save') }}
            </el-button>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- Demo data：种子状态 + SeedPlan 实时计数 + 导入 / 更新未修改项 / 还原演示数据 / 移除演示数据 -->
      <el-tab-pane :label="$t('theme.tab.demo')" name="demo">
        <template v-if="!theme.seedAvailable">
          <el-empty :description="$t('theme.seed.unavailable')" />
        </template>
        <template v-else>
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <el-tag :type="theme.seedImported ? 'success' : 'info'" size="small">
              {{ theme.seedImported ? $t('theme.seed.imported') : $t('theme.seed.notImported') }}
            </el-tag>
            <el-tag :type="theme.demoDataPresent ? 'warning' : 'info'" size="small">
              {{ theme.demoDataPresent ? $t('theme.seed.demoDataPresent') : $t('theme.seed.demoDataAbsent') }}
            </el-tag>
            <span v-if="seedPlan" class="text-xs text-gray-500">
              {{ $t('theme.seed.assetBytes', { size: formatBytes(seedPlan.assetBytes) }) }}
            </span>
            <el-button :icon="Refresh" link size="small" @click="fetchSeedPlan">{{ $t('refresh') }}</el-button>
            <template v-if="siteMatchesContext && isShowPerm('pageRegion:show')">
              <el-divider direction="vertical" />
              <el-button type="primary" link size="small" @click="gotoPages">{{ $t('theme.seed.gotoPages') }}</el-button>
            </template>
          </div>

          <!-- SeedPlan 干跑计数表（§6.2）：每次进入页签实时计算 -->
          <el-table v-loading="seedPlanLoading" :data="planRows" size="small" border class="mb-3" style="max-width: 720px">
            <el-table-column prop="category" :label="$t('theme.seed.category')" width="100" />
            <el-table-column prop="new" :label="$t('theme.seed.col.new')" width="100" />
            <el-table-column prop="linked" :label="$t('theme.seed.col.linked')" width="100" />
            <el-table-column prop="existing" :label="$t('theme.seed.col.existing')" width="100" />
            <el-table-column prop="operatorModified" :label="$t('theme.seed.col.operatorModified')" width="120" />
            <el-table-column prop="seedChanged" :label="$t('theme.seed.col.seedChanged')" />
          </el-table>

          <!-- 别名冲突（linked，从不收编操作员行） -->
          <div v-if="seedPlan?.collisions?.length" class="mb-3" style="max-width: 720px">
            <div class="mb-1 text-sm font-medium">{{ $t('theme.seed.collisions') }}</div>
            <el-table :data="seedPlan.collisions" size="small" border>
              <el-table-column prop="category" :label="$t('theme.seed.category')" width="100" />
              <el-table-column prop="originId" label="originId" />
              <el-table-column prop="alias" :label="$t('theme.seed.collision.alias')" />
              <el-table-column prop="existingId" :label="$t('theme.seed.collision.existingId')" />
            </el-table>
            <div class="mt-1 text-xs text-gray-500">{{ $t('theme.seed.collisions.tip') }}</div>
          </div>

          <div class="flex flex-wrap gap-2">
            <el-button type="primary" :disabled="seedActionDenied" @click="openImport">{{ $t('theme.seed.op.import') }}</el-button>
            <el-button :disabled="seedActionDenied || !theme.demoDataPresent" :loading="syncing" @click="handleSync">
              {{ $t('theme.seed.op.sync') }}
            </el-button>
            <el-button type="danger" plain :disabled="seedActionDenied || !theme.demoDataPresent" :loading="resetting" @click="handleReset">
              {{ $t('theme.seed.op.reset') }}
            </el-button>
            <el-button type="danger" :disabled="removeActionDenied || !theme.demoDataPresent" @click="openRemove">
              {{ $t('theme.seed.op.remove') }}
            </el-button>
          </div>
          <div class="mt-2 text-xs text-gray-500">{{ $t('theme.seed.ops.tip') }}</div>
        </template>
      </el-tab-pane>

      <!-- Export：种子导出向导（theme-seed-export §8，E3：选择范围 → 干跑预演 → 导出方式 → 下载/刷新） -->
      <el-tab-pane :label="$t('theme.tab.export')" name="export">
        <el-steps :active="exportStep" align-center class="mb-4" style="max-width: 720px">
          <el-step :title="$t('theme.export.step.selection')" />
          <el-step :title="$t('theme.export.step.plan')" />
          <el-step :title="$t('theme.export.step.mode')" />
        </el-steps>

        <!-- 第 1 步：选择范围（§3 选择 + 闭包，从不整站缺省） -->
        <div v-show="exportStep === 0" class="max-w-2xl">
          <el-alert :title="$t('theme.export.selection.tip')" type="info" :closable="false" class="mb-3" />
          <el-form label-width="120">
            <el-form-item :label="$t('theme.export.scope')">
              <el-radio-group v-model="exportScope">
                <el-radio value="all">{{ $t('theme.export.scope.all') }}</el-radio>
                <el-radio value="content">{{ $t('theme.export.scope.content') }}</el-radio>
                <el-radio value="composition">{{ $t('theme.export.scope.composition') }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="exportScope !== 'composition'">
              <el-form-item :label="$t('theme.export.channels')">
                <el-radio-group v-model="exportChannelMode">
                  <el-radio value="default">{{ $t('theme.export.channels.default') }}</el-radio>
                  <el-radio value="custom" :disabled="!siteMatchesContext">{{ $t('theme.export.channels.custom') }}</el-radio>
                </el-radio-group>
                <div
                  v-if="exportChannelMode === 'custom'"
                  v-loading="exportChannelsLoading"
                  class="mt-1 w-full"
                  style="max-height: 320px; overflow: auto; border: 1px solid var(--el-border-color); border-radius: 4px; padding: 4px"
                >
                  <el-tree
                    ref="exportChannelTree"
                    :data="exportChannelData"
                    :props="{ label: 'name' }"
                    node-key="id"
                    show-checkbox
                    check-on-click-node
                    default-expand-all
                    :default-checked-keys="exportChannelDefaultChecked"
                    :expand-on-click-node="false"
                  ></el-tree>
                </div>
              </el-form-item>
              <el-form-item :label="$t('theme.export.topics')">
                <el-radio-group v-model="exportTopicMode">
                  <el-radio value="default">{{ $t('theme.export.topics.default') }}</el-radio>
                  <el-radio value="selected" :disabled="!siteMatchesContext || currentUser.commercialTier < 2">{{ $t('theme.export.topics.selected') }}</el-radio>
                </el-radio-group>
                <el-select
                  v-if="exportTopicMode === 'selected'"
                  v-model="exportTopicIds"
                  multiple
                  filterable
                  :loading="exportTopicsLoading"
                  :placeholder="$t('theme.export.topics.placeholder')"
                  class="mt-1 w-full"
                >
                  <el-option v-for="item in exportTopicList" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('theme.export.surveys')">
                <el-radio-group v-model="exportSurveyMode">
                  <el-radio value="default">{{ $t('theme.export.surveys.default') }}</el-radio>
                  <el-radio value="selected" :disabled="!siteMatchesContext">{{ $t('theme.export.surveys.selected') }}</el-radio>
                </el-radio-group>
                <el-select
                  v-if="exportSurveyMode === 'selected'"
                  v-model="exportSurveyIds"
                  multiple
                  filterable
                  :loading="exportSurveysLoading"
                  :placeholder="$t('theme.export.surveys.placeholder')"
                  class="mt-1 w-full"
                >
                  <el-option v-for="item in exportSurveyList" :key="item.id" :label="item.title" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('theme.export.forms')">
                <el-radio-group v-model="exportFormMode">
                  <el-radio value="default">{{ $t('theme.export.forms.default') }}</el-radio>
                  <el-radio value="selected" :disabled="!siteMatchesContext">{{ $t('theme.export.forms.selected') }}</el-radio>
                </el-radio-group>
                <el-select
                  v-if="exportFormMode === 'selected'"
                  v-model="exportFormTypeIds"
                  multiple
                  filterable
                  :loading="exportFormsLoading"
                  :placeholder="$t('theme.export.forms.placeholder')"
                  class="mt-1 w-full"
                >
                  <el-option v-for="item in exportFormList" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('theme.export.linkGroups')">
                <el-radio-group v-model="exportLinkGroupMode">
                  <el-radio value="default">{{ $t('theme.export.linkGroups.default') }}</el-radio>
                  <el-radio value="selected" :disabled="!siteMatchesContext">{{ $t('theme.export.linkGroups.selected') }}</el-radio>
                </el-radio-group>
                <el-select
                  v-if="exportLinkGroupMode === 'selected'"
                  v-model="exportLinkGroupIds"
                  multiple
                  filterable
                  :loading="exportLinkGroupsLoading"
                  :placeholder="$t('theme.export.linkGroups.placeholder')"
                  class="mt-1 w-full"
                >
                  <el-option v-for="item in exportLinkGroupList" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('theme.export.articleCap')">
                <el-input-number v-model="exportArticleCap" :min="0" :max="10000" />
                <div class="w-full text-xs text-gray-500">{{ $t('theme.export.articleCap.tip') }}</div>
              </el-form-item>
              <el-form-item :label="$t('theme.export.includeDrafts')">
                <el-switch v-model="exportIncludeDrafts" />
              </el-form-item>
            </template>
          </el-form>
          <el-button type="primary" :loading="exportPlanLoading" :disabled="exportDenied" @click="runExportPreview">
            {{ $t('theme.export.preview') }}
          </el-button>
        </div>

        <!-- 第 2 步：干跑预演报告（§8 ExportPlan：分类计数 + 体积裁定 + 资源/外链/闭包诊断） -->
        <div v-show="exportStep === 1" v-loading="exportPlanLoading">
          <template v-if="exportPlan">
            <el-alert v-if="exportPlan.oversize" :title="$t('theme.export.plan.oversize')" type="error" :closable="false" class="mb-3" style="max-width: 720px" />
            <div class="mb-2 text-sm" :class="exportPlan.oversize ? 'text-red-500' : 'text-gray-500'">
              {{ $t('theme.export.plan.totalBytes', { size: formatBytes(exportPlan.totalBytes), max: formatBytes(exportPlan.maxBytes) }) }}
              <template v-if="!exportPlan.oversize">（{{ $t('theme.export.plan.withinBudget') }}）</template>
            </div>
            <el-table :data="exportPlanRows" size="small" border class="mb-1" style="max-width: 720px">
              <el-table-column prop="category" :label="$t('theme.export.plan.category')" width="90" />
              <el-table-column prop="originUnmodified" :label="$t('theme.export.plan.originUnmodified')" width="100" />
              <el-table-column prop="originModified" :label="$t('theme.export.plan.originModified')" width="100" />
              <el-table-column prop="operatorNew" :label="$t('theme.export.plan.operatorNew')" width="100" />
              <el-table-column prop="foreignOrigin" :label="$t('theme.export.plan.foreignOrigin')" width="100" />
              <el-table-column prop="linkedSkipped" :label="$t('theme.export.plan.linkedSkipped')" width="100" />
              <el-table-column prop="exported" :label="$t('theme.export.plan.exported')" />
            </el-table>
            <div class="mb-2 text-xs text-gray-500" style="max-width: 720px">{{ $t('theme.export.plan.classes.tip') }}</div>
            <div class="mb-3 text-sm text-gray-600">
              {{ $t('theme.export.plan.blockItems', { count: exportPlan.blockItems }) }}
              <el-divider direction="vertical" />
              {{ $t('theme.export.plan.assets', { size: formatBytes(exportPlan.assetBytes) }) }}
            </div>

            <div v-if="exportPlan.largestAssets.length" class="mb-3" style="max-width: 720px">
              <div class="mb-1 text-sm font-medium">{{ $t('theme.export.plan.largestAssets') }}</div>
              <el-table :data="exportPlan.largestAssets" size="small" border>
                <el-table-column prop="relpath" label="relpath" min-width="240" />
                <el-table-column :label="$t('theme.export.plan.bytes')" width="120">
                  <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
                </el-table-column>
              </el-table>
            </div>

            <div v-if="exportPlan.externalUrls.length" class="mb-3" style="max-width: 720px">
              <div class="mb-1 text-sm font-medium">{{ $t('theme.export.plan.externalUrls') }}</div>
              <div class="text-xs text-gray-500" style="word-break: break-all">
                <div v-for="url in exportPlan.externalUrls" :key="url">{{ url }}</div>
              </div>
            </div>

            <div v-if="exportPlan.refIssues.length" class="mb-3" style="max-width: 720px">
              <div class="mb-1 text-sm font-medium">{{ $t('theme.export.plan.refIssues') }}</div>
              <el-table :data="exportPlan.refIssues" size="small" border>
                <!-- 级别（D18）：error = 装出去必坏的数据问题（如遗留种子令牌），warning = 可散装的闭包提示 -->
                <el-table-column :label="$t('theme.export.plan.severity')" width="70">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.severity === 'error' ? 'danger' : 'warning'">
                      {{ $t(`theme.export.plan.severity.${row.severity === 'error' ? 'error' : 'warning'}`) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('theme.export.plan.category')" width="90">
                  <template #default="{ row }">{{ refIssueCategoryLabel(row.category) }}</template>
                </el-table-column>
                <el-table-column prop="ref" :label="$t('theme.export.plan.refIssues.ref')" width="180" />
                <el-table-column prop="detail" :label="$t('theme.export.plan.refIssues.detail')" min-width="240" />
              </el-table>
              <div class="mt-1 text-xs text-gray-500">{{ $t('theme.export.plan.refIssues.tip') }}</div>
            </div>

            <!-- 设置快照干跑报告（§7.4：预览恒附；第 3 步开关决定是否落笔） -->
            <div v-if="exportSnapshotInfo" class="mb-3" style="max-width: 720px">
              <div class="mb-1 text-sm font-medium">{{ $t('theme.export.plan.snapshot') }}</div>
              <div class="text-sm text-gray-600">
                {{
                  $t('theme.export.plan.snapshot.summary', {
                    fields: exportSnapshotInfo.fields,
                    manifests: exportSnapshotInfo.manifests.join(t('punct.enum')) || '—',
                  })
                }}
              </div>
              <div v-if="exportSnapshotInfo.dropped.length" class="mt-1 text-xs text-orange-500">
                {{ $t('theme.export.plan.snapshot.dropped', { keys: exportSnapshotInfo.dropped.join(t('punct.enum')) }) }}
              </div>
              <el-table v-if="exportSnapshotInfo.issues.length" :data="exportSnapshotInfo.issues" size="small" border class="mt-1">
                <el-table-column prop="ref" :label="$t('theme.export.plan.refIssues.ref')" width="180" />
                <el-table-column prop="detail" :label="$t('theme.export.plan.refIssues.detail')" min-width="240" />
              </el-table>
            </div>

            <div class="flex gap-2">
              <el-button @click="exportStep = 0">{{ $t('theme.export.prevStep') }}</el-button>
              <el-button :icon="Refresh" :loading="exportPlanLoading" @click="runExportPreview">{{ $t('refresh') }}</el-button>
              <el-button type="primary" @click="exportStep = 2">{{ $t('theme.export.nextStep') }}</el-button>
            </div>
          </template>
        </div>

        <!-- 第 3 步：导出方式 + 包版本 + 认领（§7.1 三模式 / §4.2 adopt 警示） -->
        <div v-show="exportStep === 2" class="max-w-2xl">
          <el-form label-width="120">
            <el-form-item :label="$t('theme.export.mode')">
              <el-radio-group v-model="exportMode" class="flex-col items-start" style="display: flex">
                <el-radio value="refreshSeedDir">
                  {{ $t('theme.export.mode.refreshSeedDir') }}
                  <span class="text-xs text-gray-400">{{ $t('theme.export.mode.refreshSeedDir.tip') }}</span>
                </el-radio>
                <el-radio value="dataOnlyZip">
                  {{ $t('theme.export.mode.dataOnlyZip') }}
                  <span class="text-xs text-gray-400">{{ $t('theme.export.mode.dataOnlyZip.tip') }}</span>
                </el-radio>
                <el-radio value="themeZip">
                  {{ $t('theme.export.mode.themeZip') }}
                  <span class="text-xs text-gray-400">{{ $t('theme.export.mode.themeZip.tip') }}</span>
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="$t('theme.export.version')">
              <el-input v-model="exportVersion" style="max-width: 200px" />
              <div class="w-full text-xs text-gray-500">{{ $t('theme.export.version.tip') }}</div>
            </el-form-item>
            <el-form-item :label="$t('theme.export.adopt')">
              <el-switch v-model="exportAdopt" />
            </el-form-item>
            <!-- 设置快照（§7.4）：只写导出产物、不写库——无需确认框；纯数据包无设置结构，禁用 -->
            <el-form-item :label="$t('theme.export.snapshot')">
              <el-switch v-model="exportSnapshot" :disabled="exportMode === 'dataOnlyZip'" />
              <div class="w-full text-xs text-gray-500">{{ $t('theme.export.snapshot.tip') }}</div>
            </el-form-item>
            <!-- ZIP 去向（install-flow §6 P3）：浏览器下载 / 存入主题包库（refreshSeedDir 无产物无去向） -->
            <el-form-item v-if="exportMode !== 'refreshSeedDir'" :label="$t('themePackage.export.destination')">
              <el-radio-group v-model="exportDestination">
                <el-radio value="download">{{ $t('themePackage.export.destination.download') }}</el-radio>
                <el-radio value="library">{{ $t('themePackage.export.destination.library') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <el-alert v-if="exportAdopt" :title="$t('theme.export.adopt.warning')" type="warning" :closable="false" class="mb-3" />
          <div class="flex gap-2">
            <el-button @click="exportStep = 1">{{ $t('theme.export.prevStep') }}</el-button>
            <el-button type="primary" :loading="exporting" :disabled="exportDenied || exportPlan?.oversize" @click="handleExport">
              {{ $t('theme.export.execute') }}
            </el-button>
          </div>
          <!-- 最近一次刷新报告（refreshSeedDir 的 JSON 形响应） -->
          <el-descriptions v-if="lastRefreshReport" :column="4" border size="small" class="mt-3" style="max-width: 720px">
            <el-descriptions-item :label="$t('theme.export.refresh.written')">{{ lastRefreshReport.written }}</el-descriptions-item>
            <el-descriptions-item :label="$t('theme.export.refresh.removed')">{{ lastRefreshReport.removed }}</el-descriptions-item>
            <el-descriptions-item :label="$t('theme.export.refresh.manifest')">
              {{ lastRefreshReport.manifestUpdated ? $t('yes') : $t('no') }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('theme.export.refresh.adopted')">{{ lastRefreshReport.adopted }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>

      <!-- Pages & regions：只读页面清单；链接到 页面/构建器；本主题来源面板一览 -->
      <el-tab-pane :label="$t('theme.tab.pages')" name="pages">
        <!-- 页面可视化设计为商业版 T3：社区版展示入口（路由守卫转介绍页），T1/T2 隐藏 -->
        <div v-if="isShowPerm('pageRegion:show')" class="mb-2">
          <el-button type="primary" link size="small" @click="gotoPageList">{{ $t('theme.pages.gotoPageList') }}</el-button>
        </div>
        <el-table v-loading="pagesLoading" :data="pages" size="small" @expand-change="handlePageExpand">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="px-6 py-2">
                <el-table :data="row.slots" size="small" border>
                  <el-table-column prop="name" :label="$t('region.name')" width="160" />
                  <el-table-column :label="$t('region.allowedShapes')" min-width="200">
                    <template #default="{ row: region }">
                      <el-tag v-for="item in region.allowedShapes ?? []" :key="item" size="small" class="mr-1">{{ item }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column :label="$t('theme.pages.slotRequired')" width="80">
                    <template #default="{ row: region }">{{ region.required ? $t('yes') : $t('no') }}</template>
                  </el-table-column>
                </el-table>

                <template v-if="siteMatchesContext">
                  <div v-loading="pagePlacementsLoading[row.name]" class="mt-2">
                    <template v-if="(pagePlacements[row.name] ?? []).length > 0">
                      <div class="mb-1 text-sm font-medium">{{ $t('theme.pages.originPanels') }}</div>
                      <el-table :data="pagePlacements[row.name]" size="small" border>
                        <el-table-column prop="region" :label="$t('region.name')" width="160" />
                        <el-table-column prop="component" :label="$t('health.component')" min-width="140" />
                      </el-table>
                    </template>
                    <div v-else-if="pagePlacements[row.name] != null" class="text-xs text-gray-400">
                      {{ $t('theme.pages.noOriginPanels') }}
                    </div>
                  </div>
                </template>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="$t('page.name')" width="160" />
          <el-table-column :label="$t('page.label')" min-width="140">
            <template #default="{ row }">{{ localized(row.label, row.name) }}</template>
          </el-table-column>
          <el-table-column :label="$t('page.templates')" min-width="160">
            <template #default="{ row }">
              <el-tag v-for="item in row.templates" :key="item" type="info" size="small" class="mr-1">{{ item }}</el-tag>
              <span v-if="!row.templates.length" class="text-gray-400">{{ row.route || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('theme.pages.regionCount')" width="90">
            <template #default="{ row }">{{ row.slots.length }}</template>
          </el-table-column>
          <el-table-column :label="$t('theme.pages.hasSettings')" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.hasSettings" type="success" size="small">{{ $t('yes') }}</el-tag>
              <span v-else class="text-gray-400">{{ $t('no') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="180">
            <template #default="{ row }">
              <el-button
                v-if="hasRegion(row as ThemePageInfo) && theme.active && isShowPerm('pageRegion:show')"
                type="primary"
                link
                size="small"
                :disabled="!isCommercialLocked('pageRegion:show') && perm('pageRegion:show')"
                @click="() => gotoBuilder(row as ThemePageInfo)"
              >
                {{ $t('theme.pages.openBuilder') }}
              </el-button>
              <el-button
                v-if="siteMatchesContext && !isDataOnly"
                type="primary"
                link
                size="small"
                :disabled="perm('theme:list')"
                :loading="pagePreviewLoading[row.name]"
                @click="() => openPagePreview(row as ThemePageInfo)"
              >
                {{ $t('theme.op.preview') }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="py-6 text-gray-400">{{ $t('theme.pages.empty') }}</div>
          </template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 导入演示数据（fillOnly 只填空；范围二选一——none 由后端 400 拒绝） -->
    <el-dialog v-model="importVisible" :title="$t('theme.seed.op.import')" width="480" :close-on-click-modal="false">
      <el-form label-width="100">
        <el-form-item :label="$t('theme.seed.import')">
          <el-radio-group v-model="importScope">
            <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
            <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div class="text-xs text-gray-500">{{ $t('theme.seed.import.tip') }}</div>
      <template #footer>
        <el-button @click="importVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="importing" @click="confirmImport">{{ $t('theme.seed.op.import') }}</el-button>
      </template>
    </el-dialog>

    <!-- 移除演示数据（§6.1：干跑计数先行；物理删除不进回收站；D24：告知将失效的页面组件引用） -->
    <el-dialog v-model="removeVisible" :title="$t('theme.seed.op.remove')" width="520" :close-on-click-modal="false">
      <el-alert :title="$t('theme.seed.remove.tip')" type="error" :closable="false" class="mb-3" />
      <el-table v-loading="removeCountsLoading" :data="removeRows" size="small" border>
        <el-table-column prop="category" :label="$t('theme.seed.category')" width="100" />
        <el-table-column prop="deleted" :label="$t('theme.seed.remove.col.delete')" width="120" />
        <el-table-column prop="kept" :label="$t('theme.seed.remove.col.kept')" />
      </el-table>
      <div class="mt-2 text-xs text-gray-500">{{ $t('theme.seed.remove.guards.tip') }}</div>
      <el-alert
        v-if="(removeCounts?.panelRefsBroken ?? 0) > 0"
        :title="$t('theme.seed.remove.panelRefs', { count: removeCounts?.panelRefsBroken })"
        type="warning"
        :closable="false"
        class="mt-2"
      />
      <template #footer>
        <el-button @click="removeVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="danger" :loading="removing" :disabled="removeCountsLoading" @click="confirmRemove">
          {{ $t('theme.seed.op.remove') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

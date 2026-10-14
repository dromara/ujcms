<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Plus, Delete, Refresh, Upload, Picture, View, ArrowDown, Box } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { isCommercialLocked, isShowPerm, perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { dayjsLocale, getLocaleHeaders } from '@/i18n';
import { handleError } from '@/utils/request';
import { querySiteList } from '@/api/system';
import { queryMarketplaceState, type MarketplaceState } from '@/api/marketplace';
import MarketplaceGallery from '@/views/design/MarketplaceGallery.vue';
import ThemePackageList from '@/views/design/ThemePackageList.vue';
import { enumParam, stringParam, useRouteState } from '@/composables/useRouteState';
import {
  themeInstallUrl,
  themeUpdateUrl,
  queryThemeLimits,
  queryThemeList,
  queryThemePages,
  registerThemeDir,
  uninstallTheme,
  activateTheme,
  previewActivateTheme,
  previewThemeSeed,
  removeThemeSeed,
  createThemeSandbox,
  queryThemeSandboxes,
  discardThemeSandbox,
  type ThemeSandboxInfo,
  type ThemeInfo,
  type ThemeInstallReport,
  type ThemeImpactReport,
  type ThemePageInfo,
  type ThemeUpdateReport,
  type ThemeLimits,
  type UninstallDataPolicy,
  type ActivateTarget,
  type SeedScope,
  type SeedPlan,
  type SeedRemoveResult,
} from '@/api/theme';
import { defaultActivateTarget, localized, notifySeedFailures, openThemePreviewAt, resolveThemePagePath, seedPlanSummary, seedDiffTotal, themePreviewUrl } from './themeUi';

defineOptions({
  name: 'ThemeGallery',
});

const { t, locale } = useI18n();
const router = useRouter();
const currentSiteStore = useCurrentSiteStore();

dayjs.extend(relativeTime);

const sites = ref<any[]>([]);
// 所选站点与页签存于 query（docs/spec/console-view-state），站点默认为当前站点：视图在刷新、页签往返、从主题详情返回时
// 都会重建，不记住就会悄悄回到当前站点，运营者可能把主题激活到错误的站点上
const { siteId: selectedSiteId, tab: activeTab } = useRouteState({
  siteId: stringParam(() => currentSiteStore.currentSiteId ?? sites.value[0]?.id),
  tab: enumParam('installed', isShowPerm('theme:package') ? ['installed', 'package', 'market'] : ['installed', 'market']),
});
const themes = ref<ThemeInfo[]>([]);
const limits = ref<ThemeLimits>({ maxSizeMb: 50 });
const loading = ref<boolean>(false);

// 「应用市场」页签（remote-marketplace C1）：enabled=false（内网/离线部署）或无浏览权限时隐藏，
// 其余一切不受影响（kickoff C1 §1）
const marketplaceState = ref<MarketplaceState>();
const marketVisible = computed<boolean>(() => marketplaceState.value?.enabled === true);

const fetchMarketplaceState = async () => {
  if (perm('marketplace:browse')) return;
  try {
    marketplaceState.value = await queryMarketplaceState();
  } catch {
    marketplaceState.value = undefined;
  }
};

const fetchSites = async () => {
  sites.value = await querySiteList();
  // 未选定，或恢复出的站点已不在可管理的站点之列（站点已删除、权限变化、手改地址）：回落到当前站点
  if (sites.value.length > 0 && !sites.value.some((item) => item.id === selectedSiteId.value)) {
    selectedSiteId.value = currentSiteStore.currentSiteId ?? sites.value[0].id;
  }
};

const fetchLimits = async () => {
  try {
    limits.value = await queryThemeLimits();
  } catch {
    limits.value = { maxSizeMb: 50 };
  }
};

const fetchThemes = async () => {
  if (selectedSiteId.value == null) return;
  loading.value = true;
  try {
    themes.value = await queryThemeList(selectedSiteId.value);
  } finally {
    loading.value = false;
  }
};

const handleSiteChange = () => {
  themePages.value = {};
  fetchThemes();
  // 站点一致性（previewSiteMatchesContext）随选中站点变化——不一致时清空沙箱面
  fetchSandboxes();
};

onMounted(async () => {
  await Promise.all([fetchSites(), fetchLimits(), fetchMarketplaceState()]);
  // 「应用市场」页签是否可见要等市场状态返回才知道；恢复出的页签不可见时回到「已安装」
  if (activeTab.value === 'market' && !marketVisible.value) {
    activeTab.value = 'installed';
  }
  fetchThemes();
  fetchSandboxes();
});

const previewUrl = (theme: ThemeInfo) => themePreviewUrl(limits.value.templatesUrl, selectedSiteId.value, theme);
const themeTags = (theme: ThemeInfo) => theme.metadata?.tags ?? [];
// 纯数据包（§8 kind: data-only）：可安装、不可激活
const isDataOnly = (theme: ThemeInfo) => theme.metadata?.kind === 'data-only';

// 详情页（A4 §7.2）：元数据/设置/演示数据/页面插槽都在详情，画廊只留生命周期动作
const openDetail = (theme: ThemeInfo, tab?: string) => {
  router.push({
    name: 'ThemeDetail',
    query: { site: String(selectedSiteId.value), theme: theme.themeName, ...(tab ? { tab } : {}) },
  });
};

// ----- 渲染试看（design-review P2-1 / H1）：激活前以候选主题真实渲染前台 -----
// 复用构建器同一枚预览令牌（身份即凭据，?_theme & _ptoken 随 URL 新标签页打开）；前台注入的
// preview-context.js 负责横幅与同源链接携带参数，可整站浏览。令牌 5 分钟过期后导航 401，重开即可。
// P3：传 page 时打开该页面的代表性地址（清单 route / 解析端点候选），否则维持整站首页试看。
const openThemePreview = async (theme: ThemeInfo, page?: ThemePageInfo) => {
  const site = sites.value.find((s) => s.id === selectedSiteId.value);
  if (!site) return;
  let path: string | null = null;
  if (page != null) {
    path = await resolveThemePagePath(page, site.dy);
    if (path == null) {
      ElMessage.info(t('theme.pages.previewFallback'));
    }
  }
  try {
    // site.dy = 部署路径 + 子目录（不含域名）：生产期同源拼装，开发期前置后端源（newTabPreviewBase）
    await openThemePreviewAt(theme.themeName, path ?? (site.dy || '/'));
  } catch {
    ElMessage.error(t('theme.preview.tokenError'));
  }
};

// 预览下拉的页面清单（懒加载，键主题名）。解析端点为当前站点作用域——画廊选中站点与之不一致时
// 不提供页面级预览（与详情页 siteMatchesContext 同口径），整站试看不受限。
const previewSiteMatchesContext = computed<boolean>(() => {
  const effective = currentSiteStore.currentSite?.id ?? currentSiteStore.currentSiteId;
  return effective != null && selectedSiteId.value != null && String(effective) === String(selectedSiteId.value);
});
const themePages = ref<Record<string, ThemePageInfo[]>>({});
const themePagesLoading = ref<Record<string, boolean>>({});
const loadThemePages = async (theme: ThemeInfo) => {
  if (themePages.value[theme.themeName] != null || selectedSiteId.value == null) return;
  themePagesLoading.value = { ...themePagesLoading.value, [theme.themeName]: true };
  try {
    const pages = await queryThemePages(selectedSiteId.value, theme.themeName);
    themePages.value = { ...themePages.value, [theme.themeName]: pages };
  } finally {
    themePagesLoading.value = { ...themePagesLoading.value, [theme.themeName]: false };
  }
};

// ----- install -----
const installVisible = ref<boolean>(false);
// 安装缺省仅布局（§6.3）：主题装上即有页面构成；完整站点演示由操作员显式选择
const installForm = ref<{ themeName: string; activate: boolean; activateTarget: ActivateTarget; seedScope: SeedScope }>({
  themeName: '',
  activate: false,
  activateTarget: 'desktop',
  seedScope: 'composition',
});
const installFile = ref<File>();
const installing = ref<boolean>(false);

const handleOpenInstall = () => {
  installForm.value = { themeName: '', activate: false, activateTarget: 'desktop', seedScope: 'composition' };
  installFile.value = undefined;
  installVisible.value = true;
};

// auto-upload=false 时 EP 只触发 on-change（before-upload 仅在真正上传前调用，此处永不发生）——
// 文件选择须挂 on-change，取 uploadFile.raw
const handleInstallSelect = (uploadFile: any): void => {
  const file: File | undefined = uploadFile?.raw;
  if (file == null) return;
  if (!/\.zip$/i.test(file.name)) {
    ElMessage.error(t('theme.error.notZip'));
    return;
  }
  const maxBytes = limits.value.maxSizeMb * 1024 * 1024;
  if (file.size > maxBytes) {
    ElMessage.error(t('theme.error.tooLarge', { size: `${limits.value.maxSizeMb} MB` }));
    return;
  }
  installFile.value = file;
  if (!installForm.value.themeName) {
    installForm.value.themeName = file.name.replace(/\.zip$/i, '');
  }
};

const postMultipart = async (url: string, formData: FormData): Promise<any | undefined> => {
  const response = await fetch(url, {
    method: 'POST',
    headers: { ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() },
    body: formData,
  });
  if (!response.ok) {
    const body = await response.text();
    try {
      handleError(JSON.parse(body));
    } catch {
      ElMessage.error(body || `${response.status} ${response.statusText}`);
    }
    return undefined;
  }
  return response.json();
};

const handleInstallConfirm = async () => {
  if (!installFile.value) {
    ElMessage.error(t('theme.error.fileRequired'));
    return;
  }
  if (!installForm.value.themeName.trim()) {
    ElMessage.error(t('theme.error.themeNameRequired'));
    return;
  }
  if (selectedSiteId.value == null) return;
  installing.value = true;
  try {
    const formData = new FormData();
    formData.append('file', installFile.value);
    formData.append('siteId', String(selectedSiteId.value));
    formData.append('themeName', installForm.value.themeName.trim());
    // 装后激活槽位（R8）：缺省只安装；desktop | mobile | both
    if (installForm.value.activate) {
      formData.append('activate', installForm.value.activateTarget);
    }
    formData.append('seedScope', installForm.value.seedScope);
    const report = (await postMultipart(themeInstallUrl, formData)) as ThemeInstallReport | undefined;
    if (report === undefined) return;
    if (report.warnings?.length) {
      ElMessage.warning(report.warnings.join('; '));
    }
    // 包 SHA-256（design-review P3-1 / H3）：供与发布方公示值带外比对，无签名机制；手动关闭以便抄录
    if (report.sha256) {
      ElNotification({ title: t('theme.install.sha256'), message: report.sha256, type: 'info', duration: 0 });
    }
    // 种子导入失败明细（§8.1/D22）：常驻警示，含可操作补救指引（如专题模型缺失 → 建模后重导）。
    // EP 同刻多条通知按进入动画期的 offsetHeight 计算堆叠位（近 0）会互相遮挡——延后一拍弹出
    setTimeout(() => notifySeedFailures(report.seed, t), 400);
    ElMessage.success(t('success'));
    await fetchThemes();
    installVisible.value = false;
  } finally {
    installing.value = false;
  }
};

// ----- update -----
const updateVisible = ref<boolean>(false);
const updateTheme = ref<ThemeInfo>();
const updateFile = ref<File>();
const updateSeedScope = ref<SeedScope>('none');
const updating = ref<boolean>(false);
const updateReport = ref<ThemeUpdateReport>();

const openUpdate = (theme: ThemeInfo) => {
  updateTheme.value = theme;
  updateFile.value = undefined;
  updateSeedScope.value = 'none';
  updateReport.value = undefined;
  updateVisible.value = true;
};

// 同 handleInstallSelect：auto-upload=false 下文件选择走 on-change
const handleUpdateSelect = (uploadFile: any): void => {
  const file: File | undefined = uploadFile?.raw;
  if (file == null) return;
  if (!/\.zip$/i.test(file.name)) {
    ElMessage.error(t('theme.error.notZip'));
    return;
  }
  updateFile.value = file;
};

const handleUpdateConfirm = async () => {
  if (!updateFile.value || !updateTheme.value || selectedSiteId.value == null) {
    ElMessage.error(t('theme.error.fileRequired'));
    return;
  }
  updating.value = true;
  try {
    const formData = new FormData();
    formData.append('file', updateFile.value);
    formData.append('seedScope', updateSeedScope.value);
    const report = await postMultipart(themeUpdateUrl(selectedSiteId.value, updateTheme.value.themeName), formData);
    if (report === undefined) return;
    updateReport.value = report as ThemeUpdateReport;
    // 随更新执行的种子导入失败明细（§8.1/D22）
    notifySeedFailures(updateReport.value.seed, t);
    ElMessage.success(t('success'));
    await fetchThemes();
  } finally {
    updating.value = false;
  }
};

// ----- 目录登记（install-flow §14）：无来历的已装目录（备份恢复/rsync 迁入/无清单历史安装）
// 重打包过真校验器后入库 + 补 theme_state 行。目录本身功能完好——登记补的是来历、校验与
// 包库可移植性（第二站点安装/修复源），文案按此定调；种子从不随登记导入 -----
const registering = ref<boolean>(false);

const handleRegister = async (theme: ThemeInfo) => {
  if (selectedSiteId.value == null) return;
  try {
    await ElMessageBox.confirm(t('theme.register.confirm', { name: theme.themeName }), t('theme.op.register'), { type: 'info' });
  } catch {
    return;
  }
  registering.value = true;
  try {
    const report = await registerThemeDir(selectedSiteId.value, theme.themeName);
    if (report.warnings?.length) {
      ElMessage.warning(report.warnings.join('; '));
    }
    ElMessage.success(
      t(report.alreadyRegistered ? 'theme.register.already' : 'theme.register.done', {
        pkg: `${report.name}@${report.version}`,
      }),
    );
    await fetchThemes();
  } finally {
    registering.value = false;
  }
};

// ----- activate / switch with impact preview -----
const activateVisible = ref<boolean>(false);
const activateCandidate = ref<ThemeInfo>();
// 激活槽位（R8，替代 alsoMobile）：桌面 / 手机 / 两者——仅手机不动桌面主题
const activateTarget = ref<ActivateTarget>('desktop');
// 激活缺省仅布局（§6.3：既有内容站点默认 composition；fillOnly 恒定，从不覆盖操作员行）
const activateSeedScope = ref<SeedScope>('composition');
// 重新激活且演示数据在库时可勾选：仅刷新「操作员未改动」的演示行（§6.3 syncUnmodified）
const activateSyncUnmodified = ref<boolean>(false);
const impact = ref<ThemeImpactReport>();
const seedPlan = ref<SeedPlan>();
const activateLoading = ref<boolean>(false);

// 影响预览随槽位选择重算（孤儿按结果主题对判定）；对话框内切换槽位时复用
const refreshActivateImpact = async (): Promise<ThemeImpactReport | undefined> => {
  if (selectedSiteId.value == null || !activateCandidate.value) return undefined;
  activateLoading.value = true;
  try {
    impact.value = await previewActivateTheme(selectedSiteId.value, activateCandidate.value.themeName, activateTarget.value);
    return impact.value;
  } finally {
    activateLoading.value = false;
  }
};

const openActivate = async (theme: ThemeInfo) => {
  if (selectedSiteId.value == null) return;
  activateCandidate.value = theme;
  activateTarget.value = defaultActivateTarget(theme, themes.value);
  activateSeedScope.value = 'composition';
  activateSyncUnmodified.value = false;
  impact.value = undefined;
  seedPlan.value = undefined;
  activateVisible.value = true;
  activateLoading.value = true;
  try {
    const report = await refreshActivateImpact();
    if (report?.seedAvailable) {
      // SeedPlan 干跑计数（§6.2）：三态提示展示 新增/沿用/已存在（含已改动、种子有更新）
      activateLoading.value = true;
      seedPlan.value = await previewThemeSeed(selectedSiteId.value, theme.themeName);
    } else {
      activateSeedScope.value = 'none';
    }
  } finally {
    activateLoading.value = false;
  }
};

const confirmActivate = async () => {
  if (selectedSiteId.value == null || !activateCandidate.value) return;
  activateLoading.value = true;
  try {
    const result = await activateTheme(
      selectedSiteId.value,
      activateCandidate.value.themeName,
      activateTarget.value,
      activateSeedScope.value,
      activateSyncUnmodified.value ? 'syncUnmodified' : 'fillOnly',
    );
    // 随激活执行的种子导入失败明细（§8.1/D22）
    notifySeedFailures(result?.seed, t);
    ElMessage.success(t('success'));
    activateVisible.value = false;
    await fetchThemes();
    // 采用后清理引导（sandbox-preview-site D9）：激活的主题有在册沙箱时提示清理
    if (activateCandidate.value) {
      promptSandboxCleanup(activateCandidate.value);
    }
  } finally {
    activateLoading.value = false;
  }
};

const orphanCount = (): number => impact.value?.orphanedPlacements.length ?? 0;
// 组件列：标题（操作员辨识，如「热点新闻」）+ 组件名
type OrphanedPlacement = ThemeImpactReport['orphanedPlacements'][number];
const orphanComponentLabel = (row: OrphanedPlacement): string =>
  row.title && row.component ? `${row.title}（${row.component}）` : (row.title ?? row.component ?? '');

// ----- uninstall with data policy + content counts (§6.3 uninstall row) -----
const uninstallVisible = ref<boolean>(false);
const uninstallTarget = ref<ThemeInfo>();
const uninstallDataPolicy = ref<UninstallDataPolicy>('KEEP');
const uninstalling = ref<boolean>(false);
// 卸载对话框的内容计数：DELETE …/seed?dryRun=true 干跑（安全不变量 2——数字先行）
const uninstallCounts = ref<SeedRemoveResult>();
const uninstallCountsLoading = ref<boolean>(false);

const openUninstall = async (theme: ThemeInfo) => {
  uninstallTarget.value = theme;
  uninstallDataPolicy.value = 'KEEP';
  uninstallCounts.value = undefined;
  uninstallVisible.value = true;
  if (selectedSiteId.value == null || !theme.demoDataPresent) return;
  uninstallCountsLoading.value = true;
  try {
    uninstallCounts.value = await removeThemeSeed(selectedSiteId.value, theme.themeName, true);
  } finally {
    uninstallCountsLoading.value = false;
  }
};

const uninstallContentSummary = computed<string>(() => {
  const c = uninstallCounts.value;
  if (c == null) return '';
  const articles = c.articlesDeleted;
  const channels = c.channelsDeleted + c.channelsOriginCleared;
  const topics = c.topicsDeleted + c.topicsOriginCleared;
  const blocks = c.blocksDeleted + c.blocksOriginCleared;
  const votes = c.votesDeleted + c.votesOriginCleared;
  const surveys = c.surveysDeleted + c.surveysOriginCleared;
  const forms = c.formsDeleted + c.formsOriginCleared;
  const linkGroups = c.linkGroupsDeleted + c.linkGroupsOriginCleared;
  const models = c.modelsDeleted + c.modelsOriginCleared;
  const dicts = c.dictsDeleted + c.dictsOriginCleared;
  if (articles + channels + topics + blocks + votes + surveys + forms + linkGroups + models + dicts <= 0) return '';
  return t('theme.uninstall.contentCounts', { articles, channels, topics, blocks, votes, surveys, forms, linkGroups, models, dicts });
});

const confirmUninstall = async () => {
  if (selectedSiteId.value == null || !uninstallTarget.value) return;
  uninstalling.value = true;
  try {
    await uninstallTheme(selectedSiteId.value, uninstallTarget.value.themeName, uninstallDataPolicy.value);
    ElMessage.success(t('success'));
    uninstallVisible.value = false;
    fetchThemes();
  } finally {
    uninstalling.value = false;
  }
};

// ----- 沙箱预览（sandbox-preview-site §7，T2 theme:sandbox）：为候选主题一键创建带完整演示
// 数据的独立子站点，浏览评估后整体清理或采用。端点以 ujcms-site-id 头解析源站点——画廊选中
// 站点与当前站点一致时才展示沙箱面（与页面级预览/模板深链同口径）。社区版展示商业版标识，
// 点击进功能介绍页；T1 商业版按惯例隐藏。 -----
const sandboxLocked = computed<boolean>(() => isCommercialLocked('theme:sandbox'));
const sandboxVisible = computed<boolean>(() => isShowPerm('theme:sandbox') && previewSiteMatchesContext.value);
const sandboxes = ref<ThemeSandboxInfo[]>([]);

const fetchSandboxes = async () => {
  if (!sandboxVisible.value || sandboxLocked.value || perm('theme:sandbox')) {
    sandboxes.value = [];
    return;
  }
  try {
    sandboxes.value = await queryThemeSandboxes();
  } catch {
    sandboxes.value = [];
  }
};

// 该主题在当前站点的沙箱（新→旧）；卡片下拉的 打开/重新创建/清理 作用于最新一个，其余经抽屉管理
const themeSandboxes = (theme: ThemeInfo): ThemeSandboxInfo[] => sandboxes.value.filter((s) => s.themeName === theme.themeName).sort((a, b) => (a.created < b.created ? 1 : -1));

// 相对年龄随控制台语言；绝对时间挂 title——年龄可见是反孤儿面（§3.4，无配额 D14、无自动过期 D11）
const sandboxAge = (created: string): string =>
  dayjs(created)
    .locale(dayjsLocale(locale.value))
    .fromNow();
const sandboxCreatedText = (created: string): string => dayjs(created).format('YYYY-MM-DD HH:mm');

const openSandboxUrl = (url?: string | null) => {
  if (url) window.open(url, '_blank');
};

const sandboxCreateVisible = ref<boolean>(false);
const sandboxTarget = ref<ThemeInfo>();
const sandboxImpact = ref<ThemeImpactReport>();
const sandboxImpactLoading = ref<boolean>(false);
const sandboxCreating = ref<boolean>(false);
const sandboxDrawerVisible = ref<boolean>(false);
const discardingSandboxIds = ref<Record<string, boolean>>({});

// 地址模式预览（§5）：子目录 sb-<theme-slug>-<随机6位> 由服务端铸造——slug 部分与服务端同法
const sandboxUrlPattern = computed<string>(() => {
  const name = sandboxTarget.value?.themeName ?? '';
  const slug =
    name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40) || 'theme';
  const site = sites.value.find((s) => s.id === selectedSiteId.value);
  return `${site?.domain ?? ''}/sb-${slug}-××××××`;
});

const handleSandboxAction = (theme: ThemeInfo) => {
  if (sandboxLocked.value) {
    router.push({ path: '/commercial-feature', query: { perm: 'theme:sandbox' } });
    return;
  }
  openSandboxCreate(theme);
};

const openSandboxCreate = async (theme: ThemeInfo) => {
  sandboxTarget.value = theme;
  sandboxImpact.value = undefined;
  sandboxCreateVisible.value = true;
  if (selectedSiteId.value == null || !theme.seedAvailable) return;
  sandboxImpactLoading.value = true;
  try {
    // 激活对话框同款干跑：declaredContent 声明计数（无 theme:activate 权限时静默省略计数）
    sandboxImpact.value = await previewActivateTheme(selectedSiteId.value, theme.themeName, 'desktop');
  } catch {
    sandboxImpact.value = undefined;
  } finally {
    sandboxImpactLoading.value = false;
  }
};

// 复合创建（§3.2 服务端编排：建站 + 复制主题实例 + 全量种子）——成功通知带可用的「打开沙箱」
// 链接（url 根相对 /sb-… 或多域名全限定，D15 起前导斜杠保证）；行级种子失败明细走安装流程同款
// notifySeedFailures（同刻多通知互相遮挡——延后一拍弹出）
const runSandboxCreate = async (theme: ThemeInfo) => {
  sandboxTarget.value = theme;
  sandboxCreating.value = true;
  try {
    const result = await createThemeSandbox(theme.themeName);
    if (result.warnings?.length) {
      ElMessage.warning(result.warnings.join('; '));
    }
    ElNotification({
      title: t('theme.sandbox.create.success'),
      type: 'success',
      duration: 0,
      message: h('div', [
        h('div', { class: 'mb-1 break-all' }, result.name),
        h('a', { href: result.url, target: '_blank', rel: 'noopener', class: 'text-primary' }, t('theme.sandbox.open')),
      ]),
    });
    setTimeout(() => notifySeedFailures(result.seed, t), 400);
    await fetchSandboxes();
  } finally {
    sandboxCreating.value = false;
  }
};

const confirmSandboxCreate = async () => {
  if (!sandboxTarget.value) return;
  await runSandboxCreate(sandboxTarget.value);
  sandboxCreateVisible.value = false;
};

// 重新创建 = 清理最新沙箱 + 再创建，单次显式确认（§7；抓取主题目录/设置值的最新状态）
const handleSandboxRecreate = async (theme: ThemeInfo) => {
  const latest = themeSandboxes(theme)[0];
  if (!latest) return;
  try {
    await ElMessageBox.confirm(t('theme.sandbox.recreate.confirm', { name: latest.name }), t('theme.sandbox.recreate'), { type: 'warning' });
  } catch {
    return;
  }
  await discardThemeSandbox(latest.siteId);
  await fetchSandboxes();
  await runSandboxCreate(theme);
};

// 清理 = 注册表守卫的整站删除级联（残留清零）；确认先行
const handleSandboxDiscard = async (row: ThemeSandboxInfo) => {
  try {
    await ElMessageBox.confirm(t('theme.sandbox.discard.confirm', { name: row.name }), t('theme.sandbox.discard'), { type: 'warning' });
  } catch {
    return;
  }
  discardingSandboxIds.value = { ...discardingSandboxIds.value, [String(row.siteId)]: true };
  try {
    await discardThemeSandbox(row.siteId);
    ElMessage.success(t('theme.sandbox.discard.success'));
    await fetchSandboxes();
  } finally {
    const rest = { ...discardingSandboxIds.value };
    delete rest[String(row.siteId)];
    discardingSandboxIds.value = rest;
  }
};

const handleSandboxCommand = (command: string | number | object, theme: ThemeInfo) => {
  const latest = themeSandboxes(theme)[0];
  if (command === 'open') {
    openSandboxUrl(latest?.url);
  } else if (command === 'recreate') {
    handleSandboxRecreate(theme);
  } else if (command === 'discard' && latest) {
    handleSandboxDiscard(latest);
  }
};

// 采用后清理引导（§3.4/D9）：激活成功且该主题在当前站点仍有沙箱 → 提示「沙箱已完成评估，
// 是否清理？」——确认则清理该主题的全部沙箱（评估已结束）；拒绝无副作用，抽屉仍可随时清理
const promptSandboxCleanup = async (theme: ThemeInfo) => {
  const rows = themeSandboxes(theme);
  if (!rows.length) return;
  try {
    await ElMessageBox.confirm(t('theme.sandbox.promoteCleanup.confirm', { count: rows.length }), t('theme.sandbox.discard'), { type: 'info' });
  } catch {
    return;
  }
  for (const row of rows) {
    await discardThemeSandbox(row.siteId);
  }
  ElMessage.success(t('theme.sandbox.discard.success'));
  await fetchSandboxes();
};

</script>

<template>
  <div>
    <!-- 站点选择对已安装/市场页签生效（安装目标与已装列表同源）；包库页签为全局面，仅取其为缺省安装目标 -->
    <div class="mb-3 flex items-center gap-2">
      <span class="font-medium">{{ $t('theme.site') }}</span>
      <el-select v-model="selectedSiteId" filterable style="width: 240px" @change="handleSiteChange">
        <el-option v-for="s in sites" :key="s.id" :value="s.id" :label="s.name" />
      </el-select>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="$t('theme.tab.installed')" name="installed">
        <div class="mb-3 flex items-center gap-2">
          <el-button :icon="Refresh" @click="fetchThemes">{{ $t('refresh') }}</el-button>
          <el-button type="primary" :icon="Plus" :disabled="perm('theme:install') || selectedSiteId == null" @click="handleOpenInstall">
            {{ $t('theme.op.install') }}
          </el-button>
          <!-- 沙箱抽屉入口（sandbox-preview-site §7）：当前站点有沙箱时可见——反孤儿面 -->
          <el-button v-if="sandboxVisible && sandboxes.length > 0" :icon="Box" @click="sandboxDrawerVisible = true">
            {{ $t('theme.sandbox.drawer.button') }} ({{ sandboxes.length }})
          </el-button>
          <el-tag class="ml-auto" type="info" size="small">{{ $t('theme.maxSize', { size: limits.maxSizeMb }) }}</el-tag>
        </div>

        <!-- 画廊（§7.2）：截图 + 名称/版本/引擎/标签 + 激活徽标 + 生命周期动作 -->
        <div v-loading="loading" class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          <el-card v-for="theme in themes" :key="theme.themeName" body-class="!p-0" shadow="hover">
            <!-- 预览图容器统一 16:10（主题规范推荐比例，如 800×500）；不合规图以 object-cover 居中裁切 -->
            <div class="relative aspect-[16/10] cursor-pointer overflow-hidden bg-gray-100" @click="() => openDetail(theme)">
              <img v-if="previewUrl(theme)" :src="previewUrl(theme)" :alt="theme.displayName" class="h-full w-full object-cover" />
              <div v-else class="flex h-full w-full items-center justify-center text-gray-300">
                <el-icon :size="48"><Picture /></el-icon>
              </div>
              <div class="absolute right-2 top-2 flex gap-1">
                <el-tag v-if="theme.active" type="success" size="small">{{ $t('theme.active.desktop') }}</el-tag>
                <el-tag v-if="theme.activeMobile" type="warning" size="small">{{ $t('theme.active.mobile') }}</el-tag>
              </div>
            </div>
            <div class="p-3">
              <div class="flex items-center gap-2">
                <el-link type="primary" underline="never" class="!text-base !font-medium" @click="() => openDetail(theme)">
                  {{ theme.displayName }}
                </el-link>
                <span class="text-xs text-gray-400">{{ theme.themeName }}</span>
                <span v-if="theme.version" class="ml-auto text-xs text-gray-500">v{{ theme.version }}</span>
              </div>
              <div class="mt-2 flex flex-wrap items-center gap-1">
                <el-tag v-if="isDataOnly(theme)" type="warning" size="small">{{ $t('theme.kind.dataOnly') }}</el-tag>
                <el-tag v-if="theme.seedAvailable" type="info" size="small">{{ $t('theme.seed.available') }}</el-tag>
                <el-tag v-if="theme.demoDataPresent" size="small">{{ $t('theme.seed.demoDataPresent') }}</el-tag>
                <!-- 沙箱徽标（sandbox-preview-site §7）：该主题在当前站点有在册沙箱 -->
                <el-tag v-if="sandboxVisible && themeSandboxes(theme).length > 0" type="success" size="small">{{ $t('theme.sandbox.badge') }}</el-tag>
                <el-tag v-for="tag in themeTags(theme)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                <el-tag v-if="!theme.hasManifest" type="info" size="small" effect="plain">{{ $t('theme.noMetadata') }}</el-tag>
                <!-- 未入主题包库（install-flow §14）：目录功能完好、仅来历缺失——提示口吻（info），不是警示也不是报错 -->
                <el-tag v-if="!theme.registered" type="info" size="small" effect="plain">{{ $t('theme.unregistered') }}</el-tag>
              </div>
              <div class="mt-3 flex flex-wrap items-center gap-x-1">
                <el-button type="primary" link size="small" @click="() => openDetail(theme)">{{ $t('theme.op.detail') }}</el-button>
                <el-button v-if="!isDataOnly(theme)" type="primary" link size="small" :icon="View" :disabled="perm('theme:list')" @click="() => openThemePreview(theme)">
                  {{ $t('theme.op.preview') }}
                </el-button>
                <!-- 页面级预览下拉（P3）：懒加载该主题页面清单，按代表性地址打开对应页面 -->
                <el-dropdown
                  v-if="!isDataOnly(theme) && previewSiteMatchesContext"
                  trigger="click"
                  @visible-change="(visible: boolean) => visible && loadThemePages(theme)"
                  @command="(page: ThemePageInfo) => openThemePreview(theme, page)"
                >
                  <el-button type="primary" link size="small" :icon="ArrowDown" :disabled="perm('theme:list')" :title="$t('theme.op.previewPage')" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <template v-if="(themePages[theme.themeName] ?? []).length > 0">
                        <el-dropdown-item v-for="page in themePages[theme.themeName]" :key="page.name" :command="page">
                          {{ localized(page.label, page.name) }}
                        </el-dropdown-item>
                      </template>
                      <el-dropdown-item v-else disabled>
                        {{ themePagesLoading[theme.themeName] ? '…' : $t('theme.preview.noPages') }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <!-- 模板深链（template-manager P2）：设计›模板 选中该主题。模板管理器以当前站点为
                     作用域，故与页面级预览同口径按站点一致性显隐 -->
                <el-button
                  v-if="!isDataOnly(theme) && previewSiteMatchesContext"
                  type="primary"
                  link
                  size="small"
                  :disabled="perm('webFileTemplate:page')"
                  @click="() => router.push({ name: 'TemplateManager', query: { theme: theme.themeName } })"
                >
                  {{ $t('menu.design.template') }}
                </el-button>
                <!-- 激活（R8）：单一动作，槽位（桌面 / 手机 / 两者）在对话框里选——与孤儿策略、种子范围同处一屏；
                     两槽位都已是本主题则隐藏。不带图标：✓ 会被读成「已选中」，槽位状态由卡片角标（桌面端 / 手机端）表达 -->
                <el-button
                  v-if="!(theme.active && theme.activeMobile) && !isDataOnly(theme)"
                  type="primary"
                  link
                  size="small"
                  :disabled="perm('theme:activate')"
                  @click="() => openActivate(theme)"
                >
                  {{ $t('theme.op.activate') }}
                </el-button>
                <!-- 沙箱预览（sandbox-preview-site §7）：已装 + 两槽位均非活动 + 非纯数据包——持有任一槽位的主题
                     已在线上渲染，沙箱评估无意义（2026-08-23 起对称门，取代原「仅桌面端非活动」）；有在册沙箱时
                     变为下拉 打开/重新创建/清理（作用于最新一个）；社区版带商业版标识进介绍页 -->
                <template v-if="sandboxVisible && !theme.active && !theme.activeMobile && !isDataOnly(theme)">
                  <el-dropdown v-if="themeSandboxes(theme).length > 0" trigger="click" @command="(command: string) => handleSandboxCommand(command, theme)">
                    <el-button type="primary" link size="small" :icon="Box" :loading="sandboxCreating && sandboxTarget?.themeName === theme.themeName">
                      {{ $t('theme.sandbox.action') }}<el-icon class="ml-0.5"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="open">{{ $t('theme.sandbox.open') }}</el-dropdown-item>
                        <el-dropdown-item command="recreate">{{ $t('theme.sandbox.recreate') }}</el-dropdown-item>
                        <el-dropdown-item command="discard">{{ $t('theme.sandbox.discard') }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button v-else type="primary" link size="small" :icon="Box" :disabled="!sandboxLocked && perm('theme:sandbox')" @click="() => handleSandboxAction(theme)">
                    {{ $t('theme.sandbox.action') }}
                    <commercial-badge v-if="sandboxLocked" permission="theme:sandbox" class="ml-1" />
                  </el-button>
                </template>
                <!-- 存入主题包库（install-flow §14 目录登记 register）：仅未入库目录可见，权限沿用 theme:install -->
                <el-button v-if="!theme.registered" type="primary" link size="small" :loading="registering" :disabled="perm('theme:install')" @click="() => handleRegister(theme)">
                  {{ $t('theme.op.register') }}
                </el-button>
                <el-button type="primary" link size="small" :icon="Upload" :disabled="perm('theme:install')" @click="() => openUpdate(theme)">
                  {{ $t('theme.op.update') }}
                </el-button>
                <el-button
                  type="primary"
                  link
                  size="small"
                  :icon="Delete"
                  :disabled="perm('theme:uninstall') || theme.active || theme.activeMobile"
                  @click="() => openUninstall(theme)"
                >
                  {{ $t('theme.op.uninstall') }}
                </el-button>
              </div>
            </div>
          </el-card>
          <el-empty v-if="!loading && themes.length <= 0" class="col-span-full" :description="$t('theme.empty')" />
        </div>
      </el-tab-pane>

      <!-- 主题包库（install-flow §6 P3 / D26）：全局包面并入主题页作页签（本站已装 → 服务器包库 → 远程市场），
           theme:package 控页签可见性；装包成功后刷新已装列表 -->
      <el-tab-pane v-if="isShowPerm('theme:package')" :label="$t('theme.tab.package')" name="package" lazy>
        <theme-package-list :site-id="selectedSiteId" @installed="fetchThemes" />
      </el-tab-pane>

      <!-- 应用市场（remote-marketplace C1/C2）：远程浏览 + 安装/更新 + 账号连接；enabled=false 时页签隐藏 -->
      <el-tab-pane v-if="marketVisible" :label="$t('marketplace.tab')" name="market" lazy>
        <marketplace-gallery
          :site-id="selectedSiteId"
          :themes="themes"
          :backend-version="marketplaceState?.backendVersion"
          :storefront-url="marketplaceState?.storefrontUrl"
          :sandbox-allowed="previewSiteMatchesContext"
          @installed="fetchThemes"
          @sandbox-created="fetchSandboxes"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- Install dialog -->
    <el-dialog v-model="installVisible" :title="$t('theme.op.install')" width="540" :close-on-click-modal="false">
      <el-form label-width="120">
        <el-form-item :label="$t('theme.zip')">
          <el-upload ref="uploader" :on-change="handleInstallSelect" :auto-upload="false" :show-file-list="false" accept=".zip" drag>
            <div class="flex flex-col items-center px-4 py-6">
              <el-icon class="text-4xl text-primary"><Plus /></el-icon>
              <div class="mt-2">{{ installFile?.name ?? $t('theme.zip.placeholder') }}</div>
              <div class="text-xs text-gray-500 mt-1">{{ $t('theme.maxSize', { size: limits.maxSizeMb }) }}</div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item :label="$t('theme.themeName')" required>
          <el-input v-model="installForm.themeName" :placeholder="$t('theme.themeName.placeholder')" maxlength="50" />
        </el-form-item>
        <!-- 三态 seedScope（§6.3）：完整站点演示 / 仅布局（缺省）/ 不导入 -->
        <el-form-item :label="$t('theme.seed.import')">
          <el-radio-group v-model="installForm.seedScope">
            <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
            <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
            <el-radio value="none">{{ $t('theme.seed.scope.none') }}</el-radio>
          </el-radio-group>
          <div class="text-xs text-gray-500">{{ $t('theme.seed.import.tip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('theme.install.activate')">
          <el-switch v-model="installForm.activate" />
        </el-form-item>
        <el-form-item v-if="installForm.activate" :label="$t('theme.activate.target')">
          <el-radio-group v-model="installForm.activateTarget">
            <el-radio value="desktop">{{ $t('theme.activate.target.desktop') }}</el-radio>
            <el-radio value="mobile">{{ $t('theme.activate.target.mobile') }}</el-radio>
            <el-radio value="both">{{ $t('theme.activate.target.both') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="installVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="installing" :disabled="!installFile || !installForm.themeName.trim()" @click="handleInstallConfirm">
          {{ $t('theme.op.install') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Update dialog -->
    <el-dialog v-model="updateVisible" :title="$t('theme.op.update')" width="540" :close-on-click-modal="false">
      <el-alert :title="$t('theme.update.tip')" type="info" :closable="false" class="mb-3" />
      <el-form label-width="120">
        <el-form-item :label="$t('theme.themeName')">
          <span>{{ updateTheme?.themeName }}</span>
        </el-form-item>
        <el-form-item :label="$t('theme.zip')">
          <el-upload :on-change="handleUpdateSelect" :auto-upload="false" :show-file-list="false" accept=".zip" drag>
            <div class="flex flex-col items-center px-4 py-6">
              <el-icon class="text-4xl text-primary"><Upload /></el-icon>
              <div class="mt-2">{{ updateFile?.name ?? $t('theme.zip.placeholder') }}</div>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item :label="$t('theme.seed.import')">
          <el-radio-group v-model="updateSeedScope">
            <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
            <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
            <el-radio value="none">{{ $t('theme.seed.scope.none') }}</el-radio>
          </el-radio-group>
          <div class="text-xs text-gray-500">{{ $t('theme.update.seedScope.tip') }}</div>
        </el-form-item>
      </el-form>
      <!-- 更新报告：逐类别种子差异（new / seedChanged / operatorModified，§6.2）；
           覆盖策略（fillOnly / syncUnmodified / resetAllOrigin）在详情页「演示数据」页签按差异选择 -->
      <template v-if="updateReport">
        <el-descriptions :column="3" border size="small" class="mt-2">
          <el-descriptions-item :label="$t('theme.update.added')">{{ seedDiffTotal(updateReport.seedDiff, 'new') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('theme.update.seedChanged')">{{ seedDiffTotal(updateReport.seedDiff, 'seedChanged') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('theme.update.operatorModified')">{{ seedDiffTotal(updateReport.seedDiff, 'operatorModified') }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="seedPlanSummary(updateReport.seedDiff, t)" class="text-xs text-gray-500 mt-1">
          {{ seedPlanSummary(updateReport.seedDiff, t) }}
        </div>
        <!-- 包 SHA-256（design-review P3-1 / H3）：供与发布方公示值带外比对，无签名机制 -->
        <div v-if="updateReport.sha256" class="text-xs text-gray-500 mt-1 break-all">SHA-256: {{ updateReport.sha256 }}</div>
        <div class="mt-2">
          <el-button type="primary" link size="small" @click="() => openDetail(updateTheme!, 'demo')">
            {{ $t('theme.update.gotoSeedTab') }}
          </el-button>
        </div>
      </template>
      <template #footer>
        <el-button @click="updateVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="updating" :disabled="!updateFile" @click="handleUpdateConfirm">
          {{ $t('theme.op.update') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Activate / switch impact dialog -->
    <el-dialog v-model="activateVisible" :title="$t('theme.activate.title')" width="600" :close-on-click-modal="false">
      <div v-loading="activateLoading">
        <el-alert v-if="orphanCount() > 0" :title="$t('theme.activate.orphanWarn', { count: orphanCount() })" type="warning" :closable="false" class="mb-3" />
        <el-alert v-else :title="$t('theme.activate.noOrphan')" type="success" :closable="false" class="mb-3" />

        <!-- 孤立投放只提示不询问：一律归档，切回时自动恢复（D21），诊断页可逐行/批量处置 -->
        <div v-if="impact && impact.orphanedPlacements.length" class="mb-2">
          <div class="font-medium mb-1">{{ $t('theme.activate.orphanPlacements') }}</div>
          <el-table :data="impact.orphanedPlacements" size="small" border max-height="180">
            <el-table-column prop="pageRef" :label="$t('theme.activate.page')" width="120" />
            <el-table-column prop="slotName" :label="$t('theme.activate.region')" width="120" />
            <el-table-column :label="$t('theme.activate.component')" show-overflow-tooltip>
              <template #default="{ row }">{{ orphanComponentLabel(row as OrphanedPlacement) }}</template>
            </el-table-column>
          </el-table>
          <div class="text-xs text-gray-500 mt-1">{{ $t('theme.activate.orphanArchiveNote') }}</div>
        </div>
        <!-- 切回自动恢复（D21）：页面/区域重新被声明的归档组件在激活时取消归档 -->
        <el-alert
          v-if="impact && impact.restorablePlacements > 0"
          :title="$t('theme.activate.restoreNote', { count: impact.restorablePlacements })"
          type="success"
          :closable="false"
          class="mb-2"
        />
        <!-- 孤立模板选择（template-track-variants D7）：只提示不处置——选择列永不改写，切回主题即恢复 -->
        <el-alert
          v-if="impact && impact.orphanedTemplateSelections > 0"
          :title="$t('theme.activate.orphanTemplateSelections', { count: impact.orphanedTemplateSelections })"
          type="info"
          :closable="false"
          class="mb-2"
        />

        <el-form label-width="140" class="mt-2">
          <!-- 激活槽位（R8）：桌面 / 手机 / 两者——已持有本主题的槽位禁选（选它是空操作：槽位不变，只会
               跑一遍孤儿/种子流程；两者之一已持有时「桌面+手机」等价于另一单槽，同禁）；切换即重算影响预览 -->
          <el-form-item :label="$t('theme.activate.target')">
            <el-radio-group v-model="activateTarget" @change="refreshActivateImpact">
              <el-radio value="desktop" :disabled="activateCandidate?.active">{{ $t('theme.activate.target.desktop') }}</el-radio>
              <el-radio value="mobile" :disabled="activateCandidate?.activeMobile">{{ $t('theme.activate.target.mobile') }}</el-radio>
              <el-radio value="both" :disabled="activateCandidate?.active || activateCandidate?.activeMobile">
                {{ $t('theme.activate.target.both') }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <!-- 三态 seedScope + SeedPlan 计数（§6.3）：仅布局为缺省；完整站点演示显式选择 -->
          <el-form-item v-if="impact?.seedAvailable" :label="$t('theme.seed.import')">
            <el-radio-group v-model="activateSeedScope">
              <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
              <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
              <el-radio value="none">{{ $t('theme.seed.scope.none') }}</el-radio>
            </el-radio-group>
            <div v-if="seedPlan" class="text-xs text-gray-500 w-full">{{ seedPlanSummary(seedPlan, t) }}</div>
          </el-form-item>
          <!-- 重新激活且演示数据在库：可选刷新未改动的演示行（散列判定，改过的保留；§6.3 syncUnmodified） -->
          <el-form-item v-if="impact?.seedAvailable && impact?.demoDataPresent && activateSeedScope !== 'none'" :label="$t('theme.seed.syncUnmodified')">
            <el-switch v-model="activateSyncUnmodified" />
            <span class="text-xs text-gray-500 ml-2">{{ $t('theme.seed.syncUnmodified.tip') }}</span>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <!-- 渲染试看（P2-1）：确认激活前新标签页真实预览候选主题 -->
        <el-button v-if="activateCandidate" class="!float-left" :icon="View" @click="() => openThemePreview(activateCandidate!)">
          {{ $t('theme.op.preview') }}
        </el-button>
        <el-button @click="activateVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="activateLoading" @click="confirmActivate">
          {{ $t('theme.activate.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Uninstall data-policy dialog（§6.3：内容计数一并展示） -->
    <el-dialog v-model="uninstallVisible" :title="$t('theme.op.uninstall')" width="520" :close-on-click-modal="false">
      <el-alert :title="$t('theme.confirm.uninstall')" type="warning" :closable="false" class="mb-3" />
      <el-alert v-if="uninstallContentSummary" v-loading="uninstallCountsLoading" :title="uninstallContentSummary" type="info" :closable="false" class="mb-3" />
      <el-form label-width="140">
        <el-form-item :label="$t('theme.uninstall.dataPolicy')">
          <el-radio-group v-model="uninstallDataPolicy">
            <el-radio value="KEEP">{{ $t('theme.uninstall.policy.keep') }}</el-radio>
            <el-radio value="ARCHIVE">{{ $t('theme.uninstall.policy.archive') }}</el-radio>
            <el-radio value="DELETE">{{ $t('theme.uninstall.policy.delete') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="text-xs text-gray-500 px-2">{{ $t('theme.uninstall.dataPolicy.tip') }}</div>
      </el-form>
      <template #footer>
        <el-button @click="uninstallVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="danger" :loading="uninstalling" @click="confirmUninstall">{{ $t('theme.op.uninstall') }}</el-button>
      </template>
    </el-dialog>

    <!-- Sandbox create dialog（sandbox-preview-site §7）：一次确认，展示将发生的事——
         地址模式（子目录服务端铸造）+ 种子声明内容计数（激活对话框同款干跑来源） -->
    <el-dialog v-model="sandboxCreateVisible" :title="$t('theme.sandbox.create.title')" width="560" :close-on-click-modal="false">
      <el-alert :title="$t('theme.sandbox.create.tip')" type="info" :closable="false" class="mb-3" />
      <el-form label-width="120">
        <el-form-item :label="$t('theme.themeName')">
          <span>
            {{ sandboxTarget?.displayName }}
            <span class="text-xs text-gray-400">{{ sandboxTarget?.themeName }}</span>
          </span>
        </el-form-item>
        <el-form-item :label="$t('theme.sandbox.create.urlPattern')">
          <div class="leading-snug">
            <div class="break-all">{{ sandboxUrlPattern }}</div>
            <div class="text-xs text-gray-500">{{ $t('theme.sandbox.create.urlPattern.tip') }}</div>
          </div>
        </el-form-item>
        <el-form-item v-if="sandboxTarget?.seedAvailable" :label="$t('theme.seed.declaredContent')">
          <div v-loading="sandboxImpactLoading" class="min-h-5 w-full text-sm leading-snug">
            <template v-if="sandboxImpact">
              {{
                $t('theme.seed.declaredContent.counts', {
                  channels: sandboxImpact.seedContent.channels,
                  articles: sandboxImpact.seedContent.articles,
                  topics: sandboxImpact.seedContent.topics,
                  blocks: sandboxImpact.seedContent.blocks,
                  votes: sandboxImpact.seedContent.votes,
                  surveys: sandboxImpact.seedContent.surveys,
                  forms: sandboxImpact.seedContent.forms,
                  linkGroups: sandboxImpact.seedContent.linkGroups,
                  models: sandboxImpact.seedContent.models,
                  dicts: sandboxImpact.seedContent.dicts,
                })
              }}
            </template>
          </div>
        </el-form-item>
        <el-form-item v-else :label="$t('theme.seed.declaredContent')">
          <span class="text-xs text-gray-500">{{ $t('theme.sandbox.create.noSeed') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sandboxCreateVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="sandboxCreating" @click="confirmSandboxCreate">{{ $t('theme.sandbox.create.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Sandbox drawer（sandbox-preview-site §7 反孤儿面）：当前站点全部沙箱——主题、创建人、
         年龄（绝对时间挂 title）、地址，逐行 打开/清理（清理先确认，整站删除级联零残留） -->
    <el-drawer v-model="sandboxDrawerVisible" :title="$t('theme.sandbox.drawer.title')" size="720">
      <el-table :data="sandboxes" size="small">
        <el-table-column :label="$t('theme.sandbox.col.name')" min-width="150">
          <template #default="{ row }">
            <div>{{ row.name }}</div>
            <div class="text-xs text-gray-400">{{ row.themeName }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('theme.sandbox.col.url')" min-width="150">
          <template #default="{ row }">
            <el-link v-if="row.url" type="primary" underline="never" class="!text-xs break-all" @click="() => openSandboxUrl(row.url)">
              {{ row.url }}
            </el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('theme.sandbox.col.creator')" width="90">
          <template #default="{ row }">{{ row.userName ?? row.userId }}</template>
        </el-table-column>
        <el-table-column :label="$t('theme.sandbox.col.age')" width="100">
          <template #default="{ row }">
            <span :title="sandboxCreatedText(row.created)">{{ sandboxAge(row.created) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :disabled="!row.url" @click="() => openSandboxUrl(row.url)">
              {{ $t('theme.sandbox.rowOpen') }}
            </el-button>
            <el-button type="danger" link size="small" :loading="discardingSandboxIds[String(row.siteId)]" @click="() => handleSandboxDiscard(row as ThemeSandboxInfo)">
              {{ $t('theme.sandbox.rowDiscard') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, toRaw, watch, watchEffect } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { localizedLabel } from '@/i18n';
import {
  previewToken,
  queryCompositionPageList,
  queryPageDetail,
  queryPreviewUrls,
  queryRegionList,
  queryRowLayouts,
  deleteRegionOverride,
  type PreviewUrlCandidate,
} from '@/api/composition';
import { queryCurrentSite } from '@/api/system';
import { queryChannelList } from '@/api/content';
import { queryThemeSettings, saveThemeSettings } from '@/api/theme';
import { perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { useLayoutVariantStore } from '@/stores/layoutVariantStore';
import {
  usePreviewBridge,
  buildNewTabPreviewUrl,
  buildPreviewUrl,
  previewTargetOrigin,
  templatePreviewBase,
  type BridgeCanvasIntent,
  type BridgeSelection,
  type DropTarget,
} from '@/composables/usePreviewBridge';
import { useRegionDraft } from '@/composables/useRegionDraft';
import { usePreviewTokenHeartbeat } from '@/composables/usePreviewTokenHeartbeat';
import { invalidateShapeDefsCache, type PanelFormValues } from '@/views/design/usePanelSettingsForm';
import PanelForm from '@/views/design/PanelForm.vue';
import ArticleForm from '@/views/content/ArticleForm.vue';
import ChannelForm from '@/views/content/ChannelForm.vue';
import BuilderTopBar from './BuilderTopBar.vue';
import PaletteSidebar from './PaletteSidebar.vue';
import CanvasStage from './CanvasStage.vue';
import InspectorSidebar from './InspectorSidebar.vue';
import StructureView from './StructureView.vue';
import HistoryDrawer from './HistoryDrawer.vue';
import { useBuilderDrag } from './useBuilderDrag';
import { cachedPageLabel, regionLabelOf, rememberPageLabel } from './builderLabels';
import { canAddShape, canInsertRow, layoutAllowed, rowsFull, shapeAllowed } from './constraints';
import { isEditableTarget, modalOpen, shortcutOf, type ShortcutAction } from './shortcuts';
import type {
  ColumnVM,
  Drafts,
  PageDetailResult,
  PaletteDragStart,
  PaletteItem,
  PanelVM,
  PlacedPayload,
  RegionDraft,
  RegionDraftState,
  RowLayout,
  RowVM,
  ShareState,
  Viewport,
} from './types';

/**
 * 可视化构建器（visual-builder §10）：全屏路由 /design/builder?page=，页面级作用域——
 * 页面全部区域可编辑。调色板（PaletteSidebar，双模式常显 P7/D12：画布态点击放置 D4 + 幽灵拖拽
 * §7.2，P3；结构态 SortableJS 克隆拖入结构视图）+ 画布
 * （CanvasStage 预览 iframe + 预览桥 v2 点击选择 + 手柄拖拽重排 P2）+ 检查器（InspectorSidebar
 * 随选择上下文，P3 增删行/面板）+ 结构视图（StructureView，原 RegionEditor 折叠而来，D7）
 * 共享 useRegionDraft 草稿模型（每区域一实例），视图切换不丢编辑；桥超时自动降级到结构视图。
 * 装载走 page-detail 聚合（§7.1：区域详情 + 调色板目录一次取回）。两视图统一保存即逐操作持久化
 * （§6 save-per-op，P9/D14：结构视图手势经 commitRegion 微任务合并 + 区域内串行提交；新增面板
 * 两视图同走延迟提交——PanelForm 确定首存，「操作」的粒度是配置完成的面板，取消则本地回滚），
 * saveDraft 响应 {version, panels} 串联版本链；撤销/重做为控制台侧
 * 逆操作栈（D8，区域各一栈 + 顶栏全局顺序）。检查器内联面板设置保存与 PanelForm 确定同终点
 * （shell-polish）；快捷键（控制台 keydown + 桥转发 key）经 runShortcut 与顶栏按钮同终点。单根组件。
 */
defineOptions({
  name: 'BuilderPage',
});

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const currentSiteStore = useCurrentSiteStore();

const page = String(route.query.page ?? '');
const presetRegion = route.query.region != null ? String(route.query.region) : '';

const loading = ref<boolean>(false);
const site = ref<any>();
const pagePath = ref<string>('/');
// 预览路径候选（preview-url-resolver P2）：路径选择器数据源 + 页面感知默认路径；
// emptyReason 'no-content' 时给出 设计 → 主题 → 演示数据 导入引导（§6 种子协同）
const pathOptions = ref<PreviewUrlCandidate[]>([]);
const pathLoading = ref<boolean>(false);
const pathEmptyReason = ref<string | null>(null);
const iframeSrc = ref<string>('');
const currentToken = ref<string>('');
// 页面显示名映射：切页重挂载时先取会话缓存（builderLabels），详情返回后再以清单为准；null = 尚未知晓（顶栏显示骨架）
const pageLabelMap = ref<Record<string, string> | null>(cachedPageLabel(page));
const rowLayouts = ref<RowLayout[]>([]);
// 调色板 Shape 目录（page-detail 聚合返回：区域 allowedShapes 并集 ∪ 既有面板 Shape，§7.1）
const paletteShapesRaw = ref<any[]>([]);
const shapeLabelMap = ref<Record<string, string>>({});
// Record<regionName, useRegionDraft 实例>；ref 深层响应，嵌套 ref 自动解包
// 记录按解包后形态（RegionDraftState）声明：Vue 的 UnwrapRef 不展开记录值内的 ref，运行期 reactive 会——
// 实例放入时经 asState 断言，读取侧即得解包后的形态
const drafts = ref<Drafts>({});
const asState = (draft: RegionDraft): RegionDraftState => draft as unknown as RegionDraftState;
const regionOrder = ref<string[]>([]);
const selection = ref<BridgeSelection | null>(null);
const structureMode = ref<boolean>(false);

const pageLabel = computed<string>(() => (pageLabelMap.value == null ? '' : localizedLabel(pageLabelMap.value, page)));
const draftRegionNames = computed<string[]>(() => regionOrder.value.filter((name) => drafts.value[name]?.hasDraft));
const anySaving = computed<boolean>(() => regionOrder.value.some((name) => drafts.value[name]?.saving));
const selectedDraft = computed<RegionDraftState | null>(() => (selection.value?.region != null ? (drafts.value[selection.value.region] ?? null) : null));
// 区域名 → 显示名（清单 label；旧后端无字段时回退区域名）——顶栏状态/确认文案/桥角标共用
const regionLabels = computed<Record<string, string>>(() => Object.fromEntries(regionOrder.value.map((name) => [name, regionLabelOf(drafts.value[name], name)])));
const regionNames = (names: string[]): string => names.map((name) => regionLabelOf(drafts.value[name], name)).join(t('punct.enum'));

// ---- 范围覆盖（visual-builder §9，2026-07-18 情境化改版）：编辑范围随预览页面推导，不再由下拉选择。
// 操作员模型只有两态：共享布局（页面默认，改动作用于该类型全部页面）/ 栏目独立布局（channel:<id>
// 覆盖行）。仅栏目页可进入独立布局（预览路径可本地解析出当前栏目）；变体覆盖行仍被渲染解析，
// 但控制台不再提供其编辑入口（预发布重置，docs/decisions/visual-builder/03 修订记）。

const SCOPE_DEFAULT = 'default';
// 实例页 surface（同一布局被同类多个页面共用）：显示 共享布局 影响范围提示；也是变体目录的适用面
const INSTANCE_SURFACES = ['channel', 'article', 'topic'];
const isChannelPage = page === 'channel';
const hasVariantSurface = INSTANCE_SURFACES.includes(page);
const scope = ref<string>(SCOPE_DEFAULT);
// 本页已存在的覆盖范围（page-detail 响应 scopes）+ 本次会话进入定制但尚未首存（后端还无行）的范围
const existingScopes = ref<string[]>([]);
const sessionScopes = ref<string[]>([]);
const channelOptions = ref<any[]>([]);
const variantStore = useLayoutVariantStore();

// 当前范围的覆盖行是否已存在（任一区域存在即视为已创建；未创建时首次保存基于共享布局建行）
const scopeOverrideExists = computed<boolean>(() => scope.value !== SCOPE_DEFAULT && regionOrder.value.some((name) => drafts.value[name]?.overrideExists));

// 预览路径 → 当前栏目（[/subDir]/channel/<slug>，别名查栏目列表；解析不出 = 不提供定制入口）
const currentChannel = computed<any | null>(() => {
  if (!isChannelPage) return null;
  let path = pagePath.value;
  const subDir = site.value?.subDir;
  if (subDir && path.startsWith(`/${subDir}/`)) path = path.slice(subDir.length + 1);
  const match = /^\/channel\/([^/?#]+)/.exec(path);
  if (match == null) return null;
  const slug = decodeURIComponent(match[1] ?? '');
  return channelOptions.value.find((c: any) => c.slug === slug) ?? null;
});
const currentChannelScope = computed<string | null>(() => (currentChannel.value != null ? `channel:${currentChannel.value.id}` : null));
// chip/确认文案里的栏目名：独立布局态取范围所指栏目（正常即当前栏目），共享态取当前栏目（定制入口提示）
const scopeChannelName = computed<string>(() => {
  if (scope.value.startsWith('channel:')) {
    const id = scope.value.slice('channel:'.length);
    return channelOptions.value.find((c: any) => String(c.id) === id)?.name ?? `#${id}`;
  }
  return currentChannel.value?.name ?? '';
});

// 顶栏范围态：hidden = 单例页（无歧义无 chip）；shared = 共享布局 chip（+ 栏目页定制入口）；
// own/ownPending = 独立布局 chip + 恢复入口（Pending = 行未创建，首存生效）
const scopeState = computed<'hidden' | 'shared' | 'own' | 'ownPending'>(() => {
  if (!hasVariantSurface) return 'hidden';
  if (scope.value === SCOPE_DEFAULT) return 'shared';
  return scopeOverrideExists.value ? 'own' : 'ownPending';
});

// 首个候选（preview-url-resolver §5.3）：变体切换自动寻址用；候选整体返回（url + variant，
// 到达后作变体随页解析的优先对照）；失败/零候选 → null
const firstCandidate = async (params: { variant?: string; channelId?: string }): Promise<PreviewUrlCandidate | null> => {
  try {
    const result = await queryPreviewUrls({ page, limit: 1, ...params });
    return result.candidates?.[0] ?? null;
  } catch {
    return null;
  }
};

// 范围切换（定制入口 / 路径推导共用）：重载区域草稿。预览画布不在此重建——路径驱动的切换由导航
// 自身重渲染；定制入口在首存前渲染结果与共享布局一致（copy-on-first-save），无需刷新
const applyScope = async (val: string): Promise<void> => {
  if (val === scope.value) return;
  scope.value = val;
  selection.value = null;
  loading.value = true;
  try {
    await loadRegions();
  } finally {
    loading.value = false;
  }
};

// 编辑范围随预览路径（§9 情境化）：预览的栏目存在覆盖行（服务端 scopes ∪ 会话待建）即自动进入
// ——所见即所编；离开该栏目（或路径解析不出栏目）自动回到共享布局。轨道切换后 existingScopes
// 换源，同一 watch 重推导（不再有跨轨道静默创建覆盖行的路径）
watch([currentChannelScope, existingScopes], () => {
  if (!isChannelPage) return;
  const candidate = currentChannelScope.value;
  const known = candidate != null && (existingScopes.value.includes(candidate) || sessionScopes.value.includes(candidate));
  const desired = known && candidate != null ? candidate : SCOPE_DEFAULT;
  if (desired !== scope.value) void applyScope(desired);
});

// 仅此栏目定制（§9 create-on-first-save：这里只登记范围并切换，行在首次保存时创建）
const handleCustomizeScope = async (): Promise<void> => {
  const value = currentChannelScope.value;
  if (value == null || scope.value !== SCOPE_DEFAULT) return;
  if (!existingScopes.value.includes(value) && !sessionScopes.value.includes(value)) {
    sessionScopes.value = [...sessionScopes.value, value];
  }
  await applyScope(value);
};

// 恢复为共享布局（§9 revert-to-default）：行已存在 = 确认后删除该栏目在本页的全部覆盖行（含其面板）
// 并整页软刷新（已发布渲染结果随删除改变）；行未创建（进入定制后从未保存）= 纯本地退出，免确认免请求
const handleRevertScope = async (): Promise<void> => {
  const current = scope.value;
  if (current === SCOPE_DEFAULT) return;
  const names = regionOrder.value.filter((name) => drafts.value[name]?.overrideExists);
  if (names.length > 0) {
    try {
      await ElMessageBox.confirm(t('builder.scope.revertConfirm', { name: scopeChannelName.value, regions: regionNames(names) }), t('region.confirm.title'), {
        type: 'warning',
      });
    } catch {
      return; // 取消
    }
    for (const name of names) {
      const draft = drafts.value[name];
      if (draft == null) continue;
      try {
        await deleteRegionOverride({ page, region: name, scope: current, version: draft.version });
      } catch {
        // 全局错误处理已提示（409 = 他人正在编辑该覆盖）
      }
    }
  }
  sessionScopes.value = sessionScopes.value.filter((s) => s !== current);
  ElMessage.success(t('builder.scope.reverted'));
  await applyScope(SCOPE_DEFAULT);
  if (names.length > 0) {
    bridge.refresh('page');
  }
};

// ---- 全站共享区域（page-regions §15）：状态按区域（同页可有自有 main + 共享 aside），显示在检查器。
// shared = 跟随宿主行（编辑作用于全站）；own = 本页已分叉；ownPending = 本地已分叉、首存建行 ----------
const shareStateOf = (name: string): ShareState => {
  const draft = drafts.value[name];
  if (draft == null || draft.sharedFrom == null) return 'none';
  // 栏目定制态由顶栏范围 chip 表达；共享/分叉只在页面默认范围下有意义
  if (scope.value !== SCOPE_DEFAULT) return 'none';
  if (draft.page !== page) return 'shared';
  return draft.overrideExists ? 'own' : 'ownPending';
};
const shareStates = computed<Record<string, ShareState>>(() => Object.fromEntries(regionOrder.value.map((name) => [name, shareStateOf(name)])));

// 仅此页面定制（copy-on-first-save，与 §9 栏目定制同款）：本地以宿主已发布树为种子建本页坐标的草稿，
// 行在首次保存时由服务端创建（面板ID剥离复制）；此前一切只是本地状态
const handleForkRegion = (name: string): void => {
  const current = drafts.value[name];
  if (current == null || current.detail == null || shareStateOf(name) !== 'shared') return;
  const draft = useRegionDraft({
    page,
    region: name,
    scope: SCOPE_DEFAULT,
    onHistoryPush: () => {
      undoOrder.value = [...undoOrder.value, name];
      redoOrder.value = [];
    },
  });
  draft.applyDetail({ ...current.detail, page, scope: SCOPE_DEFAULT, draft: null, overrideExists: false, version: 0, forked: false });
  // 就地替换实例（响应式记录内的 ref 由 reactive 自动解包，与初始装载同一形态；类型按解包后形态断言）
  drafts.value[name] = asState(draft);
  undoOrder.value = undoOrder.value.filter((n) => n !== name);
  redoOrder.value = redoOrder.value.filter((n) => n !== name);
  ElMessage.success(t('builder.share.forked'));
};

// 恢复为全站共享：行已存在 = 确认后删除本页该区域的默认行（含其面板）并整页软刷新；本地分叉未保存 = 纯重载
const handleUnforkRegion = async (name: string): Promise<void> => {
  const state = shareStateOf(name);
  const draft = drafts.value[name];
  if (draft == null || (state !== 'own' && state !== 'ownPending')) return;
  if (state === 'own') {
    try {
      await ElMessageBox.confirm(t('builder.share.revertConfirm', { region: regionLabelOf(draft, name) }), t('region.confirm.title'), { type: 'warning' });
    } catch {
      return; // 取消
    }
    try {
      await deleteRegionOverride({ page, region: name, scope: SCOPE_DEFAULT, version: draft.version });
    } catch {
      return; // 全局错误处理已提示（409 = 他人正在编辑）
    }
  }
  loading.value = true;
  try {
    await loadRegions();
  } finally {
    loading.value = false;
  }
  ElMessage.success(t('builder.share.reverted'));
  if (state === 'own') {
    bridge.refresh('page');
  }
};

// ---- 预览桥（画布 iframe） ----------------------------------------------------------------------

const stageRef = ref<InstanceType<typeof CanvasStage>>();
const iframeRef = ref<HTMLIFrameElement>();
watchEffect(() => {
  iframeRef.value = (stageRef.value as any)?.iframe ?? undefined;
});

// 画布来源的选择不回滚视口（元素已在视野内）；检查器/结构视图来源的选择令桥滚动到元素（flush:'sync' 的选择 watch 读此标记）
let selectionFromCanvas = false;

// 清选择态与设置态（画布点空白 / 画布 Esc 的 {kind:null} 哨兵、控制台 Esc 共用）：检查器回到区域列表
const clearSelection = (): void => {
  settingsScope.value = null;
  settingsFocus.value = '';
  selection.value = null;
};

const onSelect = (sel: BridgeSelection | null): void => {
  selectionFromCanvas = true;
  // 点空白处 / Esc 取消选择（桥 {kind:null} 哨兵）
  if (sel == null) {
    clearSelection();
    return;
  }
  // 齿轮锚点（§8.1 P5）：data-ujcms-setting 的 select {kind:'setting', id:'group.field'} 直接打开
  // 主题设置检查器并滚动到对应组/字段；其余选择清设置态
  if (sel.kind === 'setting') {
    selection.value = sel;
    settingsFocus.value = sel.id;
    void openSettings('theme', true);
    return;
  }
  settingsScope.value = null;
  settingsFocus.value = '';
  // 区域点选：id 即区域名——旧版（浏览器缓存）IIFE 的负载缺 region 字段，这里规范化兜底，
  // 否则 selectedDraft 取不到草稿、检查器误判「不在当前草稿中」且添加行入口消失
  selection.value = sel.kind === 'region' && sel.region == null ? { ...sel, region: sel.id } : sel;
};

// 内容深链（§8.3 P5，D5）：桥内 编辑文章/编辑栏目 悬停按钮 → 打开控制台
// 既有 ArticleForm/ChannelForm（对话框宿主）→ 保存后整页刷新
const articleFormVisible = ref<boolean>(false);
const channelFormVisible = ref<boolean>(false);
const quickEditArticleId = ref<string>();
const quickEditChannelId = ref<string>();

const handleQuickEdit = (entity: 'article' | 'channel', id: string): void => {
  if (entity === 'article') {
    quickEditArticleId.value = id;
    articleFormVisible.value = true;
  } else {
    quickEditChannelId.value = id;
    channelFormVisible.value = true;
  }
};

// 点击穿透（navigate）：校验预览页面自身源后重写为草稿预览地址（同令牌、新随机数）
const onNavigate = (url: string): void => {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return;
  }
  if (parsed.origin !== previewTargetOrigin()) return;
  const rawPath = templatePreviewBase && parsed.pathname.startsWith(templatePreviewBase) ? parsed.pathname.slice(templatePreviewBase.length) : parsed.pathname;
  // 协议相对路径（//host）会把 _ptoken 拼到外域——同源检查通过但 pathname 为 //host。拒绝（防御纵深）。
  if (rawPath.startsWith('//')) return;
  parsed.searchParams.delete('_preview');
  parsed.searchParams.delete('_ptoken');
  parsed.searchParams.delete('_pchannel');
  const qs = parsed.searchParams.toString();
  pagePath.value = `${rawPath || '/'}${qs ? `?${qs}` : ''}`;
  // 令牌临期/已过期不再复用（P1-2）：过期令牌拼出的地址会落到「已发布页 + 死桥」（超时降级结构视图），
  // 改走 buildSrc 重铸后导航
  if (!heartbeat.isFresh()) {
    void buildSrc();
    return;
  }
  iframeSrc.value = buildPreviewUrl({
    path: pagePath.value,
    token: currentToken.value,
    channel: bridge.newChannel(previewTargetOrigin()),
  });
};

// 桥超时（主题缺预览注入等）→ 自动降级到结构视图（§10 降级矩阵）
const onTimeout = (): void => {
  if (!structureMode.value) {
    structureMode.value = true;
    ElMessage.warning(t('builder.bridge.timeout'));
  }
};

// 桥握手（含模板轨道整页重载后的重新握手）：补发当前选择的选择环
const onReady = (): void => {
  if (selection.value != null) {
    bridge.select({ kind: selection.value.kind, id: selection.value.id });
  }
};

// 预览会话失效（_ptoken 过期，桥刷新自取发现响应缺预览标记 → previewLost）：重铸令牌重建画布。
// 旧行为是桥自行整页重载——落到「已发布页 + 死桥」，画布与草稿静默失同步（结构视图/画布内容不一致）。
// 心跳（下方）到期前主动重铸后，此路径退为兜底（如 busy 连续跳过越过到期点、后端 TTL 调短）
let renewingPreview = false;
const onPreviewLost = async (): Promise<void> => {
  if (renewingPreview) return;
  renewingPreview = true;
  try {
    await buildSrc();
    ElMessage.info(t('builder.bridge.previewRenewed'));
  } finally {
    renewingPreview = false;
  }
};

// 预览令牌心跳（P1-2/B2）：令牌在 iframe URL 里逐请求校验且不可过桥，到期前唯一的应用方式是
// 重建画布 URL（buildSrc）——故仅在空闲（无拖拽/放置/对话框/保存）且标签可见时静默重铸，
// 后台休眠靠绝对时刻 + visibilitychange/focus 补检。成功路径无提示（无损）；previewRenewed 留给兜底
const previewBusy = (): boolean =>
  loading.value ||
  anySaving.value ||
  renewingPreview ||
  canvasDragging.value ||
  placing.value != null ||
  paletteDragActive ||
  pendingPlacement != null ||
  panelFormVisible.value ||
  articleFormVisible.value ||
  channelFormVisible.value ||
  historyVisible.value;
const heartbeat = usePreviewTokenHeartbeat({ isBusy: previewBusy, renew: () => buildSrc() });

// 最近一次几何快照（§4 layout，文档坐标）——拖拽状态机的命中测试基准
const layoutSnapshot = ref<any | null>(null);

// 拖拽/放置事件延迟绑定：dragCtl 与放置模式在 bridge 之后创建（其 dropHint/dropZones/autoScroll
// 依赖 bridge），消息异步到达，运行期安全
const bridge = usePreviewBridge(iframeRef, {
  onSelect,
  onNavigate,
  onTimeout,
  onReady,
  onPreviewLost,
  onQuickEdit: handleQuickEdit,
  onLayout: (layout) => {
    layoutSnapshot.value = layout;
  },
  onDragStart: (payload) => {
    exitPlacement();
    dragCtl.onDragStart(payload);
  },
  onDragMove: (x, y) => dragCtl.onDragMove(x, y),
  onDrop: (x, y) => void dragCtl.onDrop(x, y),
  onDragCancel: () => dragCtl.onDragCancel(),
  onZoneClick: (target) => handleZoneClick(target),
  onPlaceCancel: () => exitPlacement(),
  onDeleteIntent: (payload) => void handleDeleteIntent(payload),
  // 画布内快捷键（桥仅转发动作）：与控制台 keydown 同一分发与守卫
  onKey: (key) => void runShortcut(key),
});
const { bridgeTimedOut, bridgeReady } = bridge;

// 画布装载态：iframe 地址已下发、桥尚未握手且未超时（newChannel 每次装载复位 ready）
const stageLoading = computed<boolean>(() => iframeSrc.value !== '' && !bridgeReady.value && !bridgeTimedOut.value);

// 画布名称角标与角标文案的显示名映射（shell-polish）：装载/变更时下发桥；桥每次 ready 由组合式函数重发
watch(
  [shapeLabelMap, rowLayouts, regionLabels],
  () => {
    bridge.setLabels({
      shapeLabels: { ...shapeLabelMap.value },
      layoutLabels: Object.fromEntries(rowLayouts.value.map((l) => [l.key, localizedLabel(l.label, l.key)])),
      regionLabels: { ...regionLabels.value },
    });
  },
  { immediate: true, deep: true },
);

// ---- 画布拖拽（P2 §7.3 桥流式坐标 / P3 §7.2 调色板指针坐标 → 状态机 → save-per-op + 区域刷新） ----

const layoutColumns = (key: string): number => (rowLayouts.value.find((l: any) => l.key === key) ?? {}).columns ?? 1;

// 画布延迟提交的待确认放置（新增面板落点不再即时保存——模板轨道整页重载曾在拖放瞬间打断手势且
// 每次新增重载两次）：PanelForm 确定 = 首次保存；取消/关闭 = 经回滚闭包纯本地撤销。
// PanelForm 先发 finished 再关窗（同步），handlePanelSaved 顶部即清此标记，关窗回滚 watcher 竞态安全。
let pendingPlacement: { panel: PanelVM; rollback: () => void } | null = null;

const dragCtl = useBuilderDrag({
  drafts,
  snapshot: layoutSnapshot,
  t,
  labelFor: (kind, info) => (kind === 'panel' ? (shapeLabelMap.value[info.shape ?? ''] ?? info.shape ?? '') : `${t('region.row')} ${(info.rowIndex ?? 0) + 1}`),
  layoutColumns,
  dropHint: bridge.dropHint,
  dropZones: bridge.dropZones,
  autoScroll: bridge.autoScroll,
  commit: async (region: string): Promise<boolean> => {
    const draft = drafts.value[region];
    if (draft != null && (await draft.save())) {
      bridge.refresh('region', region);
      return true;
    }
    return false;
  },
  // 新面板已置入本地草稿（点击放置/调色板拖拽统一终点，延迟提交）：登记回滚闭包并打开 PanelForm
  // ——确定才首次保存 + 区域刷新；选择环推迟到保存回填真实面板ID之后（放置时ID尚为空）
  onPlaced: (region: string, panel: PanelVM, rollback: () => void) => {
    pendingPlacement = { panel, rollback };
    openPanelForm({ region, panel });
  },
});
const { dragging: canvasDragging, ghost: canvasGhost } = dragCtl;

// ---- 调色板（P3 §7.1/§7.2/§7.4）------------------------------------------------------------------

// 点击放置模式（D4，无障碍/触屏路径）：点击调色板条目 → 计算有效投放区（§7.4 无效排除）→
// 桥高亮为可点击区块 → 点击放置（save-per-op + PanelForm）。Escape / 区外点击 / 再次点击条目退出。
const placing = ref<{ shape: string } | null>(null);

const exitPlacement = (): void => {
  if (placing.value == null) return;
  placing.value = null;
  bridge.dropZones([]);
};

const handlePalettePick = (shape: string): void => {
  if (placing.value?.shape === shape) {
    exitPlacement();
    return;
  }
  const zones = dragCtl.computeAddZones(shape);
  if (zones.length === 0) {
    ElMessage.warning(t('builder.palette.noZone'));
    return;
  }
  selection.value = null;
  placing.value = { shape };
  bridge.dropZones(zones, true);
};

const handleZoneClick = (target: DropTarget): void => {
  const current = placing.value;
  if (current == null) return;
  // 桥侧点击即清区退出放置模式；控制台随之复位，placeShape 对照草稿树再校验（本地构建，延迟提交）
  placing.value = null;
  dragCtl.placeShape(current.shape, target);
};

// 幽灵拖拽（§7.2）：调色板按下拖出 → 屏蔽层 + 幽灵卡（CanvasStage，dragging 驱动）→ 控制台
// 指针坐标换算为 iframe 文档坐标（docX = clientX − iframeRect.left + scroll.x）送入同一状态机
let paletteDragActive = false;

const toDocCoords = (e: PointerEvent): { x: number; y: number } | null => {
  const rect = iframeRef.value?.getBoundingClientRect();
  const scroll = layoutSnapshot.value?.scroll;
  if (rect == null || scroll == null) return null;
  return { x: Math.round(e.clientX - rect.left + (scroll.x ?? 0)), y: Math.round(e.clientY - rect.top + (scroll.y ?? 0)) };
};

const onPaletteDragMove = (e: PointerEvent): void => {
  const pos = toDocCoords(e);
  if (pos != null) dragCtl.onDragMove(pos.x, pos.y);
};

const onPaletteDragUp = (e: PointerEvent): void => {
  teardownPaletteDrag();
  const pos = toDocCoords(e);
  if (pos != null) {
    void dragCtl.onDrop(pos.x, pos.y);
  } else {
    dragCtl.onDragCancel();
  }
};

const teardownPaletteDrag = (): void => {
  paletteDragActive = false;
  window.removeEventListener('pointermove', onPaletteDragMove);
  window.removeEventListener('pointerup', onPaletteDragUp);
};

const cancelPaletteDrag = (): void => {
  if (!paletteDragActive) return;
  teardownPaletteDrag();
  dragCtl.onDragCancel();
};

const handlePaletteDragStart = ({ kind, name, label, event }: PaletteDragStart): void => {
  exitPlacement();
  if (kind === 'layout') {
    dragCtl.startPaletteRowDrag(name, label);
  } else {
    dragCtl.startPaletteDrag(name, label);
  }
  paletteDragActive = true;
  window.addEventListener('pointermove', onPaletteDragMove);
  window.addEventListener('pointerup', onPaletteDragUp);
  onPaletteDragMove(event);
};

// 进入结构视图（顶栏开关 / 画布降级按钮 / 桥超时自动降级，三个入口统一在此收口）：画布态的
// 放置模式与调色板幽灵拖拽无法对着隐藏 iframe 继续，一律取消（P7 §3.4）
watch(structureMode, (val) => {
  if (val) {
    exitPlacement();
    cancelPaletteDrag();
    bridge.hover(null);
    return;
  }
  // 回到画布（canvas-chrome D29）：结构视图期间的选择曾发往 display:none 的 iframe（零矩形，scrollIntoView 无效；
  // 环由桥的 ResizeObserver 重画但不滚动）——重发并滚到元素
  const sel = selection.value;
  if (sel != null) void nextTick(() => bridge.select({ kind: sel.kind, id: sel.id }, { scroll: true }));
});

// 控制台文档内的键盘（焦点在控制台时桥收不到键）：Escape 退出放置模式 / 取消调色板拖拽；
// ⌘/Ctrl+Z、⇧⌘Z/Ctrl+Y、Delete 走与顶栏按钮同一分发（runShortcut）；输入控件内/重复按键/输入法合成不劫持
const onConsoleKeydown = (e: KeyboardEvent): void => {
  if (e.key === 'Escape') {
    if (placing.value != null || paletteDragActive) {
      exitPlacement();
      cancelPaletteDrag();
      return;
    }
    // 清选择（canvas-chrome D26，焦点在控制台时）：画布手柄拖拽中的 Esc 归桥（拖拽起点 window.focus() 已把焦点交给
    // iframe）；弹层打开 / 焦点在输入控件 / 已被处理的不劫持。不经 runShortcut——其 previewBusy 守卫（保存中等）不应拦 Esc
    if (canvasDragging.value || modalOpen() || isEditableTarget(e.target) || e.defaultPrevented) return;
    if (selection.value != null || settingsScope.value != null) {
      clearSelection();
      e.preventDefault();
    }
    return;
  }
  if (e.defaultPrevented || e.repeat || e.isComposing || isEditableTarget(e.target)) return;
  const action = shortcutOf(e);
  if (action != null && runShortcut(action)) e.preventDefault();
};
window.addEventListener('keydown', onConsoleKeydown);
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onConsoleKeydown);
  teardownPaletteDrag();
});

// 调色板条目（§7.1）：按 presentation.group 分组（回退第一个 tag，再回退「其他」）、order 排序；
// §7.4 无任何区域可放置的条目置灰并给出原因（轨道缺失 / 无区域允许 / 可放置位置已满）
const paletteItems = computed<PaletteItem[]>(() => {
  const hasSnapshot = (layoutSnapshot.value?.regions ?? []).length > 0;
  const shapeItems = paletteShapesRaw.value.map((s: any) => {
    const group = localizedLabel(s.presentation?.group, s.tags?.[0] ?? t('builder.palette.groupOther'));
    let disabled = false;
    let disabledReason = '';
    if (s.missing) {
      disabled = true;
      disabledReason = t('builder.palette.missing');
    } else {
      const anyAllows = regionOrder.value.some((name) => {
        const d = drafts.value[name];
        return d != null && shapeAllowed(d, s.name).ok;
      });
      if (!anyAllows) {
        disabled = true;
        disabledReason = t('builder.palette.noRegionAllows');
      } else if (hasSnapshot && dragCtl.computeAddZones(s.name).length === 0) {
        disabled = true;
        disabledReason = t('builder.palette.allFull');
      }
    }
    return {
      kind: 'shape' as const,
      name: s.name,
      label: localizedLabel(s.label, s.name),
      description: s.description ?? '',
      icon: s.presentation?.icon ?? '',
      // 服务端解析后的缩略图 URL（P6/D11：data URI / 核心静态前缀 / 主题模板存储前缀；null 回退 icon）
      thumbnail: s.thumbnail ?? null,
      group,
      order: s.presentation?.order ?? 0,
      missing: !!s.missing,
      disabled,
      disabledReason,
    };
  });
  // 布局卡片（行布局目录）：拖到画布行间隙 / 结构视图行列表插入空行——添加行 的拖拽形态。
  // 置灰口径基于草稿树（行数在草稿而非快照中，首个 layout 快照到达前亦可判定）
  const layoutItems = rowLayouts.value.map((l, i) => {
    const eligible = regionOrder.value.filter((name) => {
      const d = drafts.value[name];
      return d != null && layoutAllowed(d, l.key).ok;
    });
    let disabled = false;
    let disabledReason = '';
    if (eligible.length === 0) {
      disabled = true;
      disabledReason = t('builder.palette.layoutNotAllowed');
    } else if (
      eligible.every((name) => {
        const d = drafts.value[name];
        return d != null && !rowsFull(d).ok;
      })
    ) {
      disabled = true;
      disabledReason = t('builder.palette.rowsFull');
    }
    return {
      kind: 'layout' as const,
      name: l.key,
      label: localizedLabel(l.label, l.key),
      description: t('builder.palette.layoutColumns', { count: l.columns }),
      widths: [...(l.widths ?? [])],
      columns: l.columns,
      group: t('builder.palette.groupLayout'),
      order: i,
      disabled,
      disabledReason,
    };
  });
  return [...shapeItems, ...layoutItems];
});

// 结构视图调色板拖入的 :move 校验（P7，§7.4 对齐；规则在 constraints.ts）：SortableJS 的 onMove 取自
// 拖拽「起点」列表（rootEl）的实例选项——绑在目标列上对调色板拖拽不触发，故校验挂调色板源侧，经
// evt.to 的 data-builder-* 在草稿树定位目标（Shape 卡片：列 = allowedShapes + maxPanelsPerColumn；
// 空区域 = allowedShapes + maxRows。布局卡片：行列表/空区域 = allowedRowLayouts + maxRows）。
// 返回 false 时 SortableJS 给出原生禁放反馈；服务端保存时照旧复验（shipped 兜底）。
const handlePaletteStructureMove = (evt: any): boolean => {
  const toEl: HTMLElement | null = evt.to instanceof HTMLElement ? evt.to : null;
  const region = toEl?.dataset?.builderRegion;
  // 非列/行列表/空区域目标：组接受规则下仅剩拖回调色板的还原分支（revert）——放行，拖回源侧即取消
  if (toEl == null || region == null) return true;
  const item = evt.draggedContext?.element;
  const draft = drafts.value[region];
  if (item?.name == null || draft == null) return false;
  if (item.kind === 'layout') return canInsertRow(draft, item.name).ok;
  if (toEl.dataset.builderEmptyRegion != null) return canAddShape(draft, item.name, {}).ok;
  const col = Number(toEl.dataset.builderCol);
  const row = toEl.dataset.builderRow;
  if (row == null || !Number.isInteger(col)) return false;
  return canAddShape(draft, item.name, { row, col }).ok;
};

// ---- 撤销/重做（D8：区域各一逆操作栈；顶栏按跨区域的全局操作顺序调度） ---------------------------

const undoOrder = ref<string[]>([]);
const redoOrder = ref<string[]>([]);
const canUndo = computed<boolean>(() => undoOrder.value.some((name) => drafts.value[name]?.canUndo));
const canRedo = computed<boolean>(() => redoOrder.value.some((name) => drafts.value[name]?.canRedo));

const handleUndo = async (): Promise<void> => {
  while (undoOrder.value.length > 0) {
    const region = undoOrder.value[undoOrder.value.length - 1];
    undoOrder.value = undoOrder.value.slice(0, -1);
    const draft = region != null ? drafts.value[region] : undefined;
    // 已发布/丢弃的区域条目失效（区域栈随 fetchDetail 清空）：跳过
    if (region == null || draft == null || !draft.canUndo) continue;
    if (await draft.undo()) {
      redoOrder.value = [...redoOrder.value, region];
      bridge.refresh('region', region);
    }
    return;
  }
};

const handleRedo = async (): Promise<void> => {
  while (redoOrder.value.length > 0) {
    const region = redoOrder.value[redoOrder.value.length - 1];
    redoOrder.value = redoOrder.value.slice(0, -1);
    const draft = region != null ? drafts.value[region] : undefined;
    if (region == null || draft == null || !draft.canRedo) continue;
    if (await draft.redo()) {
      undoOrder.value = [...undoOrder.value, region];
      bridge.refresh('region', region);
    }
    return;
  }
};

// ---- 快捷键分发（shell-polish）：控制台 keydown 与桥转发的 key 消息同终点，与顶栏按钮同一守卫
//（权限 / 弹层打开 / 装载与保存等忙碌态） -----------------------------------------------------------
const runShortcut = (action: ShortcutAction): boolean => {
  if (perm('pageRegion:update') || loading.value || modalOpen() || previewBusy()) return false;
  if (action === 'undo') {
    if (!canUndo.value) return false;
    void handleUndo();
    return true;
  }
  if (action === 'redo') {
    if (!canRedo.value) return false;
    void handleRedo();
    return true;
  }
  const sel = selection.value;
  if (sel?.kind === 'panel') {
    void handleDeleteIntent({ kind: 'panel', id: sel.id, region: sel.region });
    return true;
  }
  if (sel?.kind === 'row' && sel.region != null) {
    void handleDeleteRowIntent(sel.id, sel.region);
    return true;
  }
  return false;
};

// 检查器驱动的选择（区域卡片/面包屑/结构视图）→ 同步 iframe 内选择环并滚动到元素；画布来源的选择回发幂等且不滚动
watch(
  selection,
  (sel) => {
    // 点选会卸载被悬停的区域卡片（其 mouseleave 不再触发）：先清宿主侧悬停锚点
    bridge.hover(null);
    bridge.select(sel != null ? { kind: sel.kind, id: sel.id } : null, { scroll: !selectionFromCanvas });
    selectionFromCanvas = false;
  },
  { flush: 'sync' },
);

// 宿主侧悬停锚点（canvas-chrome D30）：检查器区域卡片 / 面包屑祖先悬停 → 画布同款悬停 chrome；结构视图下画布隐藏，不下发
const handleHover = (target: BridgeSelection | null): void => {
  if (!structureMode.value) bridge.hover(target);
};

const buildSrc = async (): Promise<void> => {
  exitPlacement();
  let minted: { token: string; expiresIn: number };
  try {
    minted = await previewToken();
  } catch {
    ElMessage.error(t('pagePreview.tokenError'));
    return;
  }
  currentToken.value = minted.token;
  // 心跳按绝对到期时刻武装（P1-2）：到期前空闲时静默走本函数重铸
  heartbeat.arm(minted.expiresIn);
  iframeSrc.value = buildPreviewUrl({
    path: pagePath.value,
    token: minted.token,
    channel: bridge.newChannel(previewTargetOrigin()),
  });
  bridge.setEditMode(true);
};

const handleRender = async (): Promise<void> => {
  await buildSrc();
};

// 新标签页整站草稿试看：另铸一枚令牌（新标签页无心跳续期，给足 TTL），不复用 iframe 地址——
// 后者带 _pchannel 且开发期走同源代理前缀（页内根相对链接会落到控制台 SPA）
const openInNewTab = async (): Promise<void> => {
  if (!iframeSrc.value) return;
  let minted: { token: string; expiresIn: number };
  try {
    minted = await previewToken();
  } catch {
    ElMessage.error(t('pagePreview.tokenError'));
    return;
  }
  window.open(buildNewTabPreviewUrl({ path: pagePath.value, token: minted.token }), '_blank');
};

// ---- 区域草稿装载（P3：page-detail 聚合一次取回区域详情 + 调色板目录，§7.1） ---------------------

const loadRegions = async (): Promise<void> => {
  exitPlacement();
  // 组件设置结构缓存随区域重载失效（主题重扫/切换后检查器按最新声明渲染）
  invalidateShapeDefsCache();
  const scopeAtCall = scope.value;
  const detail: PageDetailResult = await queryPageDetail(page, scopeAtCall !== SCOPE_DEFAULT ? scopeAtCall : undefined);
  // 范围竞态防护（§9 情境化：快速连续导航可致并发装载）：装载期间范围又变，丢弃本次结果
  if (scope.value !== scopeAtCall) return;
  pageLabelMap.value = detail?.pageLabel ?? {};
  rememberPageLabel(page, pageLabelMap.value);
  paletteShapesRaw.value = detail?.shapes ?? [];
  // 已存在的覆盖范围目录（范围切换器，§9）
  existingScopes.value = detail?.scopes ?? [];
  shapeLabelMap.value = Object.fromEntries(paletteShapesRaw.value.map((s: any) => [s.name, localizedLabel(s.label, s.name)]));
  const map: Drafts = {};
  const order: string[] = [];
  for (const r of detail?.regions ?? []) {
    const name: string = r.region;
    const draft = useRegionDraft({
      // 共享区域未分叉时详情坐标是宿主页（page-regions §15）：草稿直接对宿主坐标读写，画布仍按区域名对应
      page: r.page ?? page,
      region: name,
      scope: scope.value,
      // 撤销栈入账 → 顶栏全局顺序（新操作使重做序列失效，标准撤销语义）
      onHistoryPush: () => {
        undoOrder.value = [...undoOrder.value, name];
        redoOrder.value = [];
      },
    });
    // 聚合已带区域详情：直接装载，免逐区域回读（发布/丢弃/冲突刷新仍走 fetchDetail）
    draft.applyDetail(r);
    map[name] = asState(draft);
    order.push(name);
  }
  drafts.value = map;
  regionOrder.value = order;
  undoOrder.value = [];
  redoOrder.value = [];
};

const sitePathFallback = (): string => {
  const subDir = site.value?.subDir;
  return subDir ? `/${subDir}` : '/';
};

// 路径候选装载（preview-url-resolver §5.2）：选择器下拉打开/远程 q 过滤/显式刷新共用；
// 静默失败（选择器退化为纯自由输入，与无清单主题的降级路径一致）
const fetchPathOptions = async (q?: string): Promise<void> => {
  pathLoading.value = true;
  try {
    const result = await queryPreviewUrls({ page, q: q || undefined });
    pathOptions.value = result.candidates ?? [];
    // q 过滤未命中也会返回 no-content——种子导入引导只在未过滤的空态出现，过滤空态走普通空文案
    pathEmptyReason.value = q ? null : (result.emptyReason ?? null);
  } catch {
    pathOptions.value = [];
    pathEmptyReason.value = null;
  } finally {
    pathLoading.value = false;
  }
};

// 页面感知默认路径（preview-url-resolver §5.1）：home 免请求短路；其余取页面首个候选；
// 零候选回退站点根路径，no-content 时提示导入演示数据（§6）。装载于 loadRegions 之后。
const applyDefaultPath = async (): Promise<void> => {
  if (page === 'home') {
    pagePath.value = sitePathFallback();
    return;
  }
  await fetchPathOptions();
  const first = pathOptions.value[0];
  if (first != null) {
    pagePath.value = first.url;
    return;
  }
  pagePath.value = sitePathFallback();
  if (pathEmptyReason.value === 'no-content') {
    ElMessage.info(t('builder.path.noContent'));
  }
};

onMounted(async () => {
  if (page === '') {
    ElMessage.error(t('region.missingParams'));
    return;
  }
  loading.value = true;
  try {
    const [currentSite, layouts] = await Promise.all([queryCurrentSite(), queryRowLayouts()]);
    site.value = currentSite;
    // 全屏路由无 AppHeader（currentSiteStore.currentSite 的常规写入点）：为深链表单
    // （ArticleForm 的 TinyMCE 排版配置等读取处）补种当前站点
    if (currentSiteStore.currentSite == null) {
      currentSiteStore.currentSite = currentSite;
    }
    rowLayouts.value = layouts ?? [];
    await loadRegions();
    await applyDefaultPath();
    // 栏目列表（§9 情境化：预览路径别名 → 栏目推导 + chip/确认文案里的栏目名，仅栏目页需要）
    // 与本页 surface 的变体目录（§8.2 变体切换器）
    if (isChannelPage) {
      queryChannelList()
        .then((list: any) => {
          channelOptions.value = list ?? [];
        })
        .catch(() => {});
    }
    if (hasVariantSurface) {
      variantStore.fetchBySurface(page as any).catch(() => {});
    }
  } finally {
    loading.value = false;
  }
  if (presetRegion !== '' && drafts.value[presetRegion] != null) {
    selection.value = { kind: 'region', id: presetRegion, region: presetRegion };
  }
  await buildSrc();
});

// 结构视图发布/丢弃后：区域级软刷新（模板轨道 = 整页重载 + 滚动还原）
const handleStructureChanged = (region: string): void => {
  bridge.refresh('region', region);
};

// 结构视图逐操作提交（P9 §3.1/D14）：微任务合并去重——一次跨列拖拽 = 源列 removed + 目标列 added
// 两个 @change，合并为恰一次 PUT、一条 D8 历史；再按区域串行排队到进行中的保存之后（saveDraft
// 版本链的 applySaveResult 为顺序回填语义）。draft.save 自吞异常（409 走冲突对话框、其余
// handleError 提示 + dirty 保持），链路不会中断；成功后区域级软刷新——与画布 commit 闭包同终点。
const regionCommits: Record<string, { queued: boolean; chain: Promise<void> }> = {};

const commitRegion = (draft: RegionDraftState): void => {
  const state = (regionCommits[draft.region] ??= { queued: false, chain: Promise.resolve() });
  if (state.queued) return;
  state.queued = true;
  state.chain = state.chain.then(async () => {
    state.queued = false;
    if (await draft.save()) {
      bridge.refresh('region', draft.region);
    }
  });
};

// 结构视图新增面板（添加面板下拉 / 调色板拖入）：复用画布 onPlaced 登记——两视图同一延迟提交
// 终点（P9 §3.2：PanelForm 确定即首存 + 区域刷新，取消经回滚闭包纯本地撤销、零保存）
const handleStructurePlaced = ({ region, panel, rollback }: PlacedPayload): void => {
  pendingPlacement = { panel, rollback };
  openPanelForm({ region, panel });
};

// ---- 检查器动作 ----------------------------------------------------------------------------------

// 面板编辑（PanelForm 对话框宿主）：保存 → 就地合并 → save-per-op → 区域刷新
const panelFormVisible = ref<boolean>(false);
const editingPanel = ref<PanelVM>();
const editingRegion = ref<string>('');

const openPanelForm = (payload: { region: string; panel: PanelVM } | PanelVM): void => {
  // 检查器（当前选中区域）与结构视图（携带 region）两个入口
  if ('panel' in payload) {
    editingRegion.value = payload.region;
    editingPanel.value = payload.panel;
  } else {
    editingRegion.value = selection.value?.region ?? '';
    editingPanel.value = payload;
  }
  panelFormVisible.value = true;
};

const handlePanelSaved = async (updated: PanelFormValues): Promise<void> => {
  const draft = drafts.value[editingRegion.value];
  if (editingPanel.value == null || draft == null) return;
  // 画布延迟提交的新面板：确定即首次保存。同步清除待回滚标记——PanelForm 先发 finished 再关窗，
  // 关窗回滚 watcher 随后运行时标记已空，不会误回滚刚保存的面板。
  // toRaw 对齐两侧：pendingPlacement 持原始对象，editingPanel 是 ref 深层转换后的 reactive 代理，
  // 直接 === 恒假 → 保存后关窗仍触发回滚，本地树静默丢面板（结构视图失同步，下次保存连带服务端删除）
  let placedPanel: PanelVM | null = null;
  if (pendingPlacement != null && toRaw(pendingPlacement.panel) === toRaw(editingPanel.value)) {
    placedPanel = editingPanel.value;
    pendingPlacement = null;
  }
  Object.assign(editingPanel.value, {
    enabled: updated.enabled,
    visibility: updated.visibility,
    settings: updated.settings,
  });
  draft.markDirty();
  const idBeforeSave = editingPanel.value.id;
  if (await draft.save()) {
    const idAfterSave = editingPanel.value.id;
    // 新面板：保存响应回填真实ID后才建立选择环（放置时ID为空，无从选中）
    if (placedPanel != null && placedPanel.id != null) {
      selection.value = { kind: 'panel', id: String(placedPanel.id), region: editingRegion.value };
    } else if (idBeforeSave != null && idAfterSave != null && idAfterSave !== idBeforeSave && selection.value?.kind === 'panel' && selection.value.id === idBeforeSave) {
      // 写时复制（后端 D20）：已发布面板的设置编辑在服务端复制为新投放，响应回填了新ID——
      // 选择先跟随副本，随后的区域刷新按新 data-panel-id 重建选择环；检查器按同一面板对象重载，不脏
      selection.value = { kind: 'panel', id: idAfterSave, region: editingRegion.value };
    }
    bridge.refresh('region', editingRegion.value);
  }
};

// 检查器内联保存（shell-polish）：与 PanelForm 确定同终点——就地合并 + save-per-op + 区域刷新
const panelSaving = ref<boolean>(false);
const handleInlinePanelSave = async ({ panel, values }: { panel: PanelVM; values: PanelFormValues }): Promise<void> => {
  editingRegion.value = selection.value?.region ?? '';
  editingPanel.value = panel;
  panelSaving.value = true;
  try {
    await handlePanelSaved(values);
  } finally {
    panelSaving.value = false;
  }
};

// 放置态 PanelForm 取消/关闭（未 finished）：回滚本地面板——零保存零刷新，不留空面板；画布与
// 结构视图的新增同走此契约（P9 §3.2）；编辑既有面板的入口 pendingPlacement 恒为空，watcher 不生效
watch(panelFormVisible, (visible) => {
  if (!visible && pendingPlacement != null) {
    pendingPlacement.rollback();
    pendingPlacement = null;
  }
});

// 行布局变更（检查器）：改列数 + save-per-op + 区域刷新
const handleRowLayoutChange = async ({ row, layout }: { row: RowVM; layout: string }): Promise<void> => {
  const draft = selectedDraft.value;
  if (draft == null) return;
  draft.changeLayout(row, layout, layoutColumns(layout));
  if (await draft.save()) {
    bridge.refresh('region', draft.region);
  }
};

// ---- 增删操作（P3；确认在检查器 Popconfirm，此处直接执行 + save-per-op + 区域刷新；撤销走 D8 栈） ----

// 按面板ID在草稿树中定位（桥上报的标识符按不可信数据核验；region 仅作定位提示，未命中则全量搜索）
type LocatedPanel = { draft: RegionDraftState; col: ColumnVM; index: number };
const locatePanelById = (id: string, region?: string): LocatedPanel | null => {
  const hinted = region != null ? drafts.value[region] : null;
  const candidates: RegionDraftState[] = hinted != null ? [hinted] : Object.values(drafts.value).filter((d) => d != null);
  for (const draft of candidates) {
    for (const row of draft.rows) {
      for (const col of row.columns) {
        const index = col.panels.findIndex((p) => String(p.id) === id);
        if (index >= 0) return { draft, col, index };
      }
    }
  }
  return null;
};

/** 删除已定位面板（检查器删除 / 画布删除意图 / 快捷键共用尾段）：移除 + 清选择 + save-per-op + 区域刷新。 */
const deleteLocatedPanel = async (found: LocatedPanel, panelId: string): Promise<void> => {
  found.draft.removePanel(found.col, found.index);
  // 选中即被删面板时清选择态
  if (selection.value?.kind === 'panel' && selection.value.id === panelId) {
    selection.value = null;
  }
  if (await found.draft.save()) {
    bridge.refresh('region', found.draft.region);
  }
};

const handleDeletePanel = async (panel: PanelVM): Promise<void> => {
  const found = panel.id != null ? locatePanelById(String(panel.id), selection.value?.region) : null;
  if (found == null) return;
  await deleteLocatedPanel(found, String(panel.id));
};

// 复制面板（检查器「复制」）：设置深拷贝原位其后插入 + save-per-op；保存响应回填真实ID后
// 把选择态切到新面板（与画布放置的选择时序同理，ID 为空无从选中）
const handleDuplicatePanel = async (panel: PanelVM): Promise<void> => {
  const found = panel.id != null ? locatePanelById(String(panel.id), selection.value?.region) : null;
  if (found == null) return;
  const copy = found.draft.duplicatePanel(found.col, found.index);
  if (copy == null) return;
  if (await found.draft.save()) {
    if (copy.id != null) {
      selection.value = { kind: 'panel', id: String(copy.id), region: found.draft.region };
    }
    bridge.refresh('region', found.draft.region);
  }
};

const handleDeleteRow = async (row: RowVM): Promise<void> => {
  const draft = selectedDraft.value;
  if (draft == null) return;
  const index = draft.rows.indexOf(row);
  if (index < 0) return;
  draft.removeRow(index);
  selection.value = null;
  if (await draft.save()) {
    bridge.refresh('region', draft.region);
  }
};

// 快捷键删除行：确认后按 id 重定位（对话框期间树可能变动），与检查器 Popconfirm 删除同终点
const handleDeleteRowIntent = async (rowId: string, region: string): Promise<void> => {
  if (perm('pageRegion:update') || drafts.value[region]?.findRow(rowId) == null) return;
  try {
    await ElMessageBox.confirm(t('region.row.delete.confirm'), t('region.confirm.title'), { type: 'warning' });
  } catch {
    return;
  }
  const draft = drafts.value[region];
  const index = draft?.rows.findIndex((r) => r.id === rowId) ?? -1;
  if (draft == null || index < 0) return;
  draft.removeRow(index);
  if (selection.value?.kind === 'row' && selection.value.id === rowId) {
    selection.value = null;
  }
  if (await draft.save()) {
    bridge.refresh('region', draft.region);
  }
};

// 画布删除意图（桥删除角标 删除面板；画布 编辑面板 角标及卡片级直达已按操作员反馈移除，2026-09-10 /
// 2026-07-06——点选面板即在检查器内联编辑，角标无可见效果）：桥无变更权——意图按不可信数据对照草稿树核验，
// ElMessageBox 二次确认后走检查器同款删除流程（save-per-op + 区域刷新 + D8 撤销）。
// 确认对话框打开期间树可能被并发操作改动，确认后按 id 重定位
const handleDeleteIntent = async ({ id, region }: BridgeCanvasIntent): Promise<void> => {
  if (perm('pageRegion:update')) return;
  if (locatePanelById(id, region) == null) return;
  try {
    await ElMessageBox.confirm(t('builder.inspector.deletePanel.confirm'), t('region.confirm.title'), { type: 'warning' });
  } catch {
    return;
  }
  const found = locatePanelById(id, region);
  if (found == null) return;
  await deleteLocatedPanel(found, id);
};

// 面包屑「行 N」：选中当前选择项的父行（区域上下文沿用当前选择）
const handleSelectRow = (rowId: string): void => {
  const region = selection.value?.region;
  if (region == null) return;
  selection.value = { kind: 'row', id: String(rowId), region };
};

const handleAddRow = async ({ layout }: { layout: string }): Promise<void> => {
  const draft = selectedDraft.value;
  if (draft == null) return;
  if (!draft.addRow(layout, layoutColumns(layout))) return;
  if (await draft.save()) {
    bridge.refresh('region', draft.region);
  }
};

// ---- 主题设置 / 页面设置（§8.1/§8.2，theme-marketplace A1 分范围端点） ---------------------------

const settingsScope = ref<'theme' | 'page' | null>(null);
const settingsLoading = ref<boolean>(false);
const settingsSaving = ref<boolean>(false);
// { groups, values, pages, pageValues, variants, variantValues } —— 三范围全量持有，保存全量回写
const settingsData = ref<any>(null);
// 齿轮锚点焦点（§8.1 P5）：'group.field'，检查器滚动 + 高亮后随下次选择清空
const settingsFocus = ref<string>('');

const variantKey = (v: any): string => `${v.surface}:${v.value}`;

// keepSelection：齿轮锚点入口保留 setting 选择（选择环留在 iframe 元素上）；顶栏按钮入口清选择
const openSettings = async (scope: 'theme' | 'page', keepSelection = false): Promise<void> => {
  if (!keepSelection) {
    selection.value = null;
    settingsFocus.value = '';
  }
  settingsScope.value = scope;
  if (settingsData.value != null) return;
  settingsLoading.value = true;
  try {
    const siteId = site.value?.id;
    // 编辑活动（桌面）主题的设置
    const result = await queryThemeSettings(siteId, site.value?.theme ?? '');
    const pageValues: Record<string, any> = {};
    (result.pages ?? []).forEach((p: any) => {
      pageValues[p.name] = p.values ?? {};
    });
    const variantValues: Record<string, any> = {};
    (result.variants ?? []).forEach((v: any) => {
      variantValues[variantKey(v)] = v.values ?? {};
    });
    settingsData.value = {
      groups: result.groups ?? [],
      values: result.values ?? {},
      pages: result.pages ?? [],
      pageValues,
      variants: result.variants ?? [],
      variantValues,
    };
  } catch {
    settingsScope.value = null;
  } finally {
    settingsLoading.value = false;
  }
};

// 设置值写入（检查器上抛；状态由本组件持有——ThemeList.vue 的 setSettingValue/setScopedValue 同型）
const handleSetGlobalValue = ({ group, field, value }: { group: string; field: string; value: any }): void => {
  const data = settingsData.value;
  if (data == null) return;
  (data.values[group] ??= {})[field] = value;
};
const handleSetPageValue = ({ page: pageName, group, field, value }: { page: string; group: string; field: string; value: any }): void => {
  const data = settingsData.value;
  if (data == null) return;
  ((data.pageValues[pageName] ??= {})[group] ??= {})[field] = value;
};
// 变体范围写入（P5 §8.2；key = '<surface>:<value>'——注意 groups[].fields 为字段名键控的 map，P1 偏差 #1）
const handleSetVariantValue = ({ key, group, field, value }: { key: string; group: string; field: string; value: any }): void => {
  const data = settingsData.value;
  if (data == null) return;
  ((data.variantValues[key] ??= {})[group] ??= {})[field] = value;
};

// ---- 布局变体切换器（P5 §8.2；随预览页改版 2026-07-24）---------------------------------------
// 语义：默认「随预览页」——切换器反映当前预览页解析出的活动变体（template-track-variants 重置后
// 模板轨道变体可从页面自身推导）；手动选择 = 临时编辑上下文覆盖（决定 页面设置 检查器编辑哪个
// variants.<surface>:<value> 存值，可为零候选变体预配置设置），下一次导航即恢复随页。
// 不改写任何实体列，与 §9 范围切换器（区域树覆盖）相互独立。解析不出 / 模板未绑定变体 → ''
//（顶栏显示「无变体绑定」占位；可选空项已移除——基础页面设置在检查器中恒显，空项无独立用途）。
const activeVariant = ref<string>('');
const variantManuallyPicked = ref(false);
// 变体自动寻址刚到达的候选（未必在 pathOptions 缓存里）：随页解析时优先对照
const addressedCandidate = ref<PreviewUrlCandidate | null>(null);
const variantOptions = computed<{ value: string; label: string }[]>(() =>
  hasVariantSurface ? variantStore.optionsFor(page as any).map((v: any) => ({ value: v.value, label: v.label })) : [],
);

// 预览页 → 活动变体解析：候选携带的 variant（preview-urls 响应）优先；栏目页兜底按
// channelTemplate 对照目录 template 绑定反查（与后端 resolveThemeVariantByTemplate 同规，
// 声明序首个命中）。解析不出 → ''。
const resolvedPathVariant = computed<string>(() => {
  if (!hasVariantSurface) return '';
  const url = pagePath.value;
  const candidate = addressedCandidate.value?.url === url ? addressedCandidate.value : pathOptions.value.find((c) => c.url === url);
  if (candidate != null) return candidate.variant ?? '';
  if (isChannelPage && currentChannel.value != null) {
    const rows = variantStore.optionsFor(page as any);
    const template = currentChannel.value.channelTemplate || 'channel';
    return rows.find((r: any) => r.template === template)?.value ?? '';
  }
  return '';
});

// 手动覆盖只活到下一次导航：路径一变即恢复随页
watch(pagePath, () => {
  variantManuallyPicked.value = false;
});
// 随页同步（候选/栏目列表/变体目录异步到位后同样收敛）
watch([resolvedPathVariant, variantManuallyPicked], ([resolved, manual]) => {
  if (!manual && resolved !== activeVariant.value) {
    activeVariant.value = resolved as string;
  }
});

const handleVariantChange = async (val: string): Promise<void> => {
  if (val === activeVariant.value) return;
  activeVariant.value = val;
  variantManuallyPicked.value = true;
  if (val === '') return;
  // 变体自动寻址（preview-url-resolver §5.3）：请求解析到该变体的实体并导航（导航复位手动覆盖，
  // 切换器回到随页——到达页即所选变体，二者一致）；零候选保留人工导航提示（手动覆盖存续，
  // 可先行编辑该变体的设置）
  const candidate = await firstCandidate({ variant: val });
  if (candidate != null) {
    addressedCandidate.value = candidate;
    pagePath.value = candidate.url;
    await buildSrc();
  } else {
    ElMessage.info(t('builder.variant.previewHint'));
  }
};

// ---- 视口切换（P5 §10）：约束画布 iframe 宽度（§1 无逐断点编辑）；并映射设备类别下发桥
// （design-review P2-5——预设切换即模拟按设备显示：mobile 预设隐藏仅桌面面板，反之亦然。
// tablet 归桌面：768px 恰在 md 断点上） --------------------------------
const viewport = ref<Viewport>('desktop');
watch(viewport, (value) => {
  bridge.setDevice(value === 'mobile' ? 'mobile' : 'desktop');
});

// 保存：三范围全量回写（后端为全量替换语义，与 ThemeList.vue 一致）；成功后整页软刷新
const handleSaveSettings = async (): Promise<void> => {
  const data = settingsData.value;
  if (data == null) return;
  settingsSaving.value = true;
  try {
    const siteId = site.value?.id;
    const result = await saveThemeSettings(siteId, site.value?.theme ?? '', data.values, data.pageValues, data.variantValues);
    // 未声明键弃警不拒（H5）：清单结构漂移时列明被忽略的设置键
    if (result.dropped?.length) {
      ElMessage.warning(t('theme.settings.droppedWarning', { keys: result.dropped.join(t('punct.enum')) }));
    }
    ElMessage.success(t('success'));
    bridge.refresh('page');
  } catch {
    // 全局错误处理已提示
  } finally {
    settingsSaving.value = false;
  }
};

// ---- 发布 / 丢弃（多区域：确认对话框列出有草稿的区域，§10） --------------------------------------

// 多区域批量（发布/丢弃）：逐区域顺序执行、互不阻断，汇总失败区域点名提示（单区域失败的详细错误
// 由全局错误处理弹出，这里只汇总）
const runRegionBatch = async (names: string[], op: (draft: RegionDraftState) => Promise<boolean>): Promise<string[]> => {
  const failed: string[] = [];
  for (const name of names) {
    const draft = drafts.value[name];
    if (draft == null || !(await op(draft))) failed.push(name);
  }
  return failed;
};

const reportBatch = (kind: 'publish' | 'discard', names: string[], failed: string[]): void => {
  if (failed.length === 0) {
    ElMessage.success(t(kind === 'publish' ? 'builder.publish.done' : 'builder.discard.done', { count: names.length }));
    return;
  }
  ElMessage.warning({
    message: t(kind === 'publish' ? 'builder.publish.partial' : 'builder.discard.partial', { done: names.length - failed.length, failed: regionNames(failed) }),
    duration: 6000,
  });
};

const handlePublishAll = async (): Promise<void> => {
  const names = draftRegionNames.value;
  if (names.length <= 0) return;
  try {
    await ElMessageBox.confirm(t('builder.publish.confirm', { regions: regionNames(names) }), t('region.confirm.title'), { type: 'warning' });
  } catch {
    return; // 取消
  }
  const failed = await runRegionBatch(names, (draft) => draft.publish());
  reportBatch('publish', names, failed);
  bridge.refresh('page');
};

const handleDiscardAll = async (): Promise<void> => {
  const names = draftRegionNames.value;
  if (names.length <= 0) return;
  try {
    await ElMessageBox.confirm(t('builder.discard.confirm', { regions: regionNames(names) }), t('region.confirm.title'), { type: 'warning' });
  } catch {
    return; // 取消
  }
  const failed = await runRegionBatch(names, (draft) => draft.discard());
  reportBatch('discard', names, failed);
  selection.value = null;
  bridge.refresh('page');
};

// ---- 发布历史（region-history §9）：按区域的快照列表 + 恢复 --------------------------------------

const historyVisible = ref<boolean>(false);

/** 恢复成功：面板与ID整批更换，整体重载（同范围切换路径）并刷新画布。 */
const handleHistoryRestored = async (): Promise<void> => {
  selection.value = null;
  await loadRegions();
  bridge.refresh('page');
};

const handleBack = (): void => {
  router.push({ name: 'PageList' });
};

// ---- 页面切换器（顶栏）：免回 设计 → 页面 直达其它页面的构建器 -----------------------------------

// 候选与 PageList 同源同门槛（页面目录 ∩ 已声明区域的页面）；首次展开时装载，失败下次重试
const pageOptions = ref<{ name: string; label: string }[]>([]);
let pageOptionsLoaded = false;
const fetchPageOptions = async (): Promise<void> => {
  if (pageOptionsLoaded) return;
  pageOptionsLoaded = true;
  try {
    const [pages, regionList] = await Promise.all([queryCompositionPageList(), queryRegionList()]);
    const withRegions = new Set((regionList ?? []).map((item: any) => item.page));
    pageOptions.value = (pages ?? []).filter((row: any) => withRegions.has(row.name)).map((row: any) => ({ name: row.name, label: localizedLabel(row.label, row.name) }));
    (pages ?? []).forEach((row: any) => rememberPageLabel(row.name, row.label));
  } catch {
    pageOptionsLoaded = false;
  }
};

// 同名路由仅换 query 不触发 onBeforeRouteLeave，脏草稿确认须在此自行执行；
// 导航后 App.vue 的 fullPath 键控使构建器整体重挂载（预览路径/变体/范围随新页重置）
const handleSwitchPage = async (name: string): Promise<void> => {
  if (name === page || !(await confirmDirtyLeave())) return;
  router.push({ name: 'Builder', query: { page: name } });
};

// 空态引导（preview-url-resolver §6）：无候选内容时跳转 设计 → 主题（演示数据导入在主题详情）；
// 离开前的脏草稿确认由 onBeforeRouteLeave 兜底
const handleSeedData = (): void => {
  router.push({ name: 'ThemeGallery' });
};

// 检查器内联面板设置的未保存修改（离开守卫与区域脏态同口径）
const inspectorDirty = ref<boolean>(false);

const confirmDirtyLeave = async (): Promise<boolean> => {
  const dirtyNames = regionOrder.value.filter((name) => drafts.value[name]?.dirty);
  if (dirtyNames.length <= 0 && !inspectorDirty.value) return true;
  try {
    await ElMessageBox.confirm(t('region.leave.confirm'), t('region.confirm.title'), { type: 'warning' });
    return true;
  } catch {
    return false;
  }
};

onBeforeRouteLeave(confirmDirtyLeave);
</script>

<template>
  <div v-loading="loading" class="builder flex h-screen flex-col bg-gray-100">
    <builder-top-bar
      :page-label="pageLabel"
      :page-options="pageOptions"
      :page-path="pagePath"
      :path-options="pathOptions"
      :path-loading="pathLoading"
      :path-empty-reason="pathEmptyReason"
      :variant="activeVariant"
      :variant-options="variantOptions"
      :page="page"
      :scope-state="scopeState"
      :scope-channel-name="scopeChannelName"
      :scope-customizable="currentChannelScope != null"
      :viewport="viewport"
      :structure-mode="structureMode"
      :draft-regions="draftRegionNames"
      :region-labels="regionLabels"
      :saving="anySaving"
      :can-undo="canUndo"
      :can-redo="canRedo"
      @update:page-path="(val: string) => (pagePath = val)"
      @fetch-paths="(q: string) => fetchPathOptions(q)"
      @seed-data="handleSeedData"
      @update:variant="handleVariantChange"
      @update:viewport="(val: Viewport) => (viewport = val)"
      @customize-scope="handleCustomizeScope"
      @revert-scope="handleRevertScope"
      @update:structure-mode="(val: boolean) => (structureMode = val)"
      @back="handleBack"
      @fetch-pages="fetchPageOptions"
      @switch-page="handleSwitchPage"
      @render="handleRender"
      @open-in-new-tab="openInNewTab"
      @publish-all="handlePublishAll"
      @discard-all="handleDiscardAll"
      @history="() => (historyVisible = true)"
      @theme-settings="() => openSettings('theme')"
      @page-settings="() => openSettings('page')"
      @undo="handleUndo"
      @redo="handleRedo"
    />
    <div class="flex min-h-0 flex-1">
      <!-- 组件调色板（P3 §7.1/§7.2；P7/D12 双模式常显）：画布态 = 点击放置 + 幽灵拖拽；
           结构态 = SortableJS 克隆源拖入结构视图（§7.4 校验挂源侧 :move） -->
      <palette-sidebar
        class="border-r"
        :items="paletteItems"
        :placing-shape="placing?.shape ?? null"
        :disabled="perm('pageRegion:update')"
        :mode="structureMode ? 'structure' : 'canvas'"
        :structure-move="handlePaletteStructureMove"
        @pick="handlePalettePick"
        @drag-start="handlePaletteDragStart"
      />
      <div class="relative min-w-0 flex-1">
        <!-- v-show 保活 iframe：画布 ↔ 结构视图切换无损（共享草稿模型，D7） -->
        <canvas-stage
          v-show="!structureMode"
          ref="stageRef"
          :src="iframeSrc"
          :loading="stageLoading"
          :degraded="bridgeTimedOut && !structureMode"
          :dragging="canvasDragging"
          :ghost="canvasGhost"
          :viewport="viewport"
          @structure="() => (structureMode = true)"
          @retry="handleRender"
        />
        <div v-show="structureMode" class="h-full overflow-auto p-4">
          <structure-view
            :drafts="drafts"
            :region-order="regionOrder"
            :row-layouts="rowLayouts"
            :shape-label-map="shapeLabelMap"
            :commit="commitRegion"
            :selection="selection"
            :active="structureMode"
            @select="(sel: BridgeSelection | null) => (selection = sel)"
            @edit-panel="openPanelForm"
            @placed="handleStructurePlaced"
            @changed="handleStructureChanged"
          />
        </div>
      </div>
      <inspector-sidebar
        class="border-l"
        :selection="selection"
        :draft="selectedDraft"
        :drafts="drafts"
        :region-order="regionOrder"
        :row-layouts="rowLayouts"
        :shape-label-map="shapeLabelMap"
        :shapes="paletteItems"
        :settings-scope="settingsScope"
        :settings="settingsData"
        :settings-loading="settingsLoading"
        :settings-saving="settingsSaving"
        :page="page"
        :variant="activeVariant"
        :settings-focus="settingsFocus"
        :share-states="shareStates"
        :panel-saving="panelSaving"
        @hover="handleHover"
        @fork-region="handleForkRegion"
        @unfork-region="handleUnforkRegion"
        @edit-panel="openPanelForm"
        @save-panel="handleInlinePanelSave"
        @dirty-change="(val: boolean) => (inspectorDirty = val)"
        @select="(sel: BridgeSelection | null) => (selection = sel)"
        @select-row="handleSelectRow"
        @clear-selection="() => (selection = null)"
        @row-layout-change="handleRowLayoutChange"
        @delete-panel="handleDeletePanel"
        @duplicate-panel="handleDuplicatePanel"
        @delete-row="handleDeleteRow"
        @add-row="handleAddRow"
        @select-region="(name: string) => (selection = { kind: 'region', id: name, region: name })"
        @save-settings="handleSaveSettings"
        @close-settings="() => (settingsScope = null)"
        @open-theme-settings="() => openSettings('theme')"
        @set-global-value="handleSetGlobalValue"
        @set-page-value="handleSetPageValue"
        @set-variant-value="handleSetVariantValue"
      />
    </div>

    <panel-form
      v-if="editingPanel != null"
      v-model="panelFormVisible"
      :panel="editingPanel"
      :shape-label="shapeLabelMap[editingPanel.shape] ?? editingPanel.shape"
      @finished="handlePanelSaved"
    />

    <!-- 发布历史（region-history §9）：快照列表 + 恢复；恢复成功后整体重载 -->
    <history-drawer
      v-model="historyVisible"
      :page="page"
      :scope="scope"
      :region-order="regionOrder"
      :drafts="drafts"
      :initial-region="selection?.region ?? null"
      @restored="handleHistoryRestored"
    />

    <!-- 内容深链（§8.3 P5，D5）：打开既有表单（对话框宿主，常驻挂载——DialogForm 以 visible 变化
         触发装载，v-if 延迟挂载会错过首次打开），保存后整页刷新——绝不内置内容表单 -->
    <article-form
      v-model="articleFormVisible"
      :bean-id="quickEditArticleId"
      :bean-ids="quickEditArticleId != null ? [quickEditArticleId] : []"
      @finished="() => bridge.refresh('page')"
    />
    <channel-form
      v-model="channelFormVisible"
      :bean-id="quickEditChannelId"
      :bean-ids="quickEditChannelId != null ? [quickEditChannelId] : []"
      @finished="() => bridge.refresh('page')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { Link, Refresh, Search, SwitchButton } from '@element-plus/icons-vue';
import QRCode from 'qrcode';
import { isCommercialLocked, isShowPerm, perm } from '@/stores/useCurrentUser';
import { notifySeedFailures, seedDiffTotal, seedPlanSummary } from './themeUi';
import MarketplaceImage from './MarketplaceImage.vue';
import { createThemeSandboxFromStore, type ActivateTarget, type ThemeInfo, type ThemeInstallReport, type ThemeUpdateReport, type SeedScope } from '@/api/theme';
import {
  queryMarketplaceItems,
  queryMarketplaceItem,
  queryMarketplaceItemVersions,
  installMarketplaceItem,
  updateMarketplaceItem,
  queryMarketplaceConnection,
  startMarketplaceConnect,
  pollMarketplaceConnect,
  disconnectMarketplace,
  queryMarketplaceEntitlements,
  marketplaceUpdateCheck,
  type MarketItem,
  type MarketItemDetail,
  type MarketItemVersion,
  type MarketConnection,
  type MarketDeviceStart,
  type MarketUpdateCheckResult,
  type StoreResult,
} from '@/api/marketplace';

// 「应用市场」页签（remote-marketplace C1/C2）：远程浏览网格 + 详情抽屉 + 安装/更新 + 账号连接。
// 目录读取走可用性信封（D-C1-3）——商店不可达呈现离线提示而非报错；安装复用与上传安装
// 相同的确认要素（seedScope 三态 / 激活开关）与安装后 UX（SHA-256 通知、种子失败明细）。
// C2：设备授权流连接账号（令牌只存服务端，D-K3/D-C1-1）；付费条目按授权塑形（购买深链 D-K5，
// 已购解锁安装）；更新检查角标 + 市场更新（落到既有更新管线，origin_hash_ 报告原样）。
defineOptions({
  name: 'MarketplaceGallery',
});

const props = defineProps<{
  siteId?: string | number;
  /** 已装主题（安装态识别：slug 对 themeName，版本对 metadata.version） */
  themes: ThemeInfo[];
  /** 后端运行版本（D-C1-6，兼容角标；仅提示，权威门槛在安装器） */
  backendVersion?: string;
  /** 店面地址（购买深链基址 `{storefrontUrl}/items/{slug}`，D-K5） */
  storefrontUrl?: string;
  /** 沙箱面站点一致性门（S2 同口径：画廊选中站点 == 当前站点上下文时才展示试装到沙箱） */
  sandboxAllowed?: boolean;
}>();

const emit = defineEmits<{ installed: []; sandboxCreated: [] }>();

const { t } = useI18n();

// ----- 浏览网格 -----
const items = ref<MarketItem[]>([]);
const total = ref<number>(0);
const loading = ref<boolean>(false);
const unavailableReason = ref<string>();
const retryAfterSeconds = ref<number>();
const keyword = ref<string>('');
const kindFilter = ref<string>('');

const fetchItems = async () => {
  loading.value = true;
  try {
    const result = await queryMarketplaceItems({
      q: keyword.value.trim() || undefined,
      kind: kindFilter.value || undefined,
      pageSize: 100,
    });
    if (!result.available) {
      unavailableReason.value = result.reason ?? 'offline';
      retryAfterSeconds.value = result.retryAfterSeconds;
      items.value = [];
      total.value = 0;
      return;
    }
    unavailableReason.value = undefined;
    retryAfterSeconds.value = undefined;
    items.value = result.data?.items ?? [];
    total.value = result.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

watch(kindFilter, fetchItems);

// ----- 安装态识别（kickoff C1 §4：已装主题按 名称+版本 匹配） -----
const installedVersion = (item: MarketItem): string | undefined => props.themes.find((theme) => theme.themeName === item.slug)?.version ?? undefined;
const isInstalled = (item: MarketItem): boolean => props.themes.some((theme) => theme.themeName === item.slug);
const isInstalledLatest = (item: MarketItem): boolean => item.latestVersion?.version != null && installedVersion(item) === item.latestVersion.version;

// ----- 兼容角标：minUjcmsVersion vs 后端版本（三段语义化版本，宽松——不可解析视为兼容） -----
const parseSemver = (value?: string | null): number[] | undefined => {
  const match = /^(\d+)\.(\d+)\.(\d+)/.exec(value?.trim() ?? '');
  return match ? [Number(match[1]), Number(match[2]), Number(match[3])] : undefined;
};

const isCompatible = (item: MarketItem): boolean => {
  const min = parseSemver(item.latestVersion?.minUjcmsVersion);
  const current = parseSemver(props.backendVersion);
  if (!min || !current) {
    return true;
  }
  for (let i = 0; i < 3; i += 1) {
    const required = min[i] ?? 0;
    const actual = current[i] ?? 0;
    if (required !== actual) {
      return required < actual;
    }
  }
  return true;
};

// ----- 账号连接（C2：设备授权流 RFC 8628；令牌只存服务端，控制台只见状态投影） -----
const connection = ref<MarketConnection>();
const ownedSlugs = ref<Set<string>>(new Set());
const canConnect = computed<boolean>(() => !perm('marketplace:connect'));
const isConnected = computed<boolean>(() => connection.value?.connected === true && !connection.value?.reconnectRequired);
const reconnectRequired = computed<boolean>(() => connection.value?.reconnectRequired === true);

const fetchConnection = async () => {
  try {
    connection.value = await queryMarketplaceConnection();
  } catch {
    connection.value = undefined;
  }
};

const fetchEntitlements = async () => {
  if (connection.value?.connected !== true) {
    ownedSlugs.value = new Set();
    return;
  }
  const result = await queryMarketplaceEntitlements();
  if (!result.available || !result.data) {
    return; // 商店不可达：保留上次已知授权，浏览面不报错（D-C1-3）
  }
  if (result.data.reconnectRequired && connection.value) {
    connection.value = { ...connection.value, reconnectRequired: true };
  }
  ownedSlugs.value = new Set(result.data.entitlements.map((entitlement) => entitlement.item));
};

const owned = (item: MarketItem): boolean => ownedSlugs.value.has(item.slug);
const isPaid = (item: MarketItem): boolean => item.priceCents > 0;

const handleRefreshEntitlements = async () => {
  await fetchEntitlements();
  await runUpdateCheck();
  ElMessage.success(t('success'));
};

// ----- 连接对话框（code + 二维码 + 链接 + 轮询态；后端按服务端 interval 节流真实轮询） -----
const connectVisible = ref<boolean>(false);
const connectStatus = ref<'starting' | 'waiting' | 'connected' | 'denied' | 'expired' | 'offline'>('starting');
const connectStart = ref<MarketDeviceStart>();
const connectAccount = ref<string>();
const qrDataUrl = ref<string>();
let pollTimer: number | undefined;

const stopPolling = () => {
  if (pollTimer != null) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
};

const pollOnce = async () => {
  try {
    const result = await pollMarketplaceConnect();
    if (result.status === 'PENDING') return;
    stopPolling();
    if (result.status === 'CONNECTED') {
      connectStatus.value = 'connected';
      connectAccount.value = result.accountEmail ?? result.accountName ?? '';
      await fetchConnection();
      await fetchEntitlements();
      await runUpdateCheck();
    } else if (result.status === 'DENIED') {
      connectStatus.value = 'denied';
    } else {
      // EXPIRED / NONE（如后端重启丢挂起流）：重新发起
      connectStatus.value = 'expired';
    }
  } catch {
    // CMS 后端瞬时错误：保留轮询，下一轮再试
  }
};

const startConnect = async () => {
  stopPolling();
  connectStatus.value = 'starting';
  connectStart.value = undefined;
  qrDataUrl.value = undefined;
  const result: StoreResult<MarketDeviceStart> = await startMarketplaceConnect();
  if (!result.available || !result.data) {
    connectStatus.value = 'offline';
    return;
  }
  connectStart.value = result.data;
  connectStatus.value = 'waiting';
  try {
    qrDataUrl.value = await QRCode.toDataURL(result.data.verificationUriComplete, { width: 180, margin: 1 });
  } catch {
    qrDataUrl.value = undefined; // 二维码只是便利通道，code + 链接仍可完成批准
  }
  // 控制台每 2 秒问一次 CMS 后端；真正打到市场令牌端点的频率由后端按 interval（含 slow_down +5s）节流
  pollTimer = window.setInterval(pollOnce, 2000);
};

const openConnect = () => {
  connectVisible.value = true;
  startConnect();
};

watch(connectVisible, (visible) => {
  if (!visible) stopPolling();
});
onUnmounted(stopPolling);

const openVerification = () => {
  const url = connectStart.value?.verificationUriComplete ?? connectStart.value?.verificationUri;
  if (url) window.open(url, '_blank');
};

const handleDisconnect = async () => {
  await ElMessageBox.confirm(t('marketplace.connection.disconnectConfirm'), { type: 'warning' });
  await disconnectMarketplace();
  ElMessage.success(t('marketplace.connection.disconnected'));
  ownedSlugs.value = new Set();
  await fetchConnection();
};

// ----- 购买深链（D-K5：购买只在店面网站发生；付费条目未购时引导到店面条目页） -----
const openBuy = (item: MarketItem) => {
  const base = props.storefrontUrl?.replace(/\/$/, '');
  if (!base) return;
  window.open(`${base}/items/${encodeURIComponent(item.slug)}`, '_blank');
};

// ----- 更新检查（C2 画廊角标：送已装 名称/版本 对，服务端按 slug 匹配兼容新版本） -----
const updateResults = ref<Record<string, MarketUpdateCheckResult>>({});

const runUpdateCheck = async () => {
  const entries = props.themes.filter((theme) => theme.version != null && theme.version !== '').map((theme) => ({ name: theme.themeName, version: theme.version as string }));
  if (entries.length === 0) {
    updateResults.value = {};
    return;
  }
  const result = await marketplaceUpdateCheck(entries);
  if (!result.available || !result.data) {
    return; // 商店不可达：保留上次角标，浏览面不报错
  }
  const map: Record<string, MarketUpdateCheckResult> = {};
  result.data.forEach((row) => {
    map[row.name] = row;
  });
  updateResults.value = map;
};

watch(
  () => props.themes,
  () => runUpdateCheck(),
);

const updateFor = (item: MarketItem): MarketUpdateCheckResult | undefined => {
  const result = updateResults.value[item.slug];
  return result?.updateAvailable ? result : undefined;
};

onMounted(async () => {
  await Promise.all([fetchItems(), fetchConnection()]);
  await Promise.all([fetchEntitlements(), runUpdateCheck()]);
});

// ----- 详情抽屉 -----
const detailVisible = ref<boolean>(false);
const detail = ref<MarketItemDetail>();
const detailVersions = ref<MarketItemVersion[]>([]);
const detailLoading = ref<boolean>(false);

const openDetail = async (item: MarketItem) => {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = undefined;
  detailVersions.value = [];
  try {
    const [detailResult, versionsResult]: [StoreResult<MarketItemDetail>, StoreResult<MarketItemVersion[]>] = await Promise.all([
      queryMarketplaceItem(item.slug),
      queryMarketplaceItemVersions(item.slug),
    ]);
    if (!detailResult.available) {
      unavailableReason.value = detailResult.reason ?? 'offline';
      detailVisible.value = false;
      return;
    }
    detail.value = detailResult.data;
    detailVersions.value = versionsResult.available ? (versionsResult.data ?? []) : [];
  } finally {
    detailLoading.value = false;
  }
};

const formatSize = (bytes?: number): string => {
  if (bytes == null) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const formatPrice = (item: MarketItem): string => (item.priceCents > 0 ? t('marketplace.price', { price: (item.priceCents / 100).toFixed(2) }) : t('marketplace.free'));

// ----- 安装对话框（与上传安装同要素：目录名 / seedScope 三态 / 激活开关） -----
const installVisible = ref<boolean>(false);
const installItem = ref<MarketItem>();
const installVersion = ref<string>();
const installForm = ref<{ themeName: string; activate: boolean; activateTarget: ActivateTarget; seedScope: SeedScope }>({
  themeName: '',
  activate: false,
  activateTarget: 'desktop',
  seedScope: 'composition',
});
const installing = ref<boolean>(false);

const openInstall = (item: MarketItem, version?: string) => {
  installItem.value = item;
  installVersion.value = version;
  installForm.value = { themeName: item.slug, activate: false, activateTarget: 'desktop', seedScope: 'composition' };
  installVisible.value = true;
};

const handleInstallConfirm = async () => {
  if (!installItem.value || props.siteId == null) return;
  installing.value = true;
  try {
    const report: ThemeInstallReport = await installMarketplaceItem({
      slug: installItem.value.slug,
      version: installVersion.value,
      siteId: props.siteId,
      themeName: installForm.value.themeName.trim() || installItem.value.slug,
      activate: installForm.value.activate ? installForm.value.activateTarget : undefined,
      seedScope: installForm.value.seedScope,
    });
    if (report.warnings?.length) {
      ElMessage.warning(report.warnings.join('; '));
    }
    // 包 SHA-256：与上传安装同法（P3-1/H3），且市场路径已按下载授权校验过（D-K7）
    if (report.sha256) {
      ElNotification({ title: t('theme.install.sha256'), message: report.sha256, type: 'info', duration: 0 });
    }
    // 种子导入失败明细（§8.1/D22）：延后一拍避免 EP 通知堆叠遮挡（同上传安装）
    setTimeout(() => notifySeedFailures(report.seed, t), 400);
    ElMessage.success(t('success'));
    installVisible.value = false;
    emit('installed');
  } finally {
    installing.value = false;
  }
};

const canInstall = computed<boolean>(() => !perm('marketplace:install') && props.siteId != null);
/** 付费条目须已购买（授权在库）才可安装/更新（D-K6；权威门槛在下载授权，此处仅塑形 UI） */
const purchasable = (item: MarketItem): boolean => !isPaid(item) || owned(item);

// ----- 试装到沙箱（sandbox-preview-site S3 / D12）：建裸沙箱子站点 + 商店包经市场安装管线装入
// 并激活 + 全量种子——当前站点不装任何东西。权限组合 theme:sandbox 与 marketplace:install 兼备
// （D16：市场路径会把新的模板代码引入本机，安装权限不可少）；社区版带商业版标识进介绍页。 -----
const router = useRouter();
const sandboxTryLocked = computed<boolean>(() => isCommercialLocked('theme:sandbox'));
const sandboxTryVisible = computed<boolean>(() => isShowPerm('theme:sandbox') && props.sandboxAllowed === true);
const canSandboxTry = computed<boolean>(() => !perm('theme:sandbox') && !perm('marketplace:install') && props.siteId != null);
const sandboxTrying = ref<Record<string, boolean>>({});

const handleSandboxTry = async (item: MarketItem) => {
  if (sandboxTryLocked.value) {
    router.push({ path: '/commercial-feature', query: { perm: 'theme:sandbox' } });
    return;
  }
  try {
    await ElMessageBox.confirm(t('theme.sandbox.fromStore.confirm', { name: item.name }), t('theme.sandbox.fromStore'), { type: 'info' });
  } catch {
    return;
  }
  sandboxTrying.value = { ...sandboxTrying.value, [item.slug]: true };
  try {
    const result = await createThemeSandboxFromStore({ slug: item.slug });
    if (result.warnings?.length) {
      ElMessage.warning(result.warnings.join('; '));
    }
    // 成功常驻通知带可用「打开沙箱」链接 + 行级种子失败明细延后一拍（S2 复合创建同款）
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
    emit('sandboxCreated');
  } finally {
    const rest = { ...sandboxTrying.value };
    delete rest[item.slug];
    sandboxTrying.value = rest;
  }
};

// ----- 市场更新对话框（C2：落到既有更新管线；seedScope 缺省 none，同上传更新） -----
const marketUpdateVisible = ref<boolean>(false);
const marketUpdateItem = ref<MarketItem>();
const marketUpdateTarget = ref<string>();
const marketUpdateSeedScope = ref<SeedScope>('none');
const marketUpdating = ref<boolean>(false);
const marketUpdateReport = ref<ThemeUpdateReport>();

const openMarketUpdate = (item: MarketItem) => {
  marketUpdateItem.value = item;
  marketUpdateTarget.value = updateFor(item)?.latestVersion?.version;
  marketUpdateSeedScope.value = 'none';
  marketUpdateReport.value = undefined;
  marketUpdateVisible.value = true;
};

const handleMarketUpdateConfirm = async () => {
  if (!marketUpdateItem.value || props.siteId == null) return;
  marketUpdating.value = true;
  try {
    const report = await updateMarketplaceItem({
      slug: marketUpdateItem.value.slug,
      version: marketUpdateTarget.value,
      siteId: props.siteId,
      themeName: marketUpdateItem.value.slug,
      seedScope: marketUpdateSeedScope.value,
    });
    marketUpdateReport.value = report;
    // 随更新执行的种子导入失败明细（§8.1/D22）
    notifySeedFailures(report.seed, t);
    ElMessage.success(t('success'));
    emit('installed');
  } finally {
    marketUpdating.value = false;
  }
};
</script>

<template>
  <div>
    <!-- 连接状态条（C2）：账号芯片 / 重连提示 / 连接入口；连接与浏览、安装分权（marketplace:connect） -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <template v-if="reconnectRequired">
        <el-tag type="warning" size="small">{{ $t('marketplace.connection.reconnectRequired') }}</el-tag>
        <el-button type="warning" size="small" :disabled="!canConnect" @click="openConnect">{{ $t('marketplace.op.reconnect') }}</el-button>
        <el-button size="small" :disabled="!canConnect" @click="handleDisconnect">{{ $t('marketplace.op.disconnect') }}</el-button>
      </template>
      <template v-else-if="isConnected">
        <el-tooltip :content="connection?.instanceName ?? ''" :disabled="!connection?.instanceName">
          <el-tag type="success" size="small">
            {{ $t('marketplace.connection.connected', { account: connection?.accountEmail ?? connection?.accountName ?? '' }) }}
          </el-tag>
        </el-tooltip>
        <el-button size="small" :icon="Refresh" @click="handleRefreshEntitlements">{{ $t('marketplace.op.refreshEntitlements') }}</el-button>
        <el-button size="small" :icon="SwitchButton" :disabled="!canConnect" @click="handleDisconnect">{{ $t('marketplace.op.disconnect') }}</el-button>
      </template>
      <template v-else>
        <el-button type="primary" size="small" :icon="Link" :disabled="!canConnect" @click="openConnect">{{ $t('marketplace.op.connect') }}</el-button>
        <span class="text-xs text-gray-500">{{ $t('marketplace.connection.connectHint') }}</span>
      </template>
    </div>

    <div class="mb-3 flex items-center gap-2">
      <el-input
        v-model="keyword"
        :placeholder="$t('marketplace.search.placeholder')"
        :prefix-icon="Search"
        clearable
        style="width: 260px"
        @keyup.enter="fetchItems"
        @clear="fetchItems"
      />
      <el-select v-model="kindFilter" style="width: 160px">
        <el-option value="" :label="$t('marketplace.kind.all')" />
        <el-option value="full" :label="$t('marketplace.kind.full')" />
        <el-option value="data-only" :label="$t('marketplace.kind.dataOnly')" />
      </el-select>
      <el-button :icon="Refresh" @click="() => (fetchItems(), runUpdateCheck())">{{ $t('refresh') }}</el-button>
      <el-tag v-if="!unavailableReason" class="ml-auto" type="info" size="small">
        {{ $t('marketplace.total', { total }) }}
      </el-tag>
    </div>

    <!-- 商店不可达 → 离线提示而非报错（D-C1-3） -->
    <el-empty v-if="unavailableReason" :description="$t(`marketplace.unavailable.${unavailableReason}`)">
      <div v-if="retryAfterSeconds != null" class="mb-2 text-xs text-gray-500">
        {{ $t('marketplace.retryAfter', { seconds: retryAfterSeconds }) }}
      </div>
      <el-button type="primary" @click="fetchItems">{{ $t('marketplace.op.retry') }}</el-button>
    </el-empty>

    <div v-else v-loading="loading" class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      <el-card v-for="item in items" :key="item.slug" body-class="!p-0" shadow="hover">
        <!-- 预览图容器统一 16:10（与本地画廊/详情页同一比例约定） -->
        <div class="relative aspect-[16/10] cursor-pointer overflow-hidden bg-gray-100" @click="() => openDetail(item)">
          <marketplace-image :path="item.previewUrl" />
          <div class="absolute right-2 top-2 flex gap-1">
            <el-tag v-if="updateFor(item)" type="warning" size="small">
              {{ $t('marketplace.updateAvailable', { version: updateFor(item)?.latestVersion?.version }) }}
            </el-tag>
            <el-tag v-if="isInstalled(item)" type="success" size="small">
              {{ isInstalledLatest(item) ? $t('marketplace.installed') : $t('marketplace.installedVersion', { version: installedVersion(item) }) }}
            </el-tag>
          </div>
        </div>
        <div class="p-3">
          <div class="flex items-center gap-2">
            <el-link type="primary" underline="never" class="!text-base !font-medium" @click="() => openDetail(item)">
              {{ item.name }}
            </el-link>
            <span class="text-xs text-gray-400">{{ item.slug }}</span>
            <span v-if="item.latestVersion" class="ml-auto text-xs text-gray-500">v{{ item.latestVersion.version }}</span>
          </div>
          <div class="mt-2 flex flex-wrap items-center gap-1">
            <el-tag :type="item.priceCents > 0 ? 'warning' : 'success'" size="small">{{ formatPrice(item) }}</el-tag>
            <el-tag v-if="isPaid(item) && owned(item)" type="success" size="small" effect="plain">{{ $t('marketplace.owned') }}</el-tag>
            <el-tag v-if="item.kind === 'data-only'" type="warning" size="small">{{ $t('marketplace.kind.dataOnly') }}</el-tag>
            <el-tag v-if="!isCompatible(item)" type="danger" size="small">
              {{ $t('marketplace.compat.incompatible', { version: item.latestVersion?.minUjcmsVersion }) }}
            </el-tag>
            <el-tag v-for="tag in item.tags ?? []" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
          <div v-if="item.summary" class="mt-2 line-clamp-2 text-xs text-gray-500">{{ item.summary }}</div>
          <div class="mt-3 flex flex-wrap items-center gap-x-1">
            <el-button type="primary" link size="small" @click="() => openDetail(item)">{{ $t('marketplace.op.detail') }}</el-button>
            <!-- 付费未购：购买深链（D-K5，新标签页开店面条目页；购买后「刷新授权」解锁安装） -->
            <el-button v-if="isPaid(item) && !owned(item)" type="warning" link size="small" @click="() => openBuy(item)">
              {{ $t('marketplace.op.buy') }}
            </el-button>
            <!-- 已装且有兼容新版本：更新（落到既有更新管线，origin_hash_ 报告原样） -->
            <el-button
              v-if="isInstalled(item) && updateFor(item)"
              type="warning"
              link
              size="small"
              :disabled="!canInstall || !purchasable(item)"
              @click="() => openMarketUpdate(item)"
            >
              {{ $t('marketplace.op.update') }}
            </el-button>
            <el-button v-else type="primary" link size="small" :disabled="!canInstall || !purchasable(item) || isInstalled(item)" @click="() => openInstall(item)">
              {{ $t('marketplace.op.install') }}
            </el-button>
            <!-- 试装到沙箱（S3 / D12）：纯数据包不可激活故无此动作；付费未购同安装口径禁用 -->
            <el-button
              v-if="sandboxTryVisible && item.kind !== 'data-only'"
              type="primary"
              link
              size="small"
              :loading="sandboxTrying[item.slug] === true"
              :disabled="!sandboxTryLocked && (!canSandboxTry || !purchasable(item))"
              @click="() => handleSandboxTry(item)"
            >
              {{ $t('theme.sandbox.fromStore') }}
              <commercial-badge v-if="sandboxTryLocked" permission="theme:sandbox" class="ml-1" />
            </el-button>
          </div>
        </div>
      </el-card>
      <el-empty v-if="!loading && items.length <= 0" class="col-span-full" :description="$t('marketplace.empty')" />
    </div>

    <!-- 详情抽屉：描述 / 截图 / 版本历史（changelog、sha256、大小） -->
    <el-drawer v-model="detailVisible" :title="detail?.name ?? ''" size="560">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="mb-3 flex flex-wrap items-center gap-1">
            <el-tag :type="detail.priceCents > 0 ? 'warning' : 'success'" size="small">{{ formatPrice(detail) }}</el-tag>
            <el-tag v-if="isPaid(detail) && owned(detail)" type="success" size="small" effect="plain">{{ $t('marketplace.owned') }}</el-tag>
            <el-tag v-if="detail.kind === 'data-only'" type="warning" size="small">{{ $t('marketplace.kind.dataOnly') }}</el-tag>
            <el-tag v-for="tag in detail.tags ?? []" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
          <div class="mb-3 h-56 overflow-hidden rounded bg-gray-100">
            <marketplace-image :path="detail.previewUrl" />
          </div>
          <div v-if="detail.description" class="mb-4 whitespace-pre-wrap text-sm text-gray-700">{{ detail.description }}</div>

          <template v-if="detail.screenshots?.length">
            <div class="mb-2 font-medium">{{ $t('marketplace.detail.screenshots') }}</div>
            <div class="mb-4 grid grid-cols-2 gap-2">
              <div v-for="shot in detail.screenshots" :key="shot.url" class="h-32 overflow-hidden rounded bg-gray-100">
                <marketplace-image :path="shot.url" />
              </div>
            </div>
          </template>

          <div class="mb-2 font-medium">{{ $t('marketplace.detail.versions') }}</div>
          <el-table :data="detailVersions" size="small" border>
            <el-table-column prop="version" :label="$t('marketplace.detail.version')" width="90" />
            <el-table-column prop="minUjcmsVersion" :label="$t('marketplace.detail.minUjcmsVersion')" width="110" />
            <el-table-column :label="$t('marketplace.detail.size')" width="90">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column :label="$t('marketplace.detail.changelog')">
              <template #default="{ row }">
                <span class="whitespace-pre-wrap text-xs">{{ row.changelog ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column width="80">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  :disabled="!canInstall || !purchasable(detail!) || isInstalled(detail!)"
                  @click="() => openInstall(detail!, row.version)"
                >
                  {{ $t('marketplace.op.install') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('cancel') }}</el-button>
        <el-button v-if="detail && isPaid(detail) && !owned(detail)" type="warning" @click="() => openBuy(detail!)">
          {{ $t('marketplace.op.buy') }}
        </el-button>
        <el-button v-if="detail" type="primary" :disabled="!canInstall || !purchasable(detail!) || isInstalled(detail!)" @click="() => openInstall(detail!)">
          {{ $t('marketplace.op.install') }}
        </el-button>
      </template>
    </el-drawer>

    <!-- 安装对话框：与上传安装同要素（seedScope 三态 §6.3 / 激活开关），确认后服务端下载+校验+安装 -->
    <el-dialog v-model="installVisible" :title="$t('marketplace.install.title')" width="540" :close-on-click-modal="false">
      <el-form label-width="120">
        <el-form-item :label="$t('marketplace.install.item')">
          <span>{{ installItem?.name }}</span>
          <span class="ml-2 text-xs text-gray-400">{{ installItem?.slug }}</span>
        </el-form-item>
        <el-form-item :label="$t('marketplace.install.version')">
          <span>{{ installVersion ?? $t('marketplace.install.latest') }}</span>
        </el-form-item>
        <el-form-item :label="$t('theme.themeName')" required>
          <el-input v-model="installForm.themeName" maxlength="50" />
          <div class="text-xs text-gray-500">{{ $t('marketplace.themeName.tip') }}</div>
        </el-form-item>
        <el-form-item :label="$t('theme.seed.import')">
          <el-radio-group v-model="installForm.seedScope">
            <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
            <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
            <el-radio value="none">{{ $t('theme.seed.scope.none') }}</el-radio>
          </el-radio-group>
          <div class="text-xs text-gray-500">{{ $t('theme.seed.import.tip') }}</div>
        </el-form-item>
        <el-form-item v-if="installItem?.kind !== 'data-only'" :label="$t('theme.install.activate')">
          <el-switch v-model="installForm.activate" />
        </el-form-item>
        <el-form-item v-if="installItem?.kind !== 'data-only' && installForm.activate" :label="$t('theme.activate.target')">
          <el-radio-group v-model="installForm.activateTarget">
            <el-radio value="desktop">{{ $t('theme.activate.target.desktop') }}</el-radio>
            <el-radio value="mobile">{{ $t('theme.activate.target.mobile') }}</el-radio>
            <el-radio value="both">{{ $t('theme.activate.target.both') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="installVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="installing" :disabled="!installForm.themeName.trim()" @click="handleInstallConfirm">
          {{ $t('marketplace.op.install') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 市场更新对话框（C2）：当前版本 → 目标版本 + seedScope；报告与上传更新同形（种子三态差异 + SHA-256） -->
    <el-dialog v-model="marketUpdateVisible" :title="$t('marketplace.update.title')" width="540" :close-on-click-modal="false">
      <el-form label-width="120">
        <el-form-item :label="$t('marketplace.install.item')">
          <span>{{ marketUpdateItem?.name }}</span>
          <span class="ml-2 text-xs text-gray-400">{{ marketUpdateItem?.slug }}</span>
        </el-form-item>
        <el-form-item :label="$t('marketplace.update.installed')">
          <span>{{ marketUpdateItem ? installedVersion(marketUpdateItem) : '' }}</span>
        </el-form-item>
        <el-form-item :label="$t('marketplace.update.target')">
          <span>v{{ marketUpdateTarget }}</span>
        </el-form-item>
        <el-form-item :label="$t('theme.seed.import')">
          <el-radio-group v-model="marketUpdateSeedScope">
            <el-radio value="all">{{ $t('theme.seed.scope.all') }}</el-radio>
            <el-radio value="composition">{{ $t('theme.seed.scope.composition') }}</el-radio>
            <el-radio value="none">{{ $t('theme.seed.scope.none') }}</el-radio>
          </el-radio-group>
          <div class="text-xs text-gray-500">{{ $t('theme.update.seedScope.tip') }}</div>
        </el-form-item>
      </el-form>
      <template v-if="marketUpdateReport">
        <el-descriptions :column="3" border size="small" class="mt-2">
          <el-descriptions-item :label="$t('theme.update.added')">{{ seedDiffTotal(marketUpdateReport.seedDiff, 'new') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('theme.update.seedChanged')">{{ seedDiffTotal(marketUpdateReport.seedDiff, 'seedChanged') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('theme.update.operatorModified')">{{ seedDiffTotal(marketUpdateReport.seedDiff, 'operatorModified') }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="seedPlanSummary(marketUpdateReport.seedDiff, t)" class="text-xs text-gray-500 mt-1">
          {{ seedPlanSummary(marketUpdateReport.seedDiff, t) }}
        </div>
        <!-- 包 SHA-256：市场路径已按下载授权校验（D-K7），展示供带外比对 -->
        <div v-if="marketUpdateReport.sha256" class="text-xs text-gray-500 mt-1 break-all">SHA-256: {{ marketUpdateReport.sha256 }}</div>
      </template>
      <template #footer>
        <el-button @click="marketUpdateVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="marketUpdating" @click="handleMarketUpdateConfirm">
          {{ $t('marketplace.op.update') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 连接对话框（C2 / D-K4）：user_code + 二维码 + 批准页链接 + 轮询态；device_code 永不出服务端 -->
    <el-dialog v-model="connectVisible" :title="$t('marketplace.connect.title')" width="440" :close-on-click-modal="false">
      <div v-if="connectStatus === 'starting'" v-loading="true" class="h-40" />
      <el-empty v-else-if="connectStatus === 'offline'" :description="$t('marketplace.unavailable.offline')">
        <el-button type="primary" @click="startConnect">{{ $t('marketplace.op.retry') }}</el-button>
      </el-empty>
      <template v-else-if="connectStatus === 'waiting'">
        <div class="text-center">
          <div class="text-xs text-gray-500">{{ $t('marketplace.connect.userCode') }}</div>
          <div class="mt-1 select-all font-mono text-3xl font-semibold tracking-widest">{{ connectStart?.userCode }}</div>
          <div class="mt-3">
            <el-button type="primary" :icon="Link" @click="openVerification">{{ $t('marketplace.connect.openLink') }}</el-button>
          </div>
          <template v-if="qrDataUrl">
            <div class="mt-3 text-xs text-gray-500">{{ $t('marketplace.connect.scanQr') }}</div>
            <img :src="qrDataUrl" alt="QR" class="mx-auto mt-1 h-44 w-44" />
          </template>
          <div class="mt-3 flex items-center justify-center gap-2 text-sm text-gray-500">
            <el-icon class="is-loading"><Refresh /></el-icon>
            {{ $t('marketplace.connect.waiting') }}
          </div>
        </div>
      </template>
      <el-result v-else-if="connectStatus === 'connected'" icon="success" :title="$t('marketplace.connect.connected', { account: connectAccount })" />
      <el-result v-else-if="connectStatus === 'denied'" icon="error" :title="$t('marketplace.connect.denied')">
        <template #extra>
          <el-button type="primary" @click="startConnect">{{ $t('marketplace.connect.restart') }}</el-button>
        </template>
      </el-result>
      <el-result v-else icon="warning" :title="$t('marketplace.connect.expired')">
        <template #extra>
          <el-button type="primary" @click="startConnect">{{ $t('marketplace.connect.restart') }}</el-button>
        </template>
      </el-result>
      <template #footer>
        <el-button @click="connectVisible = false">{{ connectStatus === 'connected' ? $t('ok') : $t('cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

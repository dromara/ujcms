import axios from '@/utils/request';
import type { ActivateTarget, ThemeInstallReport, ThemeUpdateReport, SeedScope } from '@/api/theme';

/**
 * 应用市场 admin API（remote-marketplace C1/C2）。
 *
 * Backend mapping: `com.ujcms.cms.core.web.backendapi.MarketplaceController`。
 * 控制台唯一的商店通道（D-K3）：目录浏览走后端代理，安装/更新在服务端完成（下载 + sha256 校验 +
 * 既有安装管线）；图片经带鉴权的资产代理以 blob 加载（D-C1-4）；账号连接走设备授权流（D-K4），
 * 令牌只存服务端（加密落库，D-C1-1）。
 * 权限：`marketplace:browse` / `marketplace:install` / `marketplace:connect`（`*` 均可越过）。
 */

export interface MarketplaceState {
  enabled: boolean;
  /** 后端运行版本（兼容角标数据源，D-C1-6；仅提示——权威门槛在安装器） */
  backendVersion?: string;
  /** 店面站点地址（购买深链基址 `{storefrontUrl}/items/{slug}`，D-K5） */
  storefrontUrl?: string;
}

export interface MarketItemVersionSummary {
  version: string;
  minUjcmsVersion?: string | null;
  publishedAt?: string;
}

export interface MarketItemVersion extends MarketItemVersionSummary {
  changelog?: string | null;
  sha256: string;
  size: number;
}

export interface MarketScreenshot {
  /** 不透明相对引用——原样传给资产代理的 path 参数，绝不自行拼接（D-M3-3） */
  url: string;
  sort?: number;
}

export interface MarketItem {
  slug: string;
  type: string;
  /** full | data-only */
  kind: string;
  name: string;
  summary?: string;
  tags?: string[];
  /** 0 = 免费（免费条目无需购买即可安装，D-K6） */
  priceCents: number;
  previewUrl?: string | null;
  latestVersion?: MarketItemVersionSummary;
  publishedAt?: string;
  updatedAt?: string;
}

export interface MarketItemDetail extends MarketItem {
  /** Markdown 原文（C1 以纯文本呈现） */
  description?: string;
  screenshots?: MarketScreenshot[];
  versions?: MarketItemVersionSummary[];
}

export interface MarketItemPage {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  items: MarketItem[];
}

/** 目录读取的可用性信封（D-C1-3）：商店不可达/限流不报错，控制台按 reason 呈现离线提示 */
export interface StoreResult<T> {
  available: boolean;
  reason?: 'offline' | 'rate-limited' | 'disabled';
  retryAfterSeconds?: number;
  data?: T;
}

export interface MarketItemQuery {
  q?: string;
  kind?: string;
  tag?: string;
  sort?: 'newest' | 'popular' | 'name';
  page?: number;
  pageSize?: number;
}

export interface MarketInstallParams {
  slug: string;
  version?: string;
  siteId: string | number;
  /** 安装到站点的主题目录名，缺省取 slug */
  themeName?: string;
  /** 装后激活到的槽位（desktop | mobile | both）；缺省只安装 */
  activate?: ActivateTarget;
  seedScope?: SeedScope;
}

export const queryMarketplaceState = async (): Promise<MarketplaceState> => (await axios.get('/backend/core/marketplace/state')).data;

export const queryMarketplaceItems = async (params?: MarketItemQuery): Promise<StoreResult<MarketItemPage>> =>
  (await axios.get('/backend/core/marketplace/items', { params })).data;

export const queryMarketplaceItem = async (slug: string): Promise<StoreResult<MarketItemDetail>> =>
  (await axios.get(`/backend/core/marketplace/items/${encodeURIComponent(slug)}`)).data;

export const queryMarketplaceItemVersions = async (slug: string): Promise<StoreResult<MarketItemVersion[]>> =>
  (await axios.get(`/backend/core/marketplace/items/${encodeURIComponent(slug)}/versions`)).data;

/** 资产代理 blob 加载（鉴权头随 axios 注入；失败静默——调用方回退占位图） */
export const fetchMarketplaceAsset = async (path: string): Promise<Blob> =>
  (await axios.get('/backend/core/marketplace/asset', { params: { path }, responseType: 'blob', skipErrorHandler: true } as any)).data;

export const installMarketplaceItem = async (params: MarketInstallParams): Promise<ThemeInstallReport> => (await axios.post('/backend/core/marketplace/install', params)).data;

// ----- C2：账号连接（设备授权流）/ 授权 / 更新 -----

/** 连接状态投影（绝不含令牌）；reconnectRequired=true 表示连接失效须重连（刷新救不回来） */
export interface MarketConnection {
  connected: boolean;
  reconnectRequired: boolean;
  accountEmail?: string | null;
  accountName?: string | null;
  instanceName?: string | null;
  connectedAt?: string | null;
}

/** 设备授权流启动结果（RFC 8628）：user_code 展示 + 批准页链接（complete 版含预填码，二维码素材） */
export interface MarketDeviceStart {
  userCode: string;
  verificationUri: string;
  verificationUriComplete: string;
  expiresIn: number;
  interval: number;
}

export type MarketPollStatus = 'PENDING' | 'CONNECTED' | 'DENIED' | 'EXPIRED' | 'NONE';

export interface MarketPollResult {
  status: MarketPollStatus;
  accountEmail?: string | null;
  accountName?: string | null;
}

export interface MarketEntitlement {
  /** 条目 slug */
  item: string;
  name?: string | null;
  source?: string | null;
  grantedAt?: string | null;
}

export interface MarketEntitlementsSnapshot {
  connected: boolean;
  reconnectRequired: boolean;
  entitlements: MarketEntitlement[];
}

export interface MarketUpdateCheckEntry {
  /** 已装主题目录名（市场安装缺省即条目 slug——服务端按 slug 匹配） */
  name: string;
  version: string;
}

export interface MarketUpdateCheckResult {
  name: string;
  installedVersion: string;
  latestVersion?: MarketItemVersionSummary | null;
  updateAvailable: boolean;
}

export interface MarketUpdateParams {
  slug: string;
  version?: string;
  siteId: string | number;
  /** 已装主题目录名（更新目标），缺省取 slug */
  themeName?: string;
  seedScope?: SeedScope;
  /** fillOnly | syncUnmodified | resetAllOrigin，缺省 fillOnly */
  overwrite?: string;
}

export const queryMarketplaceConnection = async (): Promise<MarketConnection> => (await axios.get('/backend/core/marketplace/connection')).data;

export const startMarketplaceConnect = async (instanceName?: string): Promise<StoreResult<MarketDeviceStart>> =>
  (await axios.post('/backend/core/marketplace/connection/start', { instanceName })).data;

export const pollMarketplaceConnect = async (): Promise<MarketPollResult> => (await axios.post('/backend/core/marketplace/connection/poll')).data;

export const disconnectMarketplace = async (): Promise<{ remoteRevoked: boolean }> => (await axios.post('/backend/core/marketplace/connection/disconnect')).data;

export const queryMarketplaceEntitlements = async (): Promise<StoreResult<MarketEntitlementsSnapshot>> =>
  (await axios.get('/backend/core/marketplace/entitlements')).data;

export const marketplaceUpdateCheck = async (entries: MarketUpdateCheckEntry[]): Promise<StoreResult<MarketUpdateCheckResult[]>> =>
  (await axios.post('/backend/core/marketplace/update-check', entries)).data;

export const updateMarketplaceItem = async (params: MarketUpdateParams): Promise<ThemeUpdateReport> => (await axios.post('/backend/core/marketplace/update', params)).data;

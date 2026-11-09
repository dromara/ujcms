import axios from '@/utils/request';
import type { ActivateTarget, ThemeInstallReport, ThemeUpdateReport, SeedScope } from '@/api/theme';

/**
 * 应用市场 admin API（remote-marketplace C1/C2）。
 *
 * Backend mapping: `com.ujcms.cms.core.web.backendapi.MarketplaceController`。
 * 控制台唯一的商店通道（D-K3）：目录浏览走后端代理，安装/更新在服务端完成（下载 + sha256 校验 +
 * 既有安装管线）；图片经带鉴权的资产代理以 blob 加载（D-C1-4）；账号连接走设备授权流（D-K4），
 * 令牌只存服务端（加密落库，D-C1-1）。
 * 权限：`marketplace:browse` / `marketplace:install` / `marketplace:connect` / `marketplace:purchase`（`*` 均可越过）。
 * C4b：控制台内购买 `purchase/start|poll|cancel`——钱不经 CMS，控制台只渲染渠道二维码或新标签页打开渠道页面（D-C4-3）。
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
  /** 持久化 scope 含 orders:write（C4b）；已连接但为 false = C4b 之前的连接，购买对话框提示重新连接（D-C4-4） */
  purchaseEnabled?: boolean;
  /** 账号邮箱——服务端已脱敏（122***@qq.com，C4c） */
  accountEmail?: string | null;
  /** 账号手机号——服务端已脱敏（138****0000，C4a）；生产账号多为仅手机号 */
  accountPhone?: string | null;
  /** 显示名（常为微信昵称）；芯片显示 显示名（脱敏手机号 | 脱敏邮箱），见 MarketplaceGallery accountLabel（C4c） */
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
  /** 已脱敏，同 MarketConnection.accountEmail */
  accountEmail?: string | null;
  /** 已脱敏，同 MarketConnection.accountPhone */
  accountPhone?: string | null;
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

// ----- C4b：控制台内购买（start → poll → 终态；订单号不是凭据，经控制台往返，CMS 不持有挂起购买） -----

/**
 * 购买发起结果：`kind=qrcode` → 就地渲染 qrContent（微信支付 Native）；`kind=redirect` → 「前往支付」新标签页打开 url
 * （已解析为绝对地址）；providers 多于一项时提供「更换支付方式」；expiresAt 原样带回 poll 供过期推导。
 */
export interface MarketPurchaseStart {
  orderNo: string;
  itemName: string;
  amountCents: number;
  provider: string;
  providers: string[];
  kind: 'redirect' | 'qrcode';
  qrContent?: string | null;
  url?: string | null;
  expiresAt?: string | null;
  /** 店面「我的订单」地址（订单与发票在应用市场网站查看，D-M14-5） */
  storefrontOrderUrl: string;
}

/** EXPIRED 为 CMS 推导：支付载荷过期而订单仍未付（服务端没有过期状态） */
export type MarketPurchaseStatus = 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED';

export interface MarketPurchasePoll {
  status: MarketPurchaseStatus;
}

/** UNCONFIRMED：支付渠道暂时无法确认关单，订单保留（仍可付 / 稍后再取消） */
export type MarketPurchaseCancelStatus = 'CANCELLED' | 'PAID' | 'UNCONFIRMED';

export interface MarketPurchaseCancel {
  status: MarketPurchaseCancelStatus;
}

export interface MarketPurchaseStartParams {
  slug: string;
  /** 缺省取支付方式列表首项；「更换支付方式」/「重新发起」带上所选项 */
  provider?: string;
}

export interface MarketPurchasePollParams {
  orderNo: string;
  provider: string;
  expiresAt?: string | null;
}

/**
 * 发起购买。调用方自行处理错误（skipErrorHandler）：409 = 连接须重连（C4b 之前的连接缺 orders:write，或连接已失效）→
 * 对话框显示「重新连接以启用控制台内购买」；400 = 可诉业务错误（已购买 / 订单状态已变 / 无可用支付方式…）→ 对话框显示文案。
 */
export const startMarketplacePurchase = async (params: MarketPurchaseStartParams): Promise<StoreResult<MarketPurchaseStart>> =>
  (await axios.post('/backend/core/marketplace/purchase/start', params, { skipErrorHandler: true } as any)).data;

export const pollMarketplacePurchase = async (params: MarketPurchasePollParams): Promise<MarketPurchasePoll> =>
  (await axios.post('/backend/core/marketplace/purchase/poll', params, { skipErrorHandler: true } as any)).data;

export const cancelMarketplacePurchase = async (orderNo: string): Promise<MarketPurchaseCancel> =>
  (await axios.post('/backend/core/marketplace/purchase/cancel', { orderNo })).data;

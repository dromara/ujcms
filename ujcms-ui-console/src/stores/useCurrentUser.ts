import { reactive, readonly } from 'vue';
import type { RouteRecordRaw } from 'vue-router';
import { useAppStateStore } from '@/stores/appStateStore';
import { accountLogin, accountLogout, accountRefreshToken, queryBackendState, verifySecondFactorCode } from '@/api/login';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { removeAllEditorDrafts } from '@/components/Tinymce/draft';
import type { LoginParam, SecondFactorVerifyParam } from '@/api/login';
import {
  setAccessToken,
  removeAccessToken,
  getRefreshToken,
  setRefreshToken,
  getRefreshAt,
  setRefreshAt,
  getAccessAt,
  setAccessAt,
  getSessionTimeout,
  setSessionTimeout,
  removeSessionTimeout,
  removeRefreshToken,
  removeAccessAt,
  removeRefreshAt,
} from '@/utils/auth';

export interface CurrentUser {
  databaseType?: string;
  username?: string;
  avatar?: string;
  rank: number;
  permissions?: string[];
  grantPermissions?: string[];
  globalPermission: boolean;
  allChannelPermission: boolean;
  allArticlePermission: boolean;
  allStatusPermission: boolean;
  dataScope: number;
  loginDate?: Date;
  loginIp?: string;
  dataMigrationEnabled: boolean;
  lockedPermissions: string[];
  commercialTier: number;
}

const defaultUser: CurrentUser = {
  rank: 32767,
  globalPermission: false,
  allChannelPermission: true,
  allArticlePermission: true,
  allStatusPermission: false,
  dataScope: 4,
  dataMigrationEnabled: false,
  lockedPermissions: [],
  commercialTier: 0,
};

const state = reactive<CurrentUser>(defaultUser);

const setCurrentUser = (user: CurrentUser): void => {
  Object.assign(state, user);
};

export const currentUser = readonly(state);

const storeTokens = (result: any): void => {
  const now = Date.now();
  setAccessToken(result.accessToken);
  setRefreshToken(result.refreshToken);
  setSessionTimeout(result.sessionTimeout);
  setAccessAt(now);
  setRefreshAt(now);
};

// 开启双因子认证时，口令通过后返回 status 1020（result 含 mfaToken、maskedMobile），此时尚未登录，
// 需再经 verifySecondFactor 提交短信验证码才会下发令牌。
export const login = async (params: LoginParam): Promise<any> => {
  const data = await accountLogin(params);
  if (data.status === 0) {
    storeTokens(data.result);
  }
  return data;
};

export const verifySecondFactor = async (params: SecondFactorVerifyParam): Promise<any> => {
  const data = await verifySecondFactorCode(params);
  if (data.status === 0) {
    storeTokens(data.result);
  }
  return data;
};

export const logout = (): void => {
  const refreshToken = getRefreshToken();
  if (refreshToken) {
    accountLogout(refreshToken);
  }
  removeAccessAt();
  removeRefreshAt();
  removeAccessToken();
  removeRefreshToken();
  removeSessionTimeout();
  // 富文本的本地草稿是正文内容：不留给下一个用这台电脑的人
  removeAllEditorDrafts();
  setCurrentUser(defaultUser);
  // 个人偏好随账号：登出即复位（清除写在 :root 上的主题色变量）
  usePreferencesStore().seed();
};

// 会话超时后就地重新登录。登录框是挂在 App.vue 下的 ReLoginDialog，由 loginBoxDisplay 驱动：
// 开启双因子认证时重新登录同样要过两步，ElMessageBox.prompt 承载不了第二步。
const showLoginBox = () => {
  const appState = useAppStateStore();
  if (appState.messageBoxDisplay) {
    ElMessageBox.close();
  }
  appState.setLoginBoxDisplay(true);
};

// 刷新间隔时间。默认 5 分钟。
const interval = 5 * 60 * 1000;
let refreshInterval: any;

/**
 * RefreshToken 刷新机制。
 *
 * 1. 自动定时刷新。永不过期。
 * 2. 访问时刷新。30分钟过期。
 */
const runRefreshToken = async () => {
  const refreshToken = getRefreshToken();
  // refreshToken不存在，不刷新。
  if (!refreshToken) {
    return;
  }
  const accessAt = getAccessAt();
  const now = Date.now();
  // 超过时间没有访问，退出登录。默认为 30 分钟。
  const sessionTimeout = getSessionTimeout() * 60 * 1000;
  if (now - accessAt > sessionTimeout) {
    logout();
    showLoginBox();
    return;
  }
  // 记录刷新时间，用于重新加载页面时，初始化Interval。
  setRefreshAt(now);
  const data = await accountRefreshToken({ refreshToken });
  // accountRefreshToken 会把 accessAt 修改为当前时间，这里需要进行重置
  setAccessAt(accessAt);
  if (data.status !== 0) {
    removeRefreshToken();
    return;
  }
  const { result } = data;
  setAccessToken(result.accessToken);
  setRefreshToken(result.refreshToken);
};

export const initRefreshInterval = (): void => {
  let afterTime = getRefreshAt() + interval - Date.now();
  if (afterTime < 0) afterTime = 0;
  setTimeout(() => {
    runRefreshToken();
    if (!refreshInterval) {
      refreshInterval = setInterval(runRefreshToken, interval);
    }
  }, afterTime);
};

export const fetchCurrentUser = async (): Promise<any> => {
  const user = await queryBackendState();
  if (user) {
    setCurrentUser({
      databaseType: user.databaseType,
      username: user.username,
      avatar: user.avatar,
      rank: user.rank,
      permissions: user.permissions,
      grantPermissions: user.grantPermissions,
      globalPermission: user.globalPermission,
      allChannelPermission: user.allChannelPermission,
      allArticlePermission: user.allArticlePermission,
      allStatusPermission: user.allStatusPermission,
      dataScope: user.dataScope,
      loginDate: user.loginDate,
      loginIp: user.loginIp,
      dataMigrationEnabled: user.dataMigrationEnabled,
      lockedPermissions: user.lockedPermissions,
      commercialTier: user.commercialTier,
    });
    // 控制台偏好随 backend-state 下发（侧栏风格、主题色、首页设置）及安装默认外观（console-shell D7），外壳首屏即按偏好绘制
    usePreferencesStore().seed(user.preferences, user.console);
    // /env/config strips admin-only security fields (ssrfList, password
    // aging policy, login-attempt thresholds). Now that the session is
    // authenticated, pull them from /backend/core/config so TinyMCE's
    // image-fetch SSRF check and password forms see the real values.
    useSysConfigStore().enrichForAdmin();
  } else {
    removeAccessToken();
    removeRefreshToken();
  }
  return user;
};

function includes(arr: string[] | undefined, str: string): boolean {
  if (arr == null) {
    return false;
  }
  return arr.includes(str) || arr.includes('*');
}

export const hasCurrentUser = (): boolean => state.username !== undefined;
export const isUnlocked = (permission?: string): boolean => !permission || !includes(state.lockedPermissions, permission);
export const hasPermission = (permission?: string): boolean => !permission || includes(state.permissions, permission);
/** 是否缺少权限（true = 无权；语义反转以便直接绑定 :disabled）。 */
export const perm = (permission?: string): boolean => !hasPermission(permission);
// 社区版（commercialTier===0）预览模式下被锁定的商业功能：入口展示徽标，点击进入功能介绍页。
// 商业版（commercialTier>=1）对更高级别功能保持隐藏，本函数恒为 false。
export const isCommercialLocked = (permission?: string): boolean =>
  state.commercialTier === 0 && permission != null && !isUnlocked(permission);
// importData:*（数据迁移，T1）在商业级别之上再受 ujcms.data-migration-enabled 开关控制：社区版照常出锁定徽标进介绍页，
// 商业版只在已解锁且开关开启时显示
export const isShowPerm = (permission?: string): boolean =>
  permission?.startsWith('importData:')
    ? isCommercialLocked(permission) || (isUnlocked(permission) && state.dataMigrationEnabled)
    : isUnlocked(permission) || isCommercialLocked(permission);
// 分组（含 /stat 的二级分组）的可见性由子项推导：任一子项可见则分组可见，子项全不可见则分组隐藏。
// menu.* 分组码只用于社区版分组行的商业徽标，不参与可见性——角色 CSV 里的分组码是保存时的快照，
// 菜单项跨组移动后旧角色缺目标组的码，若以码判断会让该项从侧栏消失（console-menu 规约）。
export const isShowMenu = (route: RouteRecordRaw): boolean => {
  if (route.meta?.hidden) {
    return false;
  }
  if (route.children?.length) {
    return route.children.some((child) => isShowMenu(child));
  }
  return hasPermission(route.meta?.requiresPermission) && isShowPerm(route.meta?.requiresPermission);
};
// 级别区块展示判断：社区版全部展示用于推广，商业版只展示已购级别
export const isShowTier = (tier: number): boolean => state.commercialTier === 0 || state.commercialTier >= tier;

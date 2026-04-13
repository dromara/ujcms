import { reactive, readonly } from 'vue';
import type { RouteRecordRaw } from 'vue-router';
import type { Action, MessageBoxState } from 'element-plus/es/components/message-box/src/message-box.type';
import i18n from '@/i18n';
import { sm2Encrypt } from '@/utils/sm';
import { queryClientPublicKey } from '@/api/login';
import { useAppStateStore } from '@/stores/appStateStore';
import { accountLogin, accountLogout, accountRefreshToken, queryCurrentUser } from '@/api/login';
import type { LoginParam } from '@/api/login';
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
  epExcludes: string[];
  epDisplay: boolean;
  epRank: number;
  epActivated: boolean;
}

const defaultUser: CurrentUser = {
  rank: 32767,
  globalPermission: false,
  allChannelPermission: true,
  allArticlePermission: true,
  allStatusPermission: false,
  dataScope: 4,
  dataMigrationEnabled: false,
  epExcludes: [],
  epDisplay: false,
  epRank: 0,
  epActivated: false,
};

const state = reactive<CurrentUser>(defaultUser);

const setCurrentUser = (user: CurrentUser): void => {
  Object.assign(state, user);
};

export const currentUser = readonly(state);

export const login = async (params: LoginParam): Promise<any> => {
  const data = await accountLogin(params);
  if (data.status === 0) {
    const { result } = data;
    const now = Date.now();
    setAccessToken(result.accessToken);
    setRefreshToken(result.refreshToken);
    setSessionTimeout(result.sessionTimeout);
    setAccessAt(now);
    setRefreshAt(now);
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
  setCurrentUser(defaultUser);
};

const {
  global: { t },
} = i18n;

const showLoginBox = () => {
  const appState = useAppStateStore();
  if (appState.messageBoxDisplay) {
    ElMessageBox.close();
  }
  if (!appState.loginBoxDisplay) {
    appState.setLoginBoxDisplay(true);
    ElMessageBox.prompt(state.username, t('enterPassword'), {
      cancelButtonText: t('backToLogin'),
      confirmButtonText: t('login'),
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      closeOnHashChange: false,
      inputType: 'password',
      inputValue: import.meta.env.MODE === 'development' ? 'password' : undefined,
      beforeClose: async (action: Action, instance: MessageBoxState, done: () => void) => {
        if (action === 'cancel') {
          done();
          window.location.reload();
        }
        if (action === 'confirm') {
          const inputValue = instance.inputValue;
          if (inputValue == null || inputValue === '') {
            instance.editorErrorMessage = t('v.required');
            instance.validateError = true;
            return;
          }
          const publicKey = await queryClientPublicKey();
          const password = sm2Encrypt(instance.inputValue, publicKey);
          const data = await login({ username: state.username ?? '', password });
          // 登录失败，显示错误信息
          if (data.status !== 0) {
            instance.editorErrorMessage = data.message;
            instance.validateError = true;
            return;
          }
          appState.setLoginBoxDisplay(false);
          done();
        }
      },
    }).catch();
  }
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
  const user = await queryCurrentUser();
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
      epExcludes: user.epExcludes,
      epDisplay: user.epDisplay,
      epRank: user.epRank,
      epActivated: user.epActivated,
    });
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
export const isInclude = (permission?: string): boolean => !permission || !includes(state.epExcludes, permission);
export const hasPermission = (permission?: string): boolean => !permission || includes(state.permissions, permission);
export const perm = (permission?: string): boolean => !hasPermission(permission);
export const isShowPerm = (permission?: string): boolean =>
  (state.epDisplay && !permission?.startsWith('machine:')) || (state.dataMigrationEnabled && permission?.startsWith('importData:')) || isInclude(permission);
export const isShowMenu = (route: RouteRecordRaw): boolean => !route.meta?.hidden && hasPermission(route.meta?.requiresPermission) && isShowPerm(route.meta?.requiresPermission);

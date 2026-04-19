import { h } from 'vue';
import axios from 'axios';
import dayjs from 'dayjs';
import i18n from '@/i18n';
import { getAuthHeaders, removeAccessToken, removeRefreshToken, setAccessAt } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { useAppStateStore } from '@/stores/appStateStore';

const {
  global: { t },
} = i18n;
const showMessageBox = () => {
  const appState = useAppStateStore();
  if (!appState.loginBoxDisplay && !appState.messageBoxDisplay) {
    window.location.reload();
    // session超时会自动显示登录界面，应该不需要保留在原页面的提示框
    // setMessageBoxDisplay(true);
    // ElMessageBox.confirm(t('confirmLogin'), {
    //   cancelButtonText: t('cancel'),
    //   confirmButtonText: t('loginAgain'),
    //   type: 'warning',
    //   callback: (action: string) => {
    //     if (action === 'cancel' || action === 'close') {
    //       setMessageBoxDisplay(false);
    //       return;
    //     }
    //     if (action === 'confirm') {
    //       // 未登录。刷新页面以触发登录。无法直接使用router，会导致其它函数不可用的奇怪问题。
    //       window.location.reload();
    //     }
    //   },
    // });
  }
};

const service = axios.create({
  baseURL: import.meta.env.VITE_BASE_API,
  timeout: 30000,
});

service.interceptors.request.use(
  (config) => {
    setAccessAt(Date.now());
     
    config.headers = { ...config.headers, ...getAuthHeaders(), ...getSiteHeaders() };
    return config;
  },
  (error) => Promise.reject(error),
);

export interface ErrorInfo {
  message?: string;
  path?: string;
  error?: string;
  exception?: string;
  trace?: string;
  timestamp?: Date;
  status?: number;
}

export const handleError = ({ timestamp, message, path, error, exception, trace, status }: ErrorInfo): void => {
  if (exception === 'com.ujcms.cms.core.web.support.SiteForbiddenException') {
    //没有当前站点权限，清空站点信息，刷新页面以获取默认站点
    useCurrentSiteStore().setCurrentSiteId(null);
    window.location.reload();
  } else if (exception === 'com.ujcms.commons.web.exception.LogicException') {
    ElMessageBox.alert(message, { type: 'warning' });
  } else if (status === 401) {
    removeAccessToken();
    removeRefreshToken();
    showMessageBox();
  } else if (status === 404) {
    ElMessageBox({
      title: t('error.title'),
      type: 'warning',
      message: h('div', null, [h('p', { class: 'text-lg' }, t('error.notFound')), message ? h('p', { class: 'mt-2' }, message) : null]),
    });
  } else if (status === 403) {
    ElMessageBox({
      title: String(status),
      message: h('div', null, [h('p', { class: 'text-lg' }, t('error.forbidden')), h('p', { class: 'mt-2' }, path), h('p', { class: 'mt-2' }, message)]),
    });
  } else {
    ElMessageBox({
      title: t('error.title'),
      message: h('div', null, [
        h('h', null, [h('span', { class: 'text-4xl' }, status), h('span', { class: ['ml-2', 'text-xl'] }, error)]),
        h('p', { class: 'mt-2' }, dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss')),
        h('p', { class: 'mt-2' }, path),
        h('p', { class: 'mt-2' }, message),
        h('p', { class: 'mt-2' }, exception),
        h('pre', { class: 'mt-2' }, [h('code', { class: ['whitespace-pre-wrap'] }, trace)]),
      ]),
      customStyle: { maxWidth: '100%' },
    });
  }
};

service.interceptors.response.use(
  (response) => response,
  (e) => {
    const {
      response: { data, status, statusText },
    } = e;
    // spring boot 的响应
    if (data) {
      handleError(data);
      return Promise.reject(data.error);
    }
    // spring scurity BearerTokenAuthenticationEntryPoint 的响应
    handleError({ status });
    return Promise.reject(statusText);
  },
);

export default service;

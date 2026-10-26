import { h } from 'vue';
import axios from 'axios';
import dayjs from 'dayjs';
import i18n, { getLocaleHeaders } from '@/i18n';
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

    config.headers = { ...config.headers, ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() };
    return config;
  },
  (error) => Promise.reject(error),
);

export interface ErrorInfo {
  message?: string;
  path?: string;
  error?: string;
  exception?: string;
  /** 5xx 错误编号（DisclosureErrorAttributes）：与服务器日志、模板管理「最近错误」中的编号一致 */
  errorId?: string;
  trace?: string;
  timestamp?: Date;
  status?: number;
}

/** 当前正在显示的微信接口错误提示，用于合并同一页面并发请求产生的相同提示 */
let openWechatAlert: string | undefined;

export const handleError = ({ timestamp, message, path, error, exception, errorId, trace, status }: ErrorInfo): void => {
  if (exception === 'com.ujcms.cms.core.web.support.SiteForbiddenException') {
    //没有当前站点权限，清空站点信息，刷新页面以获取默认站点
    useCurrentSiteStore().setCurrentSiteId(null);
    window.location.reload();
  } else if (exception === 'com.ujcms.cms.commercial.t3.component.WechatApiException') {
    // 微信接口错误：后端已生成含错误码与处理建议的提示，只显示提示，不显示堆栈。
    // 列表页会同时请求列表和数量，同一提示只弹一次
    if (openWechatAlert !== message) {
      openWechatAlert = message;
      const reset = () => (openWechatAlert = undefined);
      ElMessageBox.alert(message, { type: 'warning' }).then(reset, reset);
    }
  } else if (exception === 'com.ujcms.common.web.exception.LogicException') {
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
        errorId ? h('p', { class: 'mt-2' }, `${t('error.id')}${t('punct.colon')}${errorId}`) : null,
        h('pre', { class: 'mt-2' }, [h('code', { class: ['whitespace-pre-wrap'] }, trace)]),
      ]),
      customStyle: { maxWidth: '100%' },
    });
  }
};

/**
 * responseType 为 blob 的请求（文件下载）失败时，错误响应体同样是 Blob：解析出其中的 JSON 再按普通错误提示。
 * 这类请求须带 skipErrorHandler，由调用方 catch 后交给本函数
 */
export const handleBlobError = async (e: any): Promise<void> => {
  let data = e?.response?.data;
  if (data instanceof Blob) {
    try {
      data = JSON.parse(await data.text());
    } catch {
      data = undefined;
    }
  }
  if (data != null && typeof data === 'object') {
    handleError(data);
    return;
  }
  handleError({ status: e?.response?.status });
};

service.interceptors.response.use(
  (response) => response,
  (e) => {
    const {
      response: { data, status, statusText },
    } = e;
    // 调用方自行处理错误（如 409 乐观锁冲突的 diff 后重试），跳过全局弹窗，抛出原始错误
    if ((e.config as any)?.skipErrorHandler) {
      return Promise.reject(e);
    }
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

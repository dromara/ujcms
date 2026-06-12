import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import type { RouteLocationNormalized } from 'vue-router';
import i18n from '@/i18n';
import { getAccessToken } from '@/utils/auth';
import { hasCurrentUser, fetchCurrentUser, hasPermission } from '@/stores/useCurrentUser';
import router from './router';

NProgress.configure({ showSpinner: false });

const LOGIN_PATH = '/login';

router.beforeEach(async (to: RouteLocationNormalized) => {
  const isLogin = getAccessToken() !== undefined;
  // 不需要权限
  if (!to.meta?.requiresPermission) {
    // 已登录状态访问登录页面，跳转到首页
    if (to.path === LOGIN_PATH && isLogin) return '/';
    NProgress.start();
    return true;
  }
  // 需要权限
  const toLogin = `${LOGIN_PATH}?redirect=${to.path}`;
  // 未登录，跳转到登录页面
  if (!isLogin) return toLogin;
  NProgress.start();
  if (!hasCurrentUser()) {
    const user = await fetchCurrentUser();
    // 没有获取到当前用户数据，代表accessToken已经失效，需要重新登录。
    if (!user) {
      NProgress.done();
      return toLogin;
    }
  }
  // 没有权限
  if (!hasPermission(to.meta?.requiresPermission)) {
    NProgress.done();
    if (to.path === '/') {
      return '/403';
    }
    return '/';
  }
  return true;
});

router.afterEach((to: RouteLocationNormalized) => {
  document.title = getPageTitle(to.meta.title);
  NProgress.done();
});

const title = import.meta.env.VITE_APP_TITLE || 'UJCMS后台管理';

function getPageTitle(pageTitle?: string): string {
  if (pageTitle) {
    const {
      global: { t },
    } = i18n;
    return `${t(pageTitle)} - ${title}`;
  }
  return `${title}`;
}

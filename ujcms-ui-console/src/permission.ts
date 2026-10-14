import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import type { RouteLocationNormalized } from 'vue-router';
import i18n from '@/i18n';
import { getAccessToken } from '@/utils/auth';
import { hasCurrentUser, fetchCurrentUser, hasPermission, isCommercialLocked } from '@/stores/useCurrentUser';
import { isSystemInstalled } from '@/api/install';
import router from './router';

NProgress.configure({ showSpinner: false });

const LOGIN_PATH = '/login';
const INSTALL_PATH = '/install';

router.beforeEach(async (to: RouteLocationNormalized) => {
  const isLogin = getAccessToken() !== undefined;
  // 不需要权限
  if (!to.meta?.requiresPermission) {
    // 未安装系统（install-flow D2）：登录页让位安装向导；已安装后访问安装页回登录。
    // 探测结果粘滞缓存（已安装即不再发请求），失败视为已安装（fail-open）
    if (to.path === LOGIN_PATH && !isLogin && !(await isSystemInstalled())) return INSTALL_PATH;
    if (to.path === INSTALL_PATH && (await isSystemInstalled())) return LOGIN_PATH;
    // 已登录状态访问登录页面，跳转到首页
    if (to.path === LOGIN_PATH && isLogin) return '/';
    NProgress.start();
    return true;
  }
  // 需要权限。redirect 用 fullPath（带查询串，由 vue-router 编码）：构建器等页面的路由参数
  // （?page=&region=）在重新登录后必须原样带回，否则落回缺参错误页
  const toLogin = { path: LOGIN_PATH, query: { redirect: to.fullPath } };
  // 未登录，跳转到登录页面（未安装系统则跳安装向导）
  if (!isLogin) {
    if (!(await isSystemInstalled())) return INSTALL_PATH;
    return toLogin;
  }
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
  // 社区版预览模式：被锁定的商业功能跳转到功能介绍页（覆盖菜单点击、深链与编程式跳转）
  if (isCommercialLocked(to.meta?.requiresPermission)) {
    NProgress.done();
    return { path: '/commercial-feature', query: { perm: to.meta?.requiresPermission } };
  }
  return true;
});

router.afterEach((to: RouteLocationNormalized) => {
  document.title = getPageTitle(to.meta.title);
  NProgress.done();
});

function getPageTitle(pageTitle?: string): string {
  const {
    global: { t },
  } = i18n;
  // VITE_APP_TITLE 可固定站点自定义标题；未设置时随控制台语言
  const title = import.meta.env.VITE_APP_TITLE || t('app.title');
  if (pageTitle) {
    return `${t(pageTitle)} - ${title}`;
  }
  return `${title}`;
}

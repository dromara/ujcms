/**
 * 最近访问的视图（路由名，最近在前，至多 8 条），供命令面板的空态展示（docs/spec/console-shell D32）。
 * 属工作状态而非偏好（console-view-state），只存浏览器本地；无权限或已不存在的路由名由面板按索引丢弃。
 */
const KEY = 'ujcms.recentViews';
const MAX = 8;

export const readRecentViews = (): string[] => {
  try {
    const value = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(value) ? value.filter((it): it is string => typeof it === 'string').slice(0, MAX) : [];
  } catch {
    return [];
  }
};

export const recordRecentView = (name: string): void => {
  try {
    const next = [name, ...readRecentViews().filter((it) => it !== name)].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // 隐私模式 / 存储被禁用：最近访问只是便利，静默放弃
  }
};

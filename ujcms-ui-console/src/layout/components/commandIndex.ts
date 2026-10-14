import type { RouteLocationNormalizedLoaded, RouteRecordRaw } from 'vue-router';
import { hasPermission, isCommercialLocked, isShowMenu } from '@/stores/useCurrentUser';
import { GLOBAL_SETTINGS_TABS, SITE_SETTINGS_TABS, lockedSettingsTier, visibleSettingsNavs, type SettingsTab } from '@/views/config/settingsNav';

/**
 * 命令面板的索引（docs/spec/console-shell D31、D8）。四个来源穷尽“侧栏与页头能到达的一切，以及设置页里的每一项”：
 * 侧栏叶子（router.options.routes × isShowMenu，含 /stat 二级分组；社区版锁定项带商业版标识、经路由守卫进介绍页）、
 * 全局设置 / 站点设置的页签（settingsNav 描述符，与视图同源）、页签内的设置项（描述符的 fields，级别达到时才有）、
 * 个人项（页头的账号菜单与收件箱）。
 * 匹配串取自当前语言与另一种语言的文案（大小写不敏感子串），中文界面可搜 watermark、英文界面可搜 水印；
 * 设置项另带提示文案（.tooltip）作低优先级匹配，“生成静态页”能找到提示里提到它的项。
 */
export type CommandKind = 'route' | 'settings' | 'field' | 'action';

export interface CommandEntry {
  /** route:<路由名> | settings:<路由名>:<导航项> | field:<路由名>:<标签键> | action:<id> */
  id: string;
  kind: CommandKind;
  /** 标题的 i18n 键（叶子 / 页签 / 设置项标签 / 动作） */
  title: string;
  /** 所在分组链的 i18n 键，作为面包屑显示（设置项以其页签收尾） */
  trail: string[];
  /** 路由名（route / settings / field） */
  name?: string;
  /** settings / field 的深链 query（{ type }） */
  query?: Record<string, string>;
  /** field：设置项的标签键，打开页签后据此定位（settingsFocus.ts） */
  field?: string;
  /** 社区版锁定的商业功能：显示商业版标识；叶子跳转仍经路由守卫，锁定的设置页签打开后只有版本说明 */
  locked: boolean;
  run?: () => void;
  /** 小写化的匹配串（标题文案） */
  haystack: string[];
  /** 小写化的低优先级匹配串（设置项的提示文案），标题都不命中时才参与 */
  hints?: string[];
}

/** 页头提供的个人项：带 name 的是隐藏路由（我的私信、我的通知），带 run 的打开对话框（修改密码、外观设置） */
export interface CommandAction {
  id: string;
  title: string;
  perm?: string;
  name?: string;
  run?: () => void;
}

/** i18n 键 → 各语言文案；键不存在时给空串（提示文案并非每项都有） */
export type LabelSource = (key: string) => string[];

export const buildCommandIndex = (routes: readonly RouteRecordRaw[], actions: CommandAction[], labels: LabelSource): CommandEntry[] => {
  const entries: CommandEntry[] = [];
  const haystackOf = (keys: string[]): string[] =>
    keys
      .flatMap(labels)
      .filter(Boolean)
      .map((label) => label.toLowerCase());

  // 与 MenuItem 同样的遍历：隐藏路由与无权限项跳过；有标题且有子项的是分组（含 /stat 的二级分组），进入分组链
  const walk = (route: RouteRecordRaw, trail: string[]): void => {
    if (route.meta?.hidden || !isShowMenu(route)) return;
    const title = route.meta?.title;
    if (route.children?.length && title) {
      route.children.forEach((child) => walk(child, [...trail, title]));
      return;
    }
    if (!route.name || !title) return;
    const name = String(route.name);
    entries.push({
      id: `route:${name}`,
      kind: 'route',
      title,
      trail,
      name,
      locked: isCommercialLocked(route.meta?.requiresPermission),
      haystack: haystackOf([title]),
    });
  };
  routes.forEach((route) => walk(route, []));

  // 页签一条，紧接着它的设置项各一条（页面顺序），结果列表里设置项自然跟在所属页签之后
  const settings = (tabs: SettingsTab[], name: string, prefix: string, perm: string, trail: string[]): void => {
    if (!hasPermission(perm)) return;
    visibleSettingsNavs(tabs).forEach(({ nav, type, fields }) => {
      const title = `${prefix}${nav}`;
      const query = { type };
      const locked = lockedSettingsTier(tabs, nav) != null;
      entries.push({ id: `settings:${name}:${nav}`, kind: 'settings', title, trail, name, query, locked, haystack: haystackOf([title]) });
      fields.forEach((key) => {
        entries.push({
          id: `field:${name}:${key}`,
          kind: 'field',
          title: key,
          trail: [...trail, title],
          name,
          query,
          field: key,
          locked: false,
          haystack: haystackOf([key]),
          hints: haystackOf([`${key}.tooltip`]),
        });
      });
    });
  };
  settings(GLOBAL_SETTINGS_TABS, 'GlobalSettings', 'config.settings.', 'config:show', ['menu.config', 'menu.config.globalSettings']);
  settings(SITE_SETTINGS_TABS, 'SiteSettings', 'site.settings.', 'siteSettings:show', ['menu.config', 'menu.config.siteSettings']);

  actions
    .filter((action) => hasPermission(action.perm))
    .forEach((action) => {
      entries.push({
        id: action.name ? `route:${action.name}` : `action:${action.id}`,
        kind: action.name ? 'route' : 'action',
        title: action.title,
        trail: ['menu.personal'],
        name: action.name,
        locked: false,
        run: action.run,
        haystack: haystackOf([action.title]),
      });
    });
  return entries;
};

/** 大小写不敏感子串匹配；标题前缀命中优先，其次标题包含，最后只有提示文案命中的设置项；各组内按索引顺序（即侧栏 / 页面顺序） */
export const searchCommands = (index: CommandEntry[], query: string, limit = 20): CommandEntry[] => {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const prefix: CommandEntry[] = [];
  const rest: CommandEntry[] = [];
  const hinted: CommandEntry[] = [];
  for (const entry of index) {
    const hit = entry.haystack.find((label) => label.includes(q));
    if (hit) (hit.startsWith(q) ? prefix : rest).push(entry);
    else if (entry.hints?.some((hint) => hint.includes(q))) hinted.push(entry);
  }
  return [...prefix, ...rest, ...hinted].slice(0, limit);
};

/** 当前路由是否侧栏叶子（可收藏为常用）：非隐藏，且所在分组带图标（隐藏的 /personal、首页、主题详情等都不是） */
export const isMenuLeafRoute = (route: RouteLocationNormalizedLoaded): boolean =>
  route.name != null && !route.meta.hidden && route.matched.some((record) => record.meta?.icon != null);

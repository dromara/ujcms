import type { Component } from 'vue';
import type { RouteRecordRaw } from 'vue-router';
import { routes } from '@/router';
import { isShowMenu } from '@/stores/useCurrentUser';
import { SHORTCUTS_MAX } from '@/stores/preferencesStore';

/**
 * 侧栏叶子的视图（docs/spec/console-home）：快捷入口、概览的链接与待办的目标都以路由名引用侧栏叶子，
 * 可见性与侧栏同一判定（isShowMenu）。遍历 router 的原始路由树（router.getRoutes() 会丢掉分组）。
 */
export interface MenuLeaf {
  name: string;
  titleKey: string;
  groupTitleKey: string;
  icon?: Component;
}
export interface MenuGroup {
  titleKey: string;
  icon?: Component;
  leaves: MenuLeaf[];
}

/** 未设常用时的默认快捷入口（按使用频度），逐项按可见性过滤 */
export const DEFAULT_SHORTCUTS: readonly string[] = ['ArticleList', 'ChannelList', 'AttachmentList', 'CommentList', 'MessageBoardList', 'ThemeGallery', 'SiteSettings', 'UserList'];
export const MAX_SHORTCUTS = SHORTCUTS_MAX;

const collect = (records: readonly RouteRecordRaw[], group: MenuGroup): void => {
  for (const record of records) {
    if (record.meta?.hidden || !isShowMenu(record)) continue;
    if (record.children?.length && record.meta?.title) {
      // /stat 的二级分组：叶子仍归属一级分组
      collect(record.children, group);
    } else if (record.name && record.meta?.title) {
      group.leaves.push({ name: String(record.name), titleKey: record.meta.title, groupTitleKey: group.titleKey, icon: group.icon });
    }
  }
};

/** 当前用户可见的侧栏叶子，按侧栏分组与顺序；空分组不列 */
export const listMenuLeaves = (): MenuGroup[] =>
  routes
    .filter((record) => !record.meta?.hidden && record.meta?.title && record.meta?.icon && record.children?.length && isShowMenu(record))
    .map((record) => {
      const group: MenuGroup = { titleKey: record.meta!.title!, icon: record.meta!.icon, leaves: [] };
      collect(record.children!, group);
      return group;
    })
    .filter((group) => group.leaves.length > 0);

const leafMap = (): Map<string, MenuLeaf> => new Map(listMenuLeaves().flatMap((group) => group.leaves.map((leaf) => [leaf.name, leaf] as const)));

export const findMenuLeaf = (name: string): MenuLeaf | undefined => leafMap().get(name);
export const isMenuRouteVisible = (name: string): boolean => findMenuLeaf(name) != null;

/** 路由名 → 可见叶子：不存在、不可见的丢弃（实体会被删、地址会被手改，恢复的值一律校验），去重、截断 */
export const resolveShortcuts = (names: readonly string[]): MenuLeaf[] => {
  const leaves = leafMap();
  const seen = new Set<string>();
  const result: MenuLeaf[] = [];
  for (const name of names) {
    const leaf = leaves.get(name);
    if (leaf && !seen.has(name)) {
      seen.add(name);
      result.push(leaf);
      if (result.length >= MAX_SHORTCUTS) break;
    }
  }
  return result;
};

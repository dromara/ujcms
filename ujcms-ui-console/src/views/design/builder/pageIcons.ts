import type { Component } from 'vue';
import { Collection, Compass, Document, Files, Folder, House } from '@element-plus/icons-vue';

/**
 * 页面名 → 图标（顶栏页面切换器芯片与下拉行共用；检查器头部不用，页面身份图标只在切换器出现）。
 * 芯片图标按页面种类取形，让它承载页面名文案之外的信息，并与 页面设置 动作按钮（Operation）区分开；
 * 主题自定义页面或未收录的页面名回退通用文档图标。
 */
const PAGE_ICONS: Record<string, Component> = {
  home: House,
  channel: Folder,
  article: Document,
  topic: Collection,
  system: Files,
  site: Compass,
};

export const pageIconOf = (name: string): Component => PAGE_ICONS[name] ?? Document;

import tinymce from 'tinymce';
import i18n from '@/i18n';
import { CROWN_SVG_SHAPES, lockedTip, showCommercialNotice } from '@/utils/commercial';
import { defaultOptions as ai } from './plugins/ai';
import { formatpainter, typesetting } from './tier2Tools';

// 社区版编辑器里的商业版 T2 工具（一键排版、格式刷、AI 助手）：显示为带皇冠的锁定按钮，悬停说明所需版本，点击弹出版本说明。
// 只放主工具栏——选中文字时的快捷工具栏逐次弹出，不放锁定按钮；T1 不显示（付费版本不提示更高级别），取舍见 Tinymce.vue
const TIER = 2;
const TOOLS = [typesetting, formatpainter, ai];

/** 锁定按钮插件名，社区版才加进 plugins */
export const LOCKED_PLUGIN = 'lockedtools';

/** 工具的锁定按钮名 */
export const lockedName = (id: string): string => `locked${id}`;

// 工具原图标缩为 18px 靠左下，右上角叠 CommercialBadge 同形同色的皇冠；两者留约 1px 间隙（格式刷的刷柄最近），不需要描白边
const withCrown = (icon: string): string => {
  const viewBox = /viewBox="([^"]+)"/.exec(icon)?.[1] ?? '0 0 24 24';
  const shapes = icon.replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '');
  return (
    '<svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">' +
    `<svg x="0" y="5" width="18" height="18" viewBox="${viewBox}">${shapes}</svg>` +
    `<g transform="translate(14.9 0) scale(0.4)" style="color: var(--el-color-warning); fill: currentColor">${CROWN_SVG_SHAPES}</g>` +
    '</svg>'
  );
};

// 以插件注册而不在 setup 回调里：插件在默认图标包载入之后初始化，锁定按钮才能取到真按钮实际显示的图标——
// 默认图标包（TinyMCE 6.6 起）自带 ai 图标，AI 插件自己的 SVG 只在图标包缺它时兜底，取 tool.icon 会画错图标
tinymce.PluginManager.add(LOCKED_PLUGIN, (editor) => {
  const icons = editor.ui.registry.getAll().icons;
  const colon = i18n.global.t('punct.colon');
  for (const tool of TOOLS) {
    const name = lockedName(tool.id);
    editor.ui.registry.addIcon(name, withCrown(icons[tool.id] ?? tool.icon));
    editor.ui.registry.addButton(name, {
      icon: name,
      tooltip: `${tool.name}${colon}${lockedTip(TIER)}`,
      onAction: () => showCommercialNotice(TIER, tool.name),
    });
  }
  return {};
});

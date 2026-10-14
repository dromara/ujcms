import i18n from '@/i18n';
import Plugin from './plugin';

export const defaultOptions = {
  id: 'ai',
  // 选中文字时快捷工具栏上的按钮名（仅选区模式），见 Tinymce.vue 的 quickbars_selection_toolbar
  selectionId: 'aiselection',
  name: i18n.global.t('editor.ai'),
  tooltip: i18n.global.t('editor.ai.tooltip'),
  // 仅兜底：TinyMCE 默认图标包（6.6 起）自带同名 ai 图标（实心“AI”字样），ui/buttons.ts 见名已占用即不注册本图标，按钮实际显示的是图标包的
  icon:
    '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" width="24" height="24">' +
    '<path d="M11 2l2.2 6.3L19.5 10.5l-6.3 2.2L11 19l-2.2-6.3L2.5 10.5l6.3-2.2L11 2z"></path>' +
    '<path d="M19 14l1 2.9 2.9 1-2.9 1L19 21.8l-1-2.9-2.9-1 2.9-1L19 14z"></path>' +
    '</svg>',
};

Plugin(defaultOptions);

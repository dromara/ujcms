/**
 * 键盘快捷键的通用判定（docs/spec/console-shell D31）。构建器（views/design/builder/shortcuts.ts）与外壳的命令面板共用。
 */
export const isMac = typeof navigator !== 'undefined' && /Mac|iP(hone|ad|od)/.test(navigator.userAgent);

/** 焦点在可编辑控件（输入框/文本域/下拉/contenteditable）时不劫持按键。 */
export const isEditableTarget = (target: EventTarget | null): boolean => {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';
};

/** Element Plus 弹层（对话框/抽屉/消息框，lock-scroll 默认开）打开中：useLockscreen 给 body 加的类。 */
export const modalOpen = (): boolean => document.body.classList.contains('el-popup-parent--hidden');

/** TinyMCE 对话框（图片、链接……）打开中：它不经 EP 弹层，须单独判定。 */
export const tinymceDialogOpen = (): boolean => document.querySelector('.tox-dialog') != null;

/** 命令面板快捷键提示（符号即通用，不入 i18n）。 */
export const paletteHint = isMac ? '⌘K' : 'Ctrl K';

/** ⌘K（macOS）/ Ctrl+K：打开命令面板。 */
export const isPaletteShortcut = (e: KeyboardEvent): boolean =>
  typeof e.key === 'string' && e.key.toLowerCase() === 'k' && (isMac ? e.metaKey : e.ctrlKey) && !e.altKey && !e.shiftKey;

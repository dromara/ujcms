/**
 * 构建器键盘快捷键（visual-builder shell-polish）：撤销/重做/删除。控制台文档与预览 iframe（桥转发
 * `key` 消息）共用同一动作枚举；判定与守卫在此，分发在 BuilderPage.runShortcut。
 */
import { isMac, isEditableTarget, modalOpen } from '@/utils/keyboard';

export type ShortcutAction = 'undo' | 'redo' | 'delete';

// 判定与守卫已上提到 src/utils/keyboard.ts（外壳的命令面板共用）；此处转出口，构建器的引用不变
export { isMac, isEditableTarget, modalOpen };

/** 顶栏 tooltip 里的快捷键提示（符号即通用，不入 i18n）。 */
export const undoHint = isMac ? '⌘Z' : 'Ctrl+Z';
export const redoHint = isMac ? '⇧⌘Z' : 'Ctrl+Y';

/** 键盘事件 → 动作；非快捷键返回 null。修饰键按平台：macOS 取 ⌘，其余取 Ctrl。 */
export const shortcutOf = (e: KeyboardEvent): ShortcutAction | null => {
  if (typeof e.key !== 'string') return null;
  const mod = isMac ? e.metaKey : e.ctrlKey;
  const key = e.key.toLowerCase();
  if (mod && !e.altKey && key === 'z') return e.shiftKey ? 'redo' : 'undo';
  if (!isMac && e.ctrlKey && !e.altKey && !e.shiftKey && key === 'y') return 'redo';
  if (!e.metaKey && !e.ctrlKey && !e.altKey && !e.shiftKey && (e.key === 'Delete' || e.key === 'Backspace')) return 'delete';
  return null;
};

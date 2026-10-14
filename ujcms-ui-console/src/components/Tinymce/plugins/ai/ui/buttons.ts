import { Editor } from 'tinymce';
import { REWRITE_MODES, modeText, openRewriteDialog } from '../core/rewrite';
import type { RewriteMode } from '../core/rewrite';

// 快捷工具栏（选中文字时出现）只放选区模式：续写忽略选区、追加到正文末尾，放在选区工具栏上会误导
const SELECTION_MODES = REWRITE_MODES.filter((mode) => mode.value !== 'continue');

const addSplitButton = (editor: Editor, name: string, defaultOptions: any, modes: RewriteMode[]): void => {
  const items = modes.map((mode) => ({ type: 'choiceitem', value: mode.value, text: modeText(mode) }));

  const run = (value: string) => {
    const mode = modes.find((it) => it.value === value);
    if (mode != null) {
      openRewriteDialog(editor, mode);
    }
  };

  editor.ui.registry.addSplitButton(name, {
    icon: defaultOptions.id,
    tooltip: defaultOptions.tooltip,
    fetch: (callback: any) => callback(items),
    onAction: () => run(items[0]?.value ?? ''),
    onItemAction: (_api: any, value: string) => run(value),
  });
};

/**
 * AI 助手拆分按钮：主工具栏 `ai`（润色/扩写/校对/续写）与快捷工具栏 `aiselection`（润色/扩写/校对）。
 * `ai_writing_assist` 为 true 时注册，否则均不出现。
 * 风险筛查不在此处：表单侧的「AI 筛查」按钮是唯一入口（两种编辑器均可用），见 docs/spec/ai/editorial-assist.md §6.2
 */
const register = (editor: Editor, defaultOptions: any): void => {
  if (editor.getParam('ai_writing_assist') !== true) {
    return;
  }
  if (!editor.ui.registry.getAll().icons[defaultOptions.id]) {
    editor.ui.registry.addIcon(defaultOptions.id, defaultOptions.icon);
  }
  addSplitButton(editor, defaultOptions.id, defaultOptions, REWRITE_MODES);
  addSplitButton(editor, defaultOptions.selectionId, defaultOptions, SELECTION_MODES);
};

export { register };

import i18n from '@/i18n';

// 商业版 T2 的两个编辑器工具（一键排版、格式刷）：名称、图标，以及插件实现的装载与注册。
// 只由 Tinymce.vue 与 lockedButtons.ts 引入——name / tooltip 在模块加载时求值，必须留在按需加载的编辑器 chunk 里，
// 晚于 main.ts 的 loadLocaleMessages()。
// 实现在 plugins/typesetting、plugins/formatpainter 两个目录。社区版（开源版）源码不含这两个目录：本文件照常编译，
// 锁定按钮（lockedButtons.ts）照常取用这里的名称与图标。

export interface Tier2Tool {
  id: string;
  name: string;
  tooltip: string;
  icon: string;
}

// 两个图标均为本项目自绘（24 网格，矩形、斜条与四角星）。x > 14 且 y < 6 的右上角留空：锁定按钮在那里叠皇冠（lockedButtons.ts）。
export const typesetting: Tier2Tool = {
  id: 'typesetting',
  name: i18n.global.t('editor.typesetting'),
  tooltip: i18n.global.t('editor.typesetting.tooltip'),
  // 四行文字，右下一支魔棒，棒尖一颗星
  icon: '<svg viewBox="0 0 24 24" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M4 5h9v2H4zM4 9h9v2H4zM4 13h7v2H4zM4 17h5v2H4zM12.4 20.6L11 19.2l5-5 1.4 1.4zM17.5 6l1 2 2 1-2 1-1 2-1-2-2-1 2-1z" fill="currentColor"/></svg>',
};

export const formatpainter: Tier2Tool = {
  id: 'formatpainter',
  name: i18n.global.t('editor.formatpainter'),
  tooltip: i18n.global.t('editor.formatpainter.tooltip'),
  // 滚筒刷：滚筒、折向下方的刷柄、握把
  icon: '<svg viewBox="0 0 24 24" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M4 5h10v4H4zM14 6h5v6h-7v2h-2v-4h7V8h-3zM9 14h4v6H9z" fill="currentColor"/></svg>',
};

interface PluginModule {
  default: (options: Tier2Tool) => void;
}

// eager：编译成静态 import，与本文件同在一个 chunk，模块加载时同步注册，编辑器的初始化时序不变；目录不存在时匹配不到文件，得到空对象。
// 不要把这个调用直接包进 Object.keys(...)：那样 Vite 只保留键，不再引入模块。
const implementations: Record<string, PluginModule | undefined> = import.meta.glob<PluginModule>('./plugins/{typesetting,formatpainter}/plugin.ts', { eager: true });

const registered = new Set<string>();
for (const tool of [typesetting, formatpainter]) {
  const implementation = implementations[`./plugins/${tool.id}/plugin.ts`];
  if (implementation != null) {
    implementation.default(tool);
    registered.add(tool.id);
  }
}

/** 本次构建是否带有该工具的插件实现；用社区版（开源版）源码构建时恒为 false */
export const hasTier2Plugin = (id: string): boolean => registered.has(id);

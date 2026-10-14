import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import type { Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { tags as t } from '@lezer/highlight';

// 模板工作台编辑器亮色主题（GitHub Primer 系配色）。basicSetup 内置的 defaultHighlightStyle
// 是 fallback 高亮，此 HighlightStyle 一经注入即胜出；FTL 叠加身份色在 freemarker.ts，与此协调。
const highlight = HighlightStyle.define([
  { tag: t.comment, color: '#6e7781', fontStyle: 'italic' },
  { tag: t.tagName, color: '#116329' },
  { tag: t.attributeName, color: '#0550ae' },
  { tag: [t.string, t.attributeValue], color: '#0a3069' },
  { tag: [t.keyword, t.operatorKeyword, t.modifier], color: '#cf222e' },
  { tag: [t.number, t.bool, t.null, t.atom], color: '#0550ae' },
  { tag: [t.function(t.variableName), t.function(t.propertyName)], color: '#8250df' },
  { tag: t.propertyName, color: '#0550ae' },
  { tag: [t.className, t.definition(t.variableName)], color: '#953800' },
  { tag: [t.regexp, t.special(t.string)], color: '#116329' },
  { tag: [t.meta, t.processingInstruction, t.documentMeta], color: '#6e7781' },
  { tag: t.heading, color: '#0550ae', fontWeight: 'bold' },
  { tag: t.link, color: '#0a3069', textDecoration: 'underline' },
  { tag: t.invalid, color: '#cf222e' },
]);

// 编辑器外观：等宽字体栈（默认 monospace 在中文 Windows 常映射为宋体系，观感差）、
// 行号弱化、活动行浅灰、与控制台浅色界面同系
const chrome = EditorView.theme({
  '&': { fontSize: '13px', backgroundColor: '#ffffff', color: '#1f2328' },
  '.cm-scroller': { fontFamily: 'ui-monospace, "SF Mono", Menlo, "Cascadia Mono", Consolas, "Liberation Mono", monospace', lineHeight: '1.6' },
  '.cm-gutters': { backgroundColor: '#fafbfc', color: '#8c959f', borderRight: '1px solid #eaeef2' },
  '.cm-activeLineGutter': { backgroundColor: '#eaeef2', color: '#57606a' },
  '.cm-activeLine': { backgroundColor: 'rgba(234, 238, 242, 0.45)' },
});

export const editorTheme: Extension[] = [chrome, syntaxHighlighting(highlight)];

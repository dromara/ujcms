import { StateField, type EditorState, type Extension, type Range } from '@codemirror/state';
import { Decoration, EditorView, type DecorationSet } from '@codemirror/view';

// FreeMarker 叠加高亮（template-manager P3）：在 lang-html 之上以装饰标记 FTL 构件。
// CodeMirror 6 无官方/可靠社区 FreeMarker 语法包；模板体量为 KB 级，全文重扫成本可忽略，
// 且天然支持跨行注释/指令——不引入语法编译工具链（operator-surface-simplicity）。
// 单趟扫描按序识别：注释 → 指令 → ${…} 插值，互不重叠。
// 同时支持两种标签语法（FreeMarker 按模板自动识别，默认主题用方括号）：
// 尖括号 <#…>/</#…>/<@…>/<#-- --> 与方括号 [#…]/[/#…]/[@…]/[#-- --]。

const COMMENT = Decoration.mark({ class: 'cm-ftl-comment' });
const DIRECTIVE = Decoration.mark({ class: 'cm-ftl-directive' });
const INTERPOLATION = Decoration.mark({ class: 'cm-ftl-interpolation' });

// 起点匹配：注释开头须先于指令开头（<#-- 也能被 <# 命中，[#-- 同理）
const START_RE = /<#--|\[#--|<\/?[#@]|\[\/?[#@]|\$\{/g;

// 跳过字符串字面量（from 位于引号处），返回收尾引号下标；支持反斜杠转义
const skipString = (text: string, from: number): number => {
  const quote = text[from];
  for (let i = from + 1; i < text.length; i += 1) {
    if (text[i] === '\\') {
      i += 1;
    } else if (text[i] === quote) {
      return i;
    }
  }
  return text.length;
};

// ${…}：追踪花括号嵌套并跳过字符串，返回收尾 } 之后的下标
const scanInterpolation = (text: string, from: number): number => {
  let depth = 1;
  for (let i = from; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '"' || ch === "'") {
      i = skipString(text, i);
    } else if (ch === '{') {
      depth += 1;
    } else if (ch === '}') {
      depth -= 1;
      if (depth === 0) {
        return i + 1;
      }
    }
  }
  return text.length;
};

// 尖括号指令：扫到未被引号包裹的 > 为止（参数表达式内的 gt 比较符极少直接写 >，宽松处理够用）
const scanAngleTag = (text: string, from: number): number => {
  for (let i = from; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '"' || ch === "'") {
      i = skipString(text, i);
    } else if (ch === '>') {
      return i + 1;
    }
  }
  return text.length;
};

// 方括号指令：表达式内可再现方括号（下标/切片 a[0]、[0..2]），须按嵌套深度收尾；from 位于起始 [ 之后
const scanBracketTag = (text: string, from: number): number => {
  let depth = 1;
  for (let i = from; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '"' || ch === "'") {
      i = skipString(text, i);
    } else if (ch === '[') {
      depth += 1;
    } else if (ch === ']') {
      depth -= 1;
      if (depth === 0) {
        return i + 1;
      }
    }
  }
  return text.length;
};

const buildDecorations = (state: EditorState): DecorationSet => {
  const text = state.doc.toString();
  const ranges: Range<Decoration>[] = [];
  START_RE.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = START_RE.exec(text)) != null) {
    const start = match.index;
    const token = match[0];
    let end: number;
    if (token === '<#--' || token === '[#--') {
      const closer = token === '<#--' ? '-->' : '--]';
      const close = text.indexOf(closer, start + 4);
      end = close < 0 ? text.length : close + 3;
      ranges.push(COMMENT.range(start, end));
    } else if (token === '${') {
      end = scanInterpolation(text, start + 2);
      ranges.push(INTERPOLATION.range(start, end));
    } else if (token.startsWith('[')) {
      end = scanBracketTag(text, start + token.length);
      ranges.push(DIRECTIVE.range(start, end));
    } else {
      end = scanAngleTag(text, start + token.length);
      ranges.push(DIRECTIVE.range(start, end));
    }
    START_RE.lastIndex = end;
  }
  return Decoration.set(ranges);
};

const field = StateField.define<DecorationSet>({
  create: buildDecorations,
  update: (deco, tr) => (tr.docChanged ? buildDecorations(tr.state) : deco),
  provide: (f) => EditorView.decorations.from(f),
});

// !important：同一文本段可能同时带 HTML 语法高亮类，FTL 身份色需要稳定胜出；色值与 editorTheme.ts 同系
const theme = EditorView.baseTheme({
  '.cm-ftl-comment': { color: '#6e7781 !important', fontStyle: 'italic' },
  '.cm-ftl-directive': { color: '#8250df !important', backgroundColor: 'rgba(130, 80, 223, 0.06)' },
  '.cm-ftl-interpolation': { color: '#0d9488 !important', backgroundColor: 'rgba(13, 148, 136, 0.07)' },
});

export const freemarker = (): Extension => [field, theme];

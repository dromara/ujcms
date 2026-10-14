import DiffMatchPatch from 'diff-match-patch';

// diff-match-patch 的操作码（包未导出常量的类型声明，此处按其约定自行定义）
const DIFF_DELETE = -1;
const DIFF_INSERT = 1;
const DIFF_EQUAL = 0;

type Diff = [number, string];

/**
 * 解析 HTML 片段为可操作的 DOM，并剔除预览中不该出现的内容：脚本类元素与 on* 事件属性。
 * 送入模型的正文已经过服务端净化，此处只是对模型输出的兜底；应用到正文时 TinyMCE 与保存时的服务端还会再净化
 */
export const parseHtml = (html: string): HTMLElement => {
  const body = new DOMParser().parseFromString(html, 'text/html').body;
  body.querySelectorAll('script,style,iframe,object,embed').forEach((el) => el.remove());
  body.querySelectorAll('*').forEach((el) => {
    Array.from(el.attributes).forEach((attr) => {
      if (/^on/i.test(attr.name)) {
        el.removeAttribute(attr.name);
      }
    });
  });
  return body;
};

export const sanitizeHtml = (html: string): string => parseHtml(html).innerHTML;

export const htmlToText = (html: string): string => parseHtml(html).textContent ?? '';

// 参与对比的文本节点：跳过块级标签之间的纯空白节点，避免模型输出与编辑器序列化的缩进差异被当作改动
const textNodesOf = (root: HTMLElement): Text[] => {
  const nodes: Text[] = [];
  const walker = (root.ownerDocument ?? document).createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let node = walker.nextNode();
  while (node != null) {
    if ((node as Text).data.trim() !== '') {
      nodes.push(node as Text);
    }
    node = walker.nextNode();
  }
  return nodes;
};

/**
 * 按 diff 结果在 DOM 中包裹某一侧的改动：sideOp 为删除时给原文包 <del>，为新增时给建议包 <ins>。
 * 游标沿文本节点顺序推进，与 textNodesOf 拼出的对比文本一一对应；跨节点的改动逐节点包裹
 */
const markSide = (root: HTMLElement, diffs: Diff[], sideOp: number, tag: string): void => {
  const doc = root.ownerDocument ?? document;
  const queue = textNodesOf(root);
  let offset = 0;
  const consume = (length: number, mark: boolean) => {
    let remaining = length;
    while (remaining > 0) {
      const node = queue[0];
      if (node == null) {
        break;
      }
      const available = node.data.length - offset;
      if (available <= 0) {
        queue.shift();
        offset = 0;
        continue;
      }
      const count = Math.min(remaining, available);
      if (mark) {
        // 切出 [offset, offset+count) 单独包裹，剩余部分作为新的当前节点继续
        const target = offset > 0 ? node.splitText(offset) : node;
        const rest = target.data.length > count ? target.splitText(count) : null;
        const wrapper = doc.createElement(tag);
        target.parentNode?.insertBefore(wrapper, target);
        wrapper.appendChild(target);
        queue.shift();
        offset = 0;
        if (rest != null) {
          queue.unshift(rest);
        }
      } else {
        offset += count;
      }
      remaining -= count;
    }
  };
  diffs.forEach(([op, text]) => {
    if (op === DIFF_EQUAL) {
      consume(text.length, false);
    } else if (op === sideOp) {
      consume(text.length, true);
    }
  });
};

export interface DiffPreview {
  original: string;
  suggestion: string;
  /** 可见文字是否有任何差异；为 false 时两侧没有标注，应用也不会改变正文的文字 */
  changed: boolean;
}

/**
 * 原文与建议按字符级对比（diff-match-patch，带语义清理，对中文友好），分别在两侧 DOM 上标注删除（<del>）
 * 与新增（<ins>），返回可直接预览的两段 HTML。标注只用于展示，应用到正文时使用未标注的建议
 */
export const annotateDiff = (originalHtml: string, suggestionHtml: string): DiffPreview => {
  const original = parseHtml(originalHtml);
  const suggestion = parseHtml(suggestionHtml);
  const originalText = textNodesOf(original)
    .map((node) => node.data)
    .join('');
  const suggestionText = textNodesOf(suggestion)
    .map((node) => node.data)
    .join('');
  const dmp = new DiffMatchPatch();
  // 长文的最优对比可能很慢，超时后退回粗粒度结果
  dmp.Diff_Timeout = 2;
  const diffs = dmp.diff_main(originalText, suggestionText);
  dmp.diff_cleanupSemantic(diffs);
  markSide(original, diffs, DIFF_DELETE, 'del');
  markSide(suggestion, diffs, DIFF_INSERT, 'ins');
  const changed = diffs.some(([op]) => op !== DIFF_EQUAL);
  return { original: original.innerHTML, suggestion: suggestion.innerHTML, changed };
};

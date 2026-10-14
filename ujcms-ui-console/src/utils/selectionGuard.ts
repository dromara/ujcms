/**
 * 多击选区防护。
 *
 * 浏览器对双击、三击的默认行为是按“词/段落”扩展选区。若点击处没有文字（如列表中的空单元格、
 * 空白区域、空文本框），Chromium 会向后一直扩展到下一个文字边界，甚至到文档末尾，
 * 形成一段只含空白的“隐形”选区。此后追加到 body 末尾的对话框正好落在这段选区之内，
 * 打开时整个对话框的标题、标签、输入值全部高亮（典型场景：双击列表行的空单元格打开编辑对话框）。
 *
 * 点击处没有文字就没有可选的词，因此在第二次及之后的 mousedown 上取消默认行为即可：
 * 第一次点击已完成聚焦与光标定位，取消后续点击的默认行为不影响聚焦、输入，
 * 也不影响 click/dblclick 事件的派发（如表格的 row-dblclick）。
 * 点击处有文字时保持浏览器原生的双击选词行为。
 */
const TEXT_INPUT_TYPES = new Set(['text', 'search', 'url', 'tel', 'email', 'password', 'number']);

const isTextControl = (target: Element): target is HTMLInputElement | HTMLTextAreaElement =>
  target instanceof HTMLTextAreaElement || (target instanceof HTMLInputElement && TEXT_INPUT_TYPES.has(target.type));

/** 点击坐标处是否落在文字节点上。无法判断时按“有文字”处理，保持原生行为。 */
const isPointOnText = (x: number, y: number): boolean => {
  const doc = document as Document & {
    caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node } | null;
    caretRangeFromPoint?: (x: number, y: number) => Range | null;
  };
  if (typeof doc.caretPositionFromPoint === 'function') {
    const position = doc.caretPositionFromPoint(x, y);
    return position == null || position.offsetNode.nodeType === Node.TEXT_NODE;
  }
  if (typeof doc.caretRangeFromPoint === 'function') {
    const range = doc.caretRangeFromPoint(x, y);
    return range == null || range.startContainer.nodeType === Node.TEXT_NODE;
  }
  return true;
};

export const installSelectionGuard = (): void => {
  document.addEventListener(
    'mousedown',
    (event: MouseEvent) => {
      // detail 为连击次数：1 单击、2 双击、3 三击；只处理鼠标左键
      if (event.detail < 2 || event.button !== 0) return;
      const target = event.target;
      if (!(target instanceof Element)) return;
      if (isTextControl(target)) {
        // 文本框内部为 shadow DOM，无法按坐标判断，改按内容是否为空判断
        if (target.value === '') event.preventDefault();
        return;
      }
      // 富文本等可编辑区域由编辑器自行处理选区
      if ((target as HTMLElement).isContentEditable) return;
      if (!isPointOnText(event.clientX, event.clientY)) event.preventDefault();
    },
    true,
  );
};

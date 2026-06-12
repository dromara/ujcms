import { Editor } from 'tinymce';

const doAction = (editor: Editor, defaultOptions: any): void => {
  editor.formatter.toggle(defaultOptions.id);
  editor.nodeChanged();
  // const { dom, selection } = editor;
  // const blocks = selection.getSelectedBlocks();
  // const styleName = 'text-indent';
  // let textIndentExists: boolean;
  // tinymce.each(blocks, (block: Element) => {
  //   const parents = dom.getParents(block, undefined, dom.getRoot());
  //   const parent = parents[parents.length - 1];
  //   if (!['p', 'div'].includes(parent.nodeName.toLowerCase())) {
  //     return;
  //   }
  //   if (textIndentExists === undefined) {
  //     // 使用 parseInt 可以将 0em 或 0px 转换成 0
  //     textIndentExists = parseInt(dom.getStyle(parent, styleName)) > 0;
  //   }
  //   dom.setStyle(parent, styleName, textIndentExists ? '' : '2em');
  // });
};

export { doAction };

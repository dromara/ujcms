import type { BytemdPlugin } from 'bytemd';
import type { HLJSApi } from 'highlight.js';

let hljs: HLJSApi | undefined;

/**
 * 代码高亮插件，替代 @bytemd/plugin-highlight：上游插件 import('highlight.js') 会把全部 190+ 种语言（约 950 kB）
 * 打进一个 chunk，这里改用 highlight.js/lib/common（36 种常用语言，约 1/8 体积）。与上游一致，只作用于预览区 DOM
 * （viewerEffect），预览区出现代码块时才按需加载；不经过 remark/rehype 管线，落库的 HTML 不受影响。
 */
export default function highlight(): BytemdPlugin {
  return {
    viewerEffect({ markdownBody }) {
      const els = markdownBody.querySelectorAll<HTMLElement>('pre>code');
      if (els.length === 0) return;
      (async () => {
        hljs ??= (await import('highlight.js/lib/common')).default;
        els.forEach((el) => hljs!.highlightElement(el));
      })();
    },
  };
}

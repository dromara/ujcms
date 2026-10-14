// 纯文本文件的读写：词库与栏目大纲的导入、导出共用（浏览器内读成文本、由浏览器写出，不经服务端）

/** 文本文件解码：UTF-8（含 BOM）优先；不是合法 UTF-8 时按 GB18030（Windows 记事本、Excel 另存的 ANSI 文件）读取 */
export const decodeText = (buffer: ArrayBuffer): string => {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(buffer);
  } catch {
    return new TextDecoder('gb18030').decode(buffer);
  }
};

/** 带 BOM 的 UTF-8 文本文件：旧版记事本与 Excel 靠 BOM 识别编码 */
export const toTextBlob = (text: string): Blob => new Blob([`\uFEFF${text}`], { type: 'text/plain;charset=utf-8' });

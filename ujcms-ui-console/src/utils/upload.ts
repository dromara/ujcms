/**
 * 校验文件扩展名是否在 accept 允许范围内（accept 形如 ".mp3,.ogg,.wav"）。
 *
 * 用于在 before-upload 阶段拦截拖拽进来的不支持文件：el-upload 的 accept 属性仅过滤
 * “选择文件”对话框，并不限制拖拽上传，因此拖入不支持的文件会被直接提交到后端并返回 400。
 *
 * 当无法按扩展名判断时（accept 为空，或仅包含通配符/MIME 类型）返回 true，交由后端兜底校验。
 *
 * @param fileName 文件名
 * @param accept   accept 字符串（逗号分隔，扩展名以 "." 开头）
 * @returns 是否允许
 */
export const isAcceptedExtension = (fileName: string, accept?: string | null): boolean => {
  if (!accept) {
    return true;
  }
  const extTokens = accept
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter((s) => s.startsWith('.'));
  if (extTokens.length === 0) {
    return true;
  }
  const name = (fileName || '').toLowerCase();
  const dot = name.lastIndexOf('.');
  const ext = dot >= 0 ? name.slice(dot) : '';
  return extTokens.includes(ext);
};

/**
 * 将 accept 字符串转为友好提示文案，例如 ".mp3,.ogg,.wav" → "mp3, ogg, wav"。
 */
export const acceptToText = (accept?: string | null): string =>
  (accept ?? '')
    .split(',')
    .map((s) => s.trim().replace(/^\./, ''))
    .filter(Boolean)
    .join(', ');

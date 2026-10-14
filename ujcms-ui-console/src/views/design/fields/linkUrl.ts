/**
 * 链接字段（link）地址规则——与服务端 LinkFieldType.LINK_URL_PATTERN 同口径：
 * http(s):// 绝对地址、/ 开头站内地址（含 // 协议相对）、# 页内锚点、mailto: / tel:。
 * 白名单之外（javascript: 等脚本协议、裸域名、相对路径）服务端一律 400，这里提前在表单内提示。
 */
export const LINK_URL_PATTERN = /^(https?:\/\/|\/|#|mailto:|tel:)/i;

export const isValidLinkUrl = (url: unknown): boolean => typeof url === 'string' && LINK_URL_PATTERN.test(url.trim());

// 裸域名（www.example.com、example.com/path、含端口）——失焦时自动补 https://，免得操作者再手敲协议。
// 单点形态（about.html）可能是相对文件路径而非域名：末段是网页文件扩展名时不补，留给红字提示。
const BARE_DOMAIN_PATTERN = /^([a-z0-9-]+(?:\.[a-z0-9-]+)+)(:\d+)?([/?#].*)?$/i;
const PAGE_FILE_EXTENSIONS = new Set(['html', 'htm', 'shtml', 'xhtml', 'php', 'jsp', 'jspx', 'asp', 'aspx', 'do', 'action', 'xml', 'json', 'txt', 'pdf', 'js', 'css']);

const looksLikeBareDomain = (value: string): boolean => {
  const match = BARE_DOMAIN_PATTERN.exec(value);
  if (match == null) return false;
  const labels = (match[1] ?? '').toLowerCase().split('.');
  if (labels[0] === 'www' || labels.length > 2) return true;
  return !PAGE_FILE_EXTENSIONS.has(labels[labels.length - 1] ?? '');
};

/**
 * 失焦归一：剥首尾空白；裸域名补 https://。其余原样返回（合法与否交由提示与服务端）。
 */
export const normalizeLinkUrl = (url: string): string => {
  const trimmed = url.trim();
  if (trimmed !== '' && !LINK_URL_PATTERN.test(trimmed) && looksLikeBareDomain(trimmed)) {
    return `https://${trimmed}`;
  }
  return trimmed;
};

export type LinkProblem = { path: string; kind: 'empty' | 'invalid' };

/**
 * 链接对象的问题：有值却缺 url（服务端「url 不能为空」），或 url/mobileUrl 不在白名单内。
 */
export const linkProblems = (value: unknown, path: string): LinkProblem[] => {
  if (value == null || typeof value !== 'object') return [];
  const link = value as Record<string, any>;
  const result: LinkProblem[] = [];
  const url = typeof link.url === 'string' ? link.url.trim() : '';
  if (url === '') {
    result.push({ path: `${path}.url`, kind: 'empty' });
  } else if (!isValidLinkUrl(url)) {
    result.push({ path: `${path}.url`, kind: 'invalid' });
  }
  const mobileUrl = typeof link.mobileUrl === 'string' ? link.mobileUrl.trim() : '';
  if (mobileUrl !== '' && !isValidLinkUrl(mobileUrl)) {
    result.push({ path: `${path}.mobileUrl`, kind: 'invalid' });
  }
  return result;
};

/**
 * 递归收集一组字段值里的链接问题（link 直接检查；list/object 递归），路径形如
 * {@code items.0.link.url}。供设置表单提交前拦截——服务端 RegionService/ThemeSettingsService
 * 同样会拒绝，这里只是把 400 提前成表单内提示。
 */
export const collectLinkProblems = (defs: Record<string, any>, values: Record<string, any> | null | undefined, prefix = ''): LinkProblem[] => {
  const result: LinkProblem[] = [];
  if (values == null) return result;
  Object.entries(defs ?? {}).forEach(([name, def]) => {
    const value = values[name];
    if (value == null || value === '') return;
    const path = prefix === '' ? name : `${prefix}.${name}`;
    switch (def?.type) {
      case 'link':
        result.push(...linkProblems(value, path));
        break;
      case 'object':
        if (typeof value === 'object') result.push(...collectLinkProblems(def.fields ?? {}, value, path));
        break;
      case 'list':
        if (Array.isArray(value)) {
          value.forEach((item, index) => {
            result.push(...collectLinkProblems({ [String(index)]: def.of ?? {} }, { [String(index)]: item }, path));
          });
        }
        break;
      default:
        break;
    }
  });
  return result;
};

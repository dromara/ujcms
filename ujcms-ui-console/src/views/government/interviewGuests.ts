/**
 * 访谈嘉宾字段的文本格式：`姓名（职务）、姓名（职务）`。
 *
 * 数据库列与前台模板直接使用这段文本（前台原样显示），控制台只在两处解析它：
 * 编辑表单的分行录入组件（拆成姓名/职务两列，保存时重新拼接）与“管理”工作台的发言人下拉选项。
 * 拆分只在括号深度为 0 时进行，因此职务中可以出现顿号（如“局长、党组书记”）。
 */
export interface InterviewGuest {
  name: string;
  title: string;
}

const SEPARATORS = new Set(['、', '，', ',', ';', '；', '\n']);
const OPENERS = new Set(['（', '(']);
const CLOSERS = new Set(['）', ')']);

export const splitGuestEntries = (text: string): string[] => {
  const entries: string[] = [];
  let depth = 0;
  let current = '';
  for (const ch of text) {
    if (OPENERS.has(ch)) depth += 1;
    else if (CLOSERS.has(ch)) depth = Math.max(0, depth - 1);
    if (depth === 0 && SEPARATORS.has(ch)) {
      entries.push(current);
      current = '';
      continue;
    }
    current += ch;
  }
  entries.push(current);
  return entries.map((entry) => entry.trim()).filter(Boolean);
};

export const parseGuests = (text?: string | null): InterviewGuest[] =>
  splitGuestEntries(text ?? '').map((entry) => {
    // 最后一对括号内为职务；括号可嵌套（“局长（兼）”），故内层用贪婪匹配
    const matched = /^(.*?)\s*[（(](.*)[）)]\s*$/.exec(entry);
    const name = matched?.[1]?.trim();
    return name ? { name, title: (matched?.[2] ?? '').trim() } : { name: entry, title: '' };
  });

export const formatGuests = (guests: InterviewGuest[]): string =>
  guests
    .map((guest) => ({ name: guest.name.trim(), title: guest.title.trim() }))
    .filter((guest) => guest.name)
    .map((guest) => (guest.title ? `${guest.name}（${guest.title}）` : guest.name))
    .join('、');

/** “管理”工作台发言人候选项：主持人与嘉宾，职务仅作提示 */
export interface SpeakerOption {
  value: string;
  role: string;
  kind: 'host' | 'guest';
}

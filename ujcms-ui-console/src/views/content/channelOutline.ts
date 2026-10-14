// 栏目大纲的解析与导出格式（docs/spec/channel-management/plan.md D3、D5）。两者同处一地：导出的文件须能原样用于批量新增。
// 文本文件，每行一个栏目：行首缩进表示层级，名称之后可跟别名（“名称<TAB>别名”或“名称 | 别名”）。
// 其余设置（模型、模板、权限等）不在文件里：批量新增时沿用所选的上级栏目

export interface OutlineItem {
  name: string;
  slug?: string;
  // 上级在 items 中的序号；-1 表示最上层（建在对话框所选的上级栏目之下）
  parent: number;
  // 层级，从 0 起
  level: number;
  // 所在行号，从 1 起
  line: number;
}

export type OutlineErrorType = 'indent' | 'name' | 'slug' | 'slugDuplicated' | 'slugExist';

export interface OutlineError {
  line: number;
  type: OutlineErrorType;
}

export interface ParsedOutline {
  items: OutlineItem[];
  errors: OutlineError[];
  // 层数（最深层级 + 1）
  depth: number;
}

/** 名称、别名的最大长度（与服务端一致） */
const NAME_MAX_LENGTH = 50;
const SLUG_MAX_LENGTH = 50;
const SLUG_PATTERN = /^[\w-]*$/;
// 名称与别名的分隔符：有 Tab（从表格复制的两列、本系统导出的文件）就按 Tab 切，否则按竖线；因此导出文件里含竖线的名称不会被误切
const PIPES = /[|｜]/;
// 从文档里复制的列表常带项目符号
const BULLET = /^[-*•·]\s+/;

/** 行首缩进：Tab 的个数，以及空格的宽度（全角空格按两个算） */
const measureIndent = (line: string): { tabs: number; spaces: number; length: number } => {
  let tabs = 0;
  let spaces = 0;
  let length = 0;
  for (const char of line) {
    if (char === '\t') {
      tabs += 1;
    } else if (char === ' ' || char === ' ') {
      spaces += 1;
    } else if (char === '　') {
      spaces += 2;
    } else {
      break;
    }
    length += char.length;
  }
  return { tabs, spaces, length };
};

/**
 * 解析栏目大纲。
 *
 * 层级 = Tab 个数 + 空格宽度 / 一级的空格宽度，后者取文本中出现过的最小空格缩进；整段文本共有的缩进先行扣除。
 * 一行最多比上一行深一级。有问题的行仍留在结果里（预览照常显示），是否可提交由调用方看 errors 是否为空。
 *
 * @param existingSlugs 本站已用的别名（小写）
 */
export const parseChannelOutline = (text: string, existingSlugs: Set<string>): ParsedOutline => {
  const rows: { line: number; tabs: number; spaces: number; content: string }[] = [];
  text.split(/\r\n|\r|\n/).forEach((line, index) => {
    if (line.trim() === '') {
      return;
    }
    const indent = measureIndent(line);
    rows.push({ line: index + 1, tabs: indent.tabs, spaces: indent.spaces, content: line.slice(indent.length).trim().replace(BULLET, '') });
  });
  const items: OutlineItem[] = [];
  const errors: OutlineError[] = [];
  if (rows.length === 0) {
    return { items, errors, depth: 0 };
  }
  const baseTabs = Math.min(...rows.map((row) => row.tabs));
  const baseSpaces = Math.min(...rows.map((row) => row.spaces));
  const spaceIndents = rows.map((row) => row.spaces - baseSpaces).filter((spaces) => spaces > 0);
  const unit = spaceIndents.length > 0 ? Math.min(...spaceIndents) : 1;

  const slugs = new Set<string>();
  // 各层级最近一项在 items 中的序号
  const lastAtLevel: number[] = [];
  let prevLevel = -1;
  let depth = 0;
  rows.forEach((row) => {
    let level = row.tabs - baseTabs + Math.floor((row.spaces - baseSpaces) / unit);
    if (level > prevLevel + 1) {
      errors.push({ line: row.line, type: 'indent' });
      level = prevLevel + 1;
    }
    const tab = row.content.indexOf('\t');
    const parts = tab >= 0 ? row.content.split('\t') : row.content.split(PIPES);
    const name = parts[0].trim();
    const slug = (parts[1] ?? '').trim();
    if (name === '' || name.length > NAME_MAX_LENGTH) {
      errors.push({ line: row.line, type: 'name' });
    }
    if (slug !== '') {
      const key = slug.toLowerCase();
      if (!SLUG_PATTERN.test(slug) || slug.length > SLUG_MAX_LENGTH) {
        errors.push({ line: row.line, type: 'slug' });
      } else if (slugs.has(key)) {
        errors.push({ line: row.line, type: 'slugDuplicated' });
      } else if (existingSlugs.has(key)) {
        errors.push({ line: row.line, type: 'slugExist' });
      }
      slugs.add(key);
    }
    items.push({ name, slug: slug !== '' ? slug : undefined, parent: level === 0 ? -1 : lastAtLevel[level - 1], level, line: row.line });
    lastAtLevel[level] = items.length - 1;
    prevLevel = level;
    depth = Math.max(depth, level + 1);
  });
  return { items, errors, depth };
};

/** 大纲项转成树（预览用）：节点 id 为其在 items 中的序号 */
export const outlineToTree = (items: OutlineItem[]): any[] => {
  const nodes = items.map((item, index) => ({ id: index, name: item.name, slug: item.slug, children: [] as any[] }));
  const root: any[] = [];
  items.forEach((item, index) => {
    (item.parent < 0 ? root : nodes[item.parent].children).push(nodes[index]);
  });
  return root;
};

/** 栏目树导出为大纲文本：Tab 缩进，“名称<TAB>别名”，CRLF 换行。树节点取 name、slug、children */
export const serializeChannelOutline = (tree: any[]): string => {
  const lines: string[] = [];
  const walk = (nodes: any[], level: number) => {
    nodes.forEach((node) => {
      lines.push(`${'\t'.repeat(level)}${node.name}${node.slug ? `\t${node.slug}` : ''}`);
      walk(node.children ?? [], level + 1);
    });
  };
  walk(tree, 0);
  return lines.join('\r\n');
};

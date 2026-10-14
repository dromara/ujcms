// 设计›模板 主题根条目角色徽标（template-manager P2/P3 共用：浏览表格与工作台文件树）。
// 结构化布局四目录是 liquid-retirement 后的唯一约定；数据目录为主题数据（页面清单/种子/形状 JSON
// 及形状缩略图）；theme.manifest.json 是主题清单锚文件；开发目录/文件仅去强调标注，不限制操作。
export const STRUCTURED_DIRS = ['layout', 'templates', 'snippets', 'assets'] as const;
export const DATA_DIRS = ['pages', 'seed', 'shapes', 'thumbnails'] as const;
export const DEV_DIRS = ['_src', 'node_modules', '_files'] as const;
export const MANIFEST_FILE = 'theme.manifest.json';
export const DEV_FILES = ['package.json', 'package-lock.json'] as const;
// 主题根 preview.<图片扩展名> 按命名约定标注为预览图（权威指向是 theme.manifest.json 的 preview
// 字段，但徽标不值得逐行解析清单；改名不随之丢标注是可接受的失真）
const PREVIEW_FILE_RE = /^preview\.(svg|png|jpe?g|gif|webp)$/i;

export interface RootBadge {
  /** 徽标文案的 i18n key */
  key: string;
  /** 开发目录/文件去强调（降低不透明度） */
  dev: boolean;
}

// 仅当 row 是主题根（root=/{siteId}/{theme}）直下条目时返回名称；root 为空（原始目录模式）不标注
const themeRootChildName = (row: any, root: string): string | undefined => {
  if (!root || root === '/') {
    return undefined;
  }
  const name = String(row.name ?? '');
  return String(row.id) === `${root}/${name}` ? name : undefined;
};

export const rootBadge = (row: any, root: string): RootBadge | undefined => {
  const name = themeRootChildName(row, root);
  if (name == null) {
    return undefined;
  }
  if (row.fileType === 'DIRECTORY') {
    if (([...STRUCTURED_DIRS, ...DATA_DIRS] as readonly string[]).includes(name)) {
      return { key: `webFile.template.dir.${name}`, dev: false };
    }
    return (DEV_DIRS as readonly string[]).includes(name) ? { key: 'webFile.template.dir.dev', dev: true } : undefined;
  }
  if (name === MANIFEST_FILE) {
    return { key: 'webFile.template.file.manifest', dev: false };
  }
  if (PREVIEW_FILE_RE.test(name)) {
    return { key: 'webFile.template.file.preview', dev: false };
  }
  return (DEV_FILES as readonly string[]).includes(name) ? { key: 'webFile.template.file.dev', dev: true } : undefined;
};

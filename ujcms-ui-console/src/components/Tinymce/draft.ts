/**
 * 富文本的本地草稿（TinyMCE autosave 插件）。
 *
 * 插件默认按“页面地址 + 编辑器 ID”存放，而编辑器 ID 每次挂载都是随机的：存下的草稿再也取不回来，只在 localStorage
 * 里越积越多。现在只有给了 draftKey 的编辑器才启用草稿（目前是文章正文），键由调用方给出，稳定可重现：
 * 同一个人在同一站点打开同一篇文章，工具栏的“恢复草稿”就能取回上次没保存的内容。
 */
const DRAFT_PREFIX = 'ujcms-draft-';
/** 旧版留下的、已无法取回的草稿 */
const LEGACY_PREFIX = 'tinymce-autosave-';

/** 草稿的键：用户 + 站点 + 数据种类 + 数据 ID（新增时为 new） */
export const editorDraftKey = (username: string | undefined, siteId: string | null | undefined, kind: string, id?: string | number | null): string =>
  `${username ?? '_'}-${siteId ?? '_'}-${kind}-${id ?? 'new'}`;

/** 交给 autosave_prefix 的前缀：插件在其后拼上 draft / time */
export const editorDraftPrefix = (key: string): string => `${DRAFT_PREFIX}${key}-`;

const removeByPrefix = (prefixes: string[]) => {
  try {
    const keys: string[] = [];
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i);
      if (key != null && prefixes.some((prefix) => key.startsWith(prefix))) {
        keys.push(key);
      }
    }
    keys.forEach((key) => localStorage.removeItem(key));
  } catch {
    // 无痕窗口、存储被禁用：没有草稿可清
  }
};

/**
 * 正在使用草稿的编辑器。清除草稿时要把编辑器的“有改动”标记一并清掉：插件每 15 秒、以及页面卸载之前，
 * 只要编辑器还标着有改动就再存一次——只删 localStorage 里的键，刚删掉的草稿转眼又被写了回来
 * （保存之后、确认放弃之后、退出登录刷新页面之前都是如此）。这里不引用 tinymce 本身，免得把编辑器拖进主包
 */
interface DraftEditor {
  key: () => string | null | undefined;
  clean: () => void;
}
const draftEditors = new Set<DraftEditor>();

/** 编辑器登记自己的草稿键与“清掉改动标记”的动作；返回注销函数 */
export const registerDraftEditor = (key: DraftEditor['key'], clean: DraftEditor['clean']): (() => void) => {
  const entry = { key, clean };
  draftEditors.add(entry);
  return () => draftEditors.delete(entry);
};

const cleanEditors = (match: (key: string | null | undefined) => boolean) => {
  draftEditors.forEach((entry) => {
    try {
      if (match(entry.key())) {
        entry.clean();
      }
    } catch {
      // 编辑器已销毁：没有标记可清
    }
  });
};

/** 清除一份草稿：保存成功、确认放弃修改之后调用 */
export const removeEditorDraft = (key: string): void => {
  cleanEditors((current) => current === key);
  removeByPrefix([editorDraftPrefix(key)]);
};

/** 清除全部草稿：退出登录时调用——草稿是正文内容，不留给下一个用这台电脑的人 */
export const removeAllEditorDrafts = (): void => {
  cleanEditors(() => true);
  removeByPrefix([DRAFT_PREFIX, LEGACY_PREFIX]);
};

/** 清除旧版留下的草稿 */
export const removeLegacyEditorDrafts = (): void => removeByPrefix([LEGACY_PREFIX]);

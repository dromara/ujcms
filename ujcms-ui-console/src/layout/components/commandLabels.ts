/**
 * 命令面板的“另一种语言”文案（docs/spec/console-shell D31）。i18n 只装载当前语言（i18n.ts），
 * 面板首次打开时按需装载另一种语言的 menu / config / system / census 四个 json（索引只用 menu.* / config.* / site.*，
 * 站点基础设置的标签在 system.json、网站普查的在 census.json；文件很小、独立 chunk），
 * 使中文界面能搜 watermark、英文界面能搜 水印。scripts/check-settings-fields.mjs 的 F2 按同一组文件校验标签键。
 */
const bundles = import.meta.glob<{ default: Record<string, string> }>('../../locales/*/{menu,config,system,census}.json');

let loaded: Promise<Record<string, string>> | undefined;

export const loadOtherLocaleLabels = (current: string): Promise<Record<string, string>> => {
  loaded ??= (async () => {
    const merged: Record<string, string> = {};
    for (const [path, load] of Object.entries(bundles)) {
      if (path.includes(`/locales/${current}/`)) continue;
      Object.assign(merged, (await load()).default);
    }
    return merged;
  })();
  return loaded;
};

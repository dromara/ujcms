import { h } from 'vue';
import type { ActivateTarget, SeedImportFailure, SeedPlan, SeedRunResult, ThemeInfo, ThemePageInfo } from '@/api/theme';
import { previewToken, queryPreviewUrls } from '@/api/composition';
import { newTabPreviewBase } from '@/composables/usePreviewBridge';
import { localizedLabel } from '@/i18n';

// 主题画廊 / 详情共用的展示辅助（theme-marketplace A4）

export const localized = localizedLabel;

/**
 * 缺省激活槽位（theme-marketplace §3 R8）：只剩一个空槽位就取它；两槽位都空时——站点两槽位现用同一主题
 * （未单独用手机主题）则整站切换（both），站点用着独立手机主题则只换桌面、尊重手机侧的选择。
 * 画廊激活对话框与详情页影响预演同口径。
 */
export const defaultActivateTarget = (theme: ThemeInfo, themes: ThemeInfo[]): ActivateTarget => {
  if (theme.active) return 'mobile';
  if (theme.activeMobile) return 'desktop';
  const sameThemeInBothSlots = themes.some((item) => item.active && item.activeMobile);
  return sameThemeInBothSlots ? 'both' : 'desktop';
};

export const SEED_CATEGORIES = ['channels', 'articles', 'topics', 'blocks', 'votes', 'surveys', 'forms', 'linkGroups', 'models', 'dicts', 'panels', 'regions'] as const;
export type SeedCategoryKey = (typeof SEED_CATEGORIES)[number];

/** SeedPlan 单行摘要：新增 / 沿用既有 / 已存在（含已改动、种子有更新）。全 0 类别省略。 */
export const seedPlanSummary = (plan: SeedPlan, t: (key: string, args?: any) => string): string =>
  SEED_CATEGORIES.map((key) => {
    const c = plan[key];
    const parts: string[] = [];
    if (c.new > 0) parts.push(`+${c.new}`);
    if (c.linked > 0) parts.push(t('theme.seed.plan.linkedN', { count: c.linked }));
    if (c.existing > 0) parts.push(t('theme.seed.plan.existingN', { count: c.existing }));
    return parts.length ? `${t(`theme.seed.cat.${key}`)} ${parts.join('/')}` : '';
  })
    .filter(Boolean)
    .join('，');

/** 种子差异按字段跨类别求和（更新报告摘要行） */
export const seedDiffTotal = (plan: SeedPlan, field: 'new' | 'seedChanged' | 'operatorModified' | 'existing'): number =>
  SEED_CATEGORIES.reduce((sum, key) => sum + plan[key][field], 0);

/**
 * 失败明细单行文案（theme-seed-export §8.1 / D22）：原因码按「译文 ≠ 键」映射 zh-CN
 * （语言包为带点扁平键，te() 按层级查找恒 false——不可用），未登记的码回退英文详情原文。
 */
const seedFailureLine = (failure: SeedImportFailure, t: (key: string, args?: any) => string): string => {
  const catKey = `theme.seed.cat.${failure.category}`;
  const catTranslated = t(catKey);
  const category = catTranslated !== catKey ? catTranslated : failure.category;
  const reasonKey = `theme.seed.failure.${failure.reason}`;
  const reasonTranslated = t(reasonKey, { detail: failure.detail });
  const reason = reasonTranslated !== reasonKey ? reasonTranslated : failure.detail;
  return `${category} ${failure.ref}：${reason}`;
};

/**
 * 导入失败明细的常驻警示通知（安装/激活/更新/手动导入共用）：逐行列出类别、引用与可操作的
 * 原因文案（如专题模型缺失 → 建模后重新导入即补齐）。无失败则无事发生。
 */
export const notifySeedFailures = (
  run: SeedRunResult | null | undefined,
  t: (key: string, args?: any) => string,
): void => {
  // 内容 + 构成两半的失败都走 R2 通道（构成半：引用令牌不可解析的整区域跳过/刷新面板跳过）
  const failures = [...(run?.content?.failures ?? []), ...(run?.composition?.failures ?? [])];
  if (!failures.length) return;
  ElNotification({
    title: t('theme.seed.failure.title'),
    type: 'warning',
    duration: 0,
    message: h(
      'div',
      failures.map((failure) => h('div', { class: 'mb-1' }, seedFailureLine(failure, t))),
    ),
  });
};

/**
 * 种子运行结果的单行可读摘要（详情页导入/激活/更新的成功提示、安装向导完成页共用）：
 * 逐类别列出 +新增/更新/沿用/失败计数，构成半列出面板与区域；全无变化时给「无变化」文案。
 */
export const seedRunSummary = (run: SeedRunResult, t: (key: string, args?: any) => string): string => {
  const c = run.content;
  const p = run.composition;
  const content = SEED_RUN_CATEGORIES.map((key) => {
    const item = c[key];
    const parts: string[] = [];
    if (item.imported > 0) parts.push(`+${item.imported}`);
    if (item.updated > 0) parts.push(t('theme.seed.run.updatedN', { count: item.updated }));
    if (item.linked > 0) parts.push(t('theme.seed.plan.linkedN', { count: item.linked }));
    if (item.failed > 0) parts.push(t('theme.seed.run.failedN', { count: item.failed }));
    return parts.length ? `${t(`theme.seed.cat.${key}`)} ${parts.join('/')}` : '';
  }).filter(Boolean);
  const compositionParts: string[] = [];
  if (p.panelsImported + p.panelsUpdated > 0) {
    compositionParts.push(`${t('theme.seed.cat.panels')} +${p.panelsImported}${p.panelsUpdated > 0 ? '/' + t('theme.seed.run.updatedN', { count: p.panelsUpdated }) : ''}`);
  }
  if (p.panelsRepaired > 0) {
    compositionParts.push(`${t('theme.seed.cat.panels')} ${t('theme.seed.run.repairedN', { count: p.panelsRepaired })}`);
  }
  if (p.regionsImported + p.regionsReset > 0) {
    const parts: string[] = [];
    if (p.regionsImported > 0) parts.push(`+${p.regionsImported}`);
    if (p.regionsReset > 0) parts.push(t('theme.seed.run.resetN', { count: p.regionsReset }));
    compositionParts.push(`${t('theme.seed.cat.regions')} ${parts.join('/')}`);
  }
  const all = [...content, ...compositionParts];
  return all.length ? all.join(t('punct.enum')) : t('theme.seed.run.noChange');
};

const SEED_RUN_CATEGORIES = ['channels', 'articles', 'topics', 'blocks', 'votes', 'surveys', 'forms', 'linkGroups', 'models', 'dicts', 'blockItems', 'linkItems'] as const;

/** 画廊/详情截图地址：{templatesUrl}/{siteId}/{themeName}/{相对路径}（模板存储对外公开，与 {{theme}} 同源） */
export const themeAssetUrl = (
  templatesUrl: string | undefined,
  siteId: string | number | undefined,
  themeName: string,
  relPath: string | undefined,
): string | undefined => {
  if (!templatesUrl || siteId == null || !relPath) return undefined;
  return `${templatesUrl}/${siteId}/${encodeURIComponent(themeName)}/${relPath}`;
};

export const themePreviewUrl = (
  templatesUrl: string | undefined,
  siteId: string | number | undefined,
  theme: ThemeInfo,
): string | undefined => themeAssetUrl(templatesUrl, siteId, theme.themeName, theme.metadata?.preview);

// ----- 页面级渲染试看（preview-url-resolver P3）：详情「页面与区域」页签 / 画廊预览下拉共用 -----

/** 站点部署路径（site.dy，部署路径 + 子目录）与固定路由拼接。解析端点回的候选地址已含前缀，勿重复拼。 */
export const joinSitePath = (dy: string | undefined, route: string): string => {
  const base = dy && dy !== '/' ? dy.replace(/\/+$/, '') : '';
  const path = route.startsWith('/') ? route : `/${route}`;
  return `${base}${path}` || '/';
};

/**
 * 解析主题页面的代表性预览路径：清单 route（候选主题自身权威，参数化路由除外）→ 解析端点首个候选
 * （当前站点作用域；实体地址与主题无关，活动主题的绑定口径即为代表）→ null（调用方回退站点首页）。
 */
export const resolveThemePagePath = async (page: ThemePageInfo, siteDy: string | undefined): Promise<string | null> => {
  if (page.route && !page.route.includes('[')) {
    return joinSitePath(siteDy, page.route);
  }
  const result = await queryPreviewUrls({ page: page.name, limit: 1 });
  return result.candidates[0]?.url ?? null;
};

/**
 * 以 ?_theme= 候选主题渲染试看打开路径（P2-1 同机制：身份即凭据，前台注入的 preview-context.js
 * 负责横幅、同源链接/表单/子请求携带参数；令牌 5 分钟过期后导航 401，重开即可）。新标签页整站浏览，
 * 开发期直接开后端源（newTabPreviewBase）而非 iframe 用的同源代理前缀。令牌签发失败时抛出，调用方提示。
 */
export const openThemePreviewAt = async (themeName: string, path: string): Promise<void> => {
  const minted = await previewToken();
  const base = `${newTabPreviewBase}${path || '/'}`;
  const sep = base.includes('?') ? '&' : '?';
  window.open(`${base}${sep}_theme=${encodeURIComponent(themeName)}&_ptoken=${encodeURIComponent(minted.token)}`, '_blank');
};

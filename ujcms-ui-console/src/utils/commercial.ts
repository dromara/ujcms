import { ElMessageBox } from 'element-plus';
import i18n from '@/i18n';
import { currentUser } from '@/stores/useCurrentUser';

// 商业版功能的版本说明：CommercialNotice、锁定操作的悬停提示、没有独立页面的锁定功能弹窗共用同一套文案——
// 总是写明所需版本（商业版 · 名称）与当前版本，并指向版本与价格页。界面只用版本名称：T1–T4 是内部代号，价格页上没有
export const PRICING_URL = 'https://www.ujcms.com/price/';

/** 皇冠图形（viewBox 0 0 24 24，颜色取 currentColor）：CommercialBadge 与编辑器锁定按钮（Tinymce/lockedButtons.ts）共用 */
export const CROWN_SVG_SHAPES =
  '<path d="M3.2 8.4 7.7 12 12 5.6l4.3 6.4 4.5-3.6-1.6 9.1H4.8z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />' +
  '<circle cx="3" cy="7.6" r="1.7" /><circle cx="12" cy="4.4" r="1.7" /><circle cx="21" cy="7.6" r="1.7" />' +
  '<rect x="4.8" y="19" width="14.4" height="2.2" rx="1.1" />';

const t = (key: string, params?: Record<string, unknown>): string => (params ? i18n.global.t(key, params) : i18n.global.t(key));

/** 所需版本名称，如“商业版 · 标准版”；级别未知时为“商业版” */
export const tierLabel = (tier?: number): string => (tier ? t(`commercial.tier.${tier}`) : t('commercial.edition.commercial'));

/** 当前运行的版本名称 */
export const currentEditionLabel = (): string => (currentUser.commercialTier > 0 ? t(`commercial.tier.${currentUser.commercialTier}`) : t('commercial.edition.community'));

/** 锁定操作（按钮、下拉项、表单校验）的提示：级别未达到时为“需要商业版 · 名称”，已达到为 undefined（:title 不渲染） */
export const lockedTip = (tier: number): string | undefined => (currentUser.commercialTier < tier ? t('commercial.requires', { tier: tierLabel(tier) }) : undefined);

/**
 * 没有独立介绍页的锁定功能（Word 导入、编辑器工具栏的 T2 工具）：弹窗说明所需版本，确认即打开版本与价格页。title 为功能或工具名。
 * 层级固定为 2000：编辑器全屏（.tox-fullscreen = 1200）时也要盖住，而控制台 EP 基础层级为 500；
 * 遮罩层的 z-index 是行内样式，靠 Tailwind important 模式的 !important 压过
 */
export const showCommercialNotice = (tier: number, title: string): void => {
  ElMessageBox.confirm(t('commercial.notice.upgrade', { current: currentEditionLabel(), tier: tierLabel(tier) }), title, {
    confirmButtonText: t('commercial.notice.pricing'),
    cancelButtonText: t('close'),
    modalClass: 'z-[2000]',
  })
    .then(() => window.open(PRICING_URL, '_blank', 'noopener'))
    .catch(() => {});
};

import { currentUser, hasPermission, isShowTier } from '@/stores/useCurrentUser';

/**
 * 全局设置 / 站点设置的左侧页签描述符（docs/spec/console-shell S5）：两个设置视图与命令面板共用同一来源，
 * 视图据此决定显示哪些页签，面板据此索引“站点设置 › 水印设置”这类深链（?type=）以及页签内的每个设置项
 * （fields：标签键，即 label-tip 的 message / el-form-item 的 :label，按页面顺序；面板按两种语言的标签文案匹配，
 * 提示文案（.tooltip）作低优先级匹配，回车打开页签并滚动到该项——settingsFocus.ts）。
 * fields 与视图实际渲染的表单项由 scripts/check-settings-fields.mjs 双向校验（挂在 lint / build 链上）。
 * 顺序即左侧导航顺序（console-menu「In-page navigation」），data.ts 权限树的对应节点与此同序。
 */
export interface SettingsTab {
  /** 具体设置页，即 ?type= 与权限码中的类型 */
  type: string;
  /** 左侧导航项（四个存储点共用 storage，页内以分段按钮切换） */
  nav: string;
  perm: string;
  /** 级别区块：isShowTier（社区版展示推广、商业版按已购级别） */
  tier?: number;
  /** 额外可见条件 */
  visible?: () => boolean;
  /** 页签内设置项的标签键，按页面顺序；动态表单（自定义）为空 */
  fields: readonly string[];
}

// 四个存储点共用一张表单（GlobalSettings.vue 的 STORAGE_TYPES 区块），字段随存储点类型显示
const STORAGE_FIELDS: readonly string[] = [
  'config.storage.type',
  'config.storage.path',
  'config.storage.url',
  'config.storage.hostname',
  'config.storage.port',
  'config.storage.username',
  'config.storage.password',
  'config.storage.encoding',
  'config.storage.passive',
  'config.storage.encryption',
  'config.storage.endpoint',
  'config.storage.region',
  'config.storage.bucket',
  'config.storage.accessKey',
  'config.storage.secretKey',
];

// 部署（基础、上传、存储）→ 账号（注册、安全、验证码、第三方登录）→ 外部服务（短信、邮件、AI服务商）→ 一键置灰 → 自定义
export const GLOBAL_SETTINGS_TABS: SettingsTab[] = [
  {
    type: 'base',
    nav: 'base',
    perm: 'config:base:update',
    fields: [
      'config.port',
      'config.contextPath',
      'config.channelUrl',
      'config.articleUrl',
      'config.defaultSite',
      'config.multiDomain',
      'config.console.sidebarTheme',
      'config.console.themeColor',
    ],
  },
  {
    type: 'upload',
    nav: 'upload',
    perm: 'config:upload:update',
    fields: [
      'config.upload.imageTypes',
      'config.upload.imageLimit',
      'config.upload.videoTypes',
      'config.upload.videoLimit',
      'config.upload.audioTypes',
      'config.upload.audioLimit',
      'config.upload.libraryTypes',
      'config.upload.libraryLimit',
      'config.upload.docTypes',
      'config.upload.docLimit',
      'config.upload.fileTypes',
      'config.upload.fileLimit',
      'config.upload.imageMaxWidth',
      'config.upload.imageMaxHeight',
    ],
  },
  { type: 'uploadStorage', nav: 'storage', perm: 'config:uploadStorage:update', fields: STORAGE_FIELDS },
  { type: 'htmlStorage', nav: 'storage', perm: 'config:htmlStorage:update', fields: STORAGE_FIELDS },
  { type: 'templateStorage', nav: 'storage', perm: 'config:templateStorage:update', fields: STORAGE_FIELDS },
  { type: 'packageStorage', nav: 'storage', perm: 'config:packageStorage:update', fields: STORAGE_FIELDS },
  {
    type: 'register',
    nav: 'register',
    perm: 'config:register:update',
    fields: [
      'config.register.enabled',
      'config.register.verifyMode',
      'config.register.usernameMinLength',
      'config.register.usernameMaxLength',
      'config.register.usernameRegex',
      'config.register.smallAvatarSize',
      'config.register.mediumAvatarSize',
      'config.register.largeAvatarSize',
      'config.register.avatar',
    ],
  },
  {
    type: 'security',
    nav: 'security',
    perm: 'config:security:update',
    tier: 1,
    fields: [
      'config.security.passwordStrength',
      'config.security.passwordMinLength',
      'config.security.passwordMaxLength',
      'config.security.userMaxAttempts',
      'config.security.userLockMinutes',
      'config.security.ipCaptchaAttempts',
      'config.security.ipMaxAttempts',
      'config.security.forceChangePasswordOnFirstLogin',
      'config.security.passwordMinDays',
      'config.security.passwordMaxDays',
      'config.security.passwordWarnDays',
      'config.security.passwordMaxHistory',
      'config.security.twoFactor',
      'config.security.ssrfWhiteList',
    ],
  },
  {
    type: 'captcha',
    nav: 'captcha',
    perm: 'config:captcha:update',
    fields: [
      'config.captcha.type',
      'config.captcha.silentSubmits',
      'config.captcha.powDifficulty',
      'config.captcha.geetest.captchaId',
      'config.captcha.geetest.captchaKey',
      'config.captcha.tencent.appId',
      'config.captcha.tencent.appSecretKey',
      'config.captcha.tencent.secretId',
      'config.captcha.tencent.secretKey',
      'config.captcha.aliyun.accessKeyId',
      'config.captcha.aliyun.accessKeySecret',
      'config.captcha.aliyun.sceneId',
      'config.captcha.aliyun.prefix',
      'config.captcha.aliyun.region',
      'config.captcha.yidun.captchaId',
      'config.captcha.yidun.secretId',
      'config.captcha.yidun.secretKey',
      'config.captcha.turnstile.siteKey',
      'config.captcha.turnstile.secretKey',
    ],
  },
  {
    type: 'oauth',
    nav: 'oauth',
    perm: 'config:oauth:show',
    tier: 2,
    fields: [
      'config.oauth.weixin.enabled',
      'config.oauth.weixin.clientId',
      'config.oauth.weixin.clientSecret',
      'config.oauth.weixin.callbackUrl',
      'config.oauth.qq.enabled',
      'config.oauth.qq.clientId',
      'config.oauth.qq.clientSecret',
      'config.oauth.qq.callbackUrl',
      'config.oauth.weibo.enabled',
      'config.oauth.weibo.clientId',
      'config.oauth.weibo.clientSecret',
      'config.oauth.weibo.callbackUrl',
    ],
  },
  {
    type: 'sms',
    nav: 'sms',
    perm: 'config:sms:update',
    fields: [
      'config.sms.provider',
      'config.sms.maxPerIp',
      'config.sms.codeLength',
      'config.sms.codeExpires',
      'config.sms.accessKeyId',
      'config.sms.accessKeySecret',
      'config.sms.templateCode',
      'config.sms.codeName',
      'config.sms.secretId',
      'config.sms.secretKey',
      'config.sms.templateId',
      'config.sms.sdkAppId',
      'config.sms.region',
      'config.sms.signName',
      'config.sms.testMobile',
    ],
  },
  {
    type: 'email',
    nav: 'email',
    perm: 'config:email:update',
    fields: [
      'config.email.host',
      'config.email.port',
      'config.email.username',
      'config.email.password',
      'config.email.from',
      'config.email.timeout',
      'config.email.ssl',
      'config.email.maxPerIp',
      'config.email.codeLength',
      'config.email.codeExpires',
      'config.email.subject',
      'config.email.text',
      'config.email.testTo',
    ],
  },
  {
    type: 'ai',
    nav: 'ai',
    perm: 'config:ai:show',
    tier: 2,
    fields: [
      'config.ai.enabled',
      'config.ai.providers',
      'config.ai.defaultProvider',
      'config.ai.writingProvider',
      'config.ai.moderationProvider',
      'config.ai.logRetentionDays',
      'config.ai.dailyTokenLimit',
      'config.ai.translationProvider',
      'config.ai.mt.baiduAppId',
      'config.ai.mt.baiduSecret',
      'config.ai.mt.youdaoAppKey',
      'config.ai.mt.youdaoSecret',
      'config.ai.mt.deeplAuthKey',
    ],
  },
  // 一键置灰（console-menu D23）：T2 起，社区版不展示（不是推广区块）
  { type: 'grey', nav: 'grey', perm: 'config:grey:show', visible: () => currentUser.commercialTier >= 2, fields: ['config.grey.enabled', 'config.grey.greyDates'] },
  { type: 'customs', nav: 'customs', perm: 'config:customs:update', fields: [] },
];

// 站点（基础、固定链接、静态化、GEO）→ 内容（水印、编辑器、AI功能）→ 互动（留言、评论）→ 政务（依申请公开 … 无障碍）→ 自定义
export const SITE_SETTINGS_TABS: SettingsTab[] = [
  {
    type: 'base',
    nav: 'base',
    perm: 'siteSettings:base:update',
    fields: [
      'site.name',
      'site.protocol',
      'site.domain',
      'site.subDir',
      'site.lang',
      'site.model',
      'site.theme',
      'site.mobileTheme',
      'site.logo',
      'site.seoTitle',
      'site.seoKeywords',
      'site.seoDescription',
      'site.pageSize',
      'site.status',
    ],
  },
  { type: 'permalink', nav: 'permalink', perm: 'siteSettings:permalink:update', fields: ['site.permalink.article', 'site.permalink.pinyin'] },
  { type: 'html', nav: 'html', perm: 'siteSettings:html:update', fields: ['site.html.enabled', 'site.html.auto', 'site.html.channel', 'site.html.article', 'site.html.listPages'] },
  {
    type: 'geo',
    nav: 'geo',
    perm: 'siteSettings:geo:update',
    fields: [
      'site.geo.jsonLdEnabled',
      'site.geo.llmsTxtEnabled',
      'site.geo.llmsTxtArticleCount',
      'site.geo.llmsTxtChannelIds',
      'site.geo.robotsEnabled',
      'site.geo.disallowedAiAgents',
      'site.geo.robotsCustom',
      'site.geo.preview',
      'site.geo.baiduPushEnabled',
      'site.geo.baiduPushUrl',
      'site.geo.push.status',
      'site.geo.indexNowEnabled',
      'site.geo.indexNowKey',
    ],
  },
  {
    type: 'watermark',
    nav: 'watermark',
    perm: 'siteSettings:watermark:update',
    fields: ['site.watermark.enabled', 'site.watermark.overlay', 'site.watermark.position', 'site.watermark.dissolve', 'site.watermark.minWidth', 'site.watermark.minHeight'],
  },
  {
    type: 'editor',
    nav: 'editor',
    perm: 'siteSettings:editor:update',
    tier: 2,
    fields: [
      'site.editor.typesetting.fontFamily',
      'site.editor.typesetting.fontSize',
      'site.editor.typesetting.lineHeight',
      'site.editor.typesetting.emptyLine',
      'site.editor.typesetting.textIndent',
      'site.editor.typesetting.imageCenterAlign',
      'site.editor.typesetting.tableFullWidth',
      'site.editor.typesetting.halfWidthCharConversion',
    ],
  },
  {
    type: 'ai',
    nav: 'ai',
    perm: 'siteSettings:ai:update',
    tier: 2,
    fields: [
      'site.ai.seoAssist',
      'site.ai.writingAssist',
      'site.ai.screening',
      'site.ai.translate',
      'site.ai.relatedAssist',
      'site.ai.dailyTokenQuota',
      'site.ai.moderationMode',
      'site.ai.replyDraft',
      'site.ai.autoApproveBelow',
      'site.ai.autoBlockAbove',
    ],
  },
  { type: 'messageBoard', nav: 'messageBoard', perm: 'siteSettings:messageBoard:update', fields: ['site.messageBoard.enabled', 'site.messageBoard.loginRequired'] },
  {
    type: 'comment',
    nav: 'comment',
    perm: 'siteSettings:comment:update',
    fields: ['site.comment.enabled', 'site.comment.loginRequired', 'site.comment.auditRequired', 'site.comment.imageEnabled', 'site.comment.imageMaxCount'],
  },
  { type: 'disclosure', nav: 'disclosure', perm: 'siteSettings:disclosure:update', tier: 4, fields: ['site.disclosure.enabled', 'site.disclosure.loginRequired'] },
  {
    type: 'mailbox',
    nav: 'mailbox',
    perm: 'siteSettings:mailbox:update',
    tier: 4,
    fields: [
      'site.mailbox.enabled',
      'site.mailbox.loginRequired',
      'site.mailbox.handlingDays',
      'site.mailbox.extendedDays',
      'site.mailbox.smsEnabled',
      'site.mailbox.smsTemplateCode',
      'site.mailbox.smsCodeName',
    ],
  },
  { type: 'interview', nav: 'interview', perm: 'siteSettings:interview:update', tier: 4, fields: ['site.interview.enabled', 'site.interview.loginRequired'] },
  {
    type: 'census',
    nav: 'census',
    perm: 'siteSettings:census:update',
    tier: 4,
    fields: [
      'site.census.newsChannelIds',
      'site.census.policyChannelIds',
      'site.census.excludedChannelIds',
      'site.census.staleMonths',
      'site.census.linkCheckEnabled',
      'site.census.linkTimeoutMs',
      'site.census.maxUrls',
    ],
  },
  {
    type: 'accessibility',
    nav: 'accessibility',
    perm: 'siteSettings:accessibility:update',
    tier: 4,
    fields: ['site.accessibility.enabled', 'site.accessibility.entryButton', 'site.accessibility.defaultOpen', 'site.accessibility.helpUrl', 'site.accessibility.elderUrl'],
  },
  { type: 'customs', nav: 'customs', perm: 'siteSettings:customs:update', fields: [] },
];

export const isSettingsTabVisible = (tab: SettingsTab): boolean => hasPermission(tab.perm) && (tab.tier == null || isShowTier(tab.tier)) && (tab.visible?.() ?? true);

// 级别未达到时页签只展示推广区块（社区版 isShowTier 放行），表单项并不存在，不可索引
const isSettingsTabUnlocked = (tab: SettingsTab): boolean => tab.tier == null || currentUser.commercialTier >= tab.tier;

/** 左侧导航项的锁定级别：社区版里级别未达到的页签带商业版标识（页内只有 CommercialNotice）；已解锁或无级别为 undefined */
export const lockedSettingsTier = (tabs: SettingsTab[], nav: string): number | undefined =>
  tabs.find((tab) => tab.nav === nav && isSettingsTabVisible(tab) && !isSettingsTabUnlocked(tab))?.tier;

/** 当前用户可见的设置页类型，按导航顺序 */
export const visibleSettingsTypes = (tabs: SettingsTab[]): string[] => tabs.filter(isSettingsTabVisible).map((tab) => tab.type);

/** 每个导航项一条（storage 取首个可见存储点），供命令面板深链 ?type= 与索引页签内的设置项 */
export const visibleSettingsNavs = (tabs: SettingsTab[]): { nav: string; type: string; fields: readonly string[] }[] => {
  const seen = new Set<string>();
  return tabs
    .filter(isSettingsTabVisible)
    .filter((tab) => !seen.has(tab.nav) && seen.add(tab.nav))
    .map((tab) => ({ nav: tab.nav, type: tab.type, fields: isSettingsTabUnlocked(tab) ? tab.fields : [] }));
};

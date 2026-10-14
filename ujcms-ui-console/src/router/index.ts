import type { Component } from 'vue';
import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { Document, Setting, User, DataLine, Operation, Connection, Folder, Notebook, Brush, OfficeBuilding, ChatDotRound } from '@element-plus/icons-vue';
import Layout from '@/layout/index.vue';

declare module 'vue-router' {
  interface RouteMeta {
    hidden?: boolean;
    title?: string;
    icon?: Component;
    requiresPermission?: string;
    noCache?: boolean;
    // 承载视图内 UI 状态的 query 键（如 ThemeDetail 的 tab）：AppMain 计算视图重建键时排除之，
    // 视图改写这些 query 不会触发整页重建（闪烁 + 重复请求）；身份类 query（site/theme 等）不在此列
    stateQuery?: string[];
    // 隐藏路由没有自己的菜单项：侧栏代为高亮的菜单项，以路由名指定（控制台一律按路由名引用，不写 path）
    activeMenu?: string;
  }
}
export const routes: Array<RouteRecordRaw> = [
  { path: '/refresh', component: () => import('@/views/RefreshPage.vue'), meta: { hidden: true, noCache: true } },
  { path: '/login', component: () => import('@/views/LoginPage.vue'), meta: { hidden: true, noCache: true } },
  // 安装向导（install-flow D2）：backend-state.installed=false 时替代登录页（见 permission.ts）
  { path: '/install', component: () => import('@/views/InstallPage.vue'), meta: { hidden: true, noCache: true } },
  { path: '/404', component: () => import('@/views/404.vue'), meta: { hidden: true, noCache: true } },
  { path: '/403', component: () => import('@/views/403.vue'), meta: { hidden: true, noCache: true } },
  {
    // 可视化构建器（visual-builder §10）：全屏路由（不套 Layout），页面级作用域；
    // 原 /composition/region 独立区域编辑器已折叠为构建器内的结构视图（D7）
    path: '/design/builder',
    name: 'Builder',
    component: () => import('@/views/design/builder/BuilderPage.vue'),
    meta: { title: 'builder.title', requiresPermission: 'pageRegion:show', hidden: true, noCache: true },
  },
  {
    // 社区版预览模式下，被锁定的商业功能统一跳转到此介绍页（见 permission.ts 路由守卫）
    path: '/commercial-feature',
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: '',
        name: 'CommercialFeaturePage',
        component: () => import('@/views/CommercialFeaturePage.vue'),
        meta: { title: 'commercial.locked.title', requiresPermission: 'backend', noCache: true },
      },
    ],
  },
  {
    path: '',
    component: Layout,
    meta: { hidden: true },
    children: [{ path: '', name: 'HomePage', component: () => import('@/views/home/HomePage.vue'), meta: { title: 'menu.homepage', requiresPermission: 'backend' } }],
  },
  {
    // 个人项（console-menu R9 / D26 / D28）：只显示当前登录用户本人数据的视图没有侧栏菜单，入口在页头——
    // 我的私信是信封图标、我的通知是铃铛图标（各带未读角标），权限树节点归 个人设置
    path: '/personal',
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: 'message',
        name: 'MessageList',
        component: () => import('@/views/personal/MessageList.vue'),
        meta: { title: 'menu.personal.message', requiresPermission: 'message:page', hidden: true },
      },
      {
        path: 'notification',
        name: 'NotificationList',
        component: () => import('@/views/personal/NotificationList.vue'),
        meta: { title: 'menu.personal.notification', requiresPermission: 'notification:page', hidden: true, stateQuery: ['status'] },
      },
    ],
  },
  {
    path: '/content',
    component: Layout,
    meta: { title: 'menu.content', icon: Document, requiresPermission: 'menu.content' },
    children: [
      {
        path: 'article',
        name: 'ArticleList',
        component: () => import('@/views/content/ArticleList.vue'),
        meta: { title: 'menu.content.article', requiresPermission: 'article:page', stateQuery: ['channelId', 'status'] },
      },
      {
        path: 'article-review',
        name: 'ArticleReviewList',
        component: () => import('@/views/content/ArticleReviewList.vue'),
        meta: { title: 'menu.content.articleReview', requiresPermission: 'articleReview:page' },
      },
      {
        path: 'channel',
        name: 'ChannelList',
        component: () => import('@/views/content/ChannelList.vue'),
        meta: { title: 'menu.content.channel', requiresPermission: 'channel:page', stateQuery: ['channelId'] },
      },
      {
        path: 'topic',
        name: 'TopicList',
        component: () => import('@/views/content/TopicList.vue'),
        meta: { title: 'menu.content.topic', requiresPermission: 'topic:page', stateQuery: ['topicId', 'tab'] },
      },
      { path: 'tag', name: 'TagList', component: () => import('@/views/content/TagList.vue'), meta: { title: 'menu.content.tag', requiresPermission: 'tag:page' } },
      {
        path: 'block-item',
        name: 'BlockItemList',
        component: () => import('@/views/content/BlockItemList.vue'),
        meta: { title: 'menu.content.blockItem', requiresPermission: 'blockItem:page', stateQuery: ['blockId'] },
      },
      {
        path: 'link',
        name: 'LinkList',
        component: () => import('@/views/content/LinkList.vue'),
        meta: { title: 'menu.content.link', requiresPermission: 'link:page', stateQuery: ['groupId'] },
      },
      {
        path: 'attachment',
        name: 'AttachmentList',
        component: () => import('@/views/content/ResourceLibrary.vue'),
        meta: { title: 'menu.content.resourceLibrary', requiresPermission: 'attachment:page', stateQuery: ['tab', 'categoryId', 'myCategoryId'] },
      },
      {
        path: 'collection',
        name: 'CollectionList',
        component: () => import('@/views/content/CollectionList.vue'),
        meta: { title: 'menu.content.collection', requiresPermission: 'collection:page' },
      },
    ],
  },
  {
    path: '/interaction',
    component: Layout,
    meta: { title: 'menu.interaction', icon: Connection, requiresPermission: 'menu.interaction' },
    children: [
      {
        path: 'message-board',
        name: 'MessageBoardList',
        component: () => import('@/views/interaction/MessageBoardList.vue'),
        meta: { title: 'menu.interaction.messageBoard', requiresPermission: 'messageBoard:page', stateQuery: ['status', 'id'] },
      },
      {
        path: 'comment',
        name: 'CommentList',
        component: () => import('@/views/interaction/CommentList.vue'),
        meta: { title: 'menu.interaction.comment', requiresPermission: 'comment:page', stateQuery: ['status', 'id'] },
      },
      {
        path: 'ai-moderation',
        name: 'AiModerationList',
        component: () => import('@/views/interaction/AiModerationList.vue'),
        meta: { title: 'menu.interaction.aiModeration', requiresPermission: 'aiModeration:page' },
      },
      {
        path: 'form',
        name: 'FormList',
        component: () => import('@/views/interaction/FormList.vue'),
        meta: { title: 'menu.interaction.form', requiresPermission: 'form:page', stateQuery: ['typeId', 'status'] },
      },
      {
        path: 'form-review',
        name: 'FormReviewList',
        component: () => import('@/views/interaction/FormReviewList.vue'),
        meta: { title: 'menu.interaction.formReview', requiresPermission: 'formReview:page', stateQuery: ['typeId'] },
      },
      {
        path: 'vote',
        name: 'VoteList',
        component: () => import('@/views/interaction/VoteList.vue'),
        meta: { title: 'menu.interaction.vote', requiresPermission: 'vote:page' },
      },
      {
        path: 'survey',
        name: 'SurveyList',
        component: () => import('@/views/interaction/SurveyList.vue'),
        meta: { title: 'menu.interaction.survey', requiresPermission: 'survey:page' },
      },
      // {
      //   path: 'example',
      //   name: 'ExampleList',
      //   component: () => import('@/views/interaction/ExampleList.vue'),
      //   meta: { title: 'menu.interaction.example', requiresPermission: 'example:page' },
      // },
    ],
  },
  {
    // 政务（console-menu）：依申请公开 / 领导信箱 / 在线访谈 / 网站普查四项 T4 功能合为一组。
    // 组权限码沿用 menu.census（码与路径、标签无关，不迁移角色数据；同 /design 沿用 menu.composition 的先例）
    path: '/government',
    component: Layout,
    meta: { title: 'menu.government', icon: OfficeBuilding, requiresPermission: 'menu.census' },
    children: [
      {
        path: 'disclosure',
        name: 'DisclosureList',
        component: () => import('@/views/government/DisclosureList.vue'),
        meta: { title: 'menu.government.disclosure', requiresPermission: 'disclosure:page', stateQuery: ['status'] },
      },
      {
        path: 'mailbox',
        name: 'MailboxList',
        component: () => import('@/views/government/MailboxList.vue'),
        meta: { title: 'menu.government.mailbox', requiresPermission: 'mailbox:page', stateQuery: ['status'] },
      },
      {
        path: 'mailbox-stat',
        name: 'MailboxStat',
        component: () => import('@/views/government/MailboxStat.vue'),
        meta: { title: 'menu.government.mailboxStat', requiresPermission: 'mailboxStat:page' },
      },
      {
        path: 'interview',
        name: 'InterviewList',
        component: () => import('@/views/government/InterviewList.vue'),
        meta: { title: 'menu.government.interview', requiresPermission: 'interview:page', stateQuery: ['status'] },
      },
      {
        path: 'census',
        name: 'CensusList',
        component: () => import('@/views/government/CensusList.vue'),
        meta: { title: 'menu.government.census', requiresPermission: 'census:page', stateQuery: ['status'] },
      },
      {
        path: 'census-issue',
        name: 'CensusIssueList',
        component: () => import('@/views/government/CensusIssueList.vue'),
        meta: { title: 'menu.government.censusIssue', requiresPermission: 'censusIssue:page', stateQuery: ['censusId', 'status'] },
      },
    ],
  },
  {
    path: '/wechat',
    component: Layout,
    meta: { title: 'menu.wechat', icon: ChatDotRound, requiresPermission: 'menu.wechat' },
    children: [
      {
        path: 'wechat-account',
        name: 'WechatAccountList',
        component: () => import('@/views/wechat/WechatAccountList.vue'),
        meta: { title: 'menu.wechat.wechatAccount', requiresPermission: 'wechatAccount:page' },
      },
      {
        path: 'wechat-material',
        name: 'WechatMaterialList',
        component: () => import('@/views/wechat/WechatMaterialList.vue'),
        meta: { title: 'menu.wechat.wechatMaterial', requiresPermission: 'wechatMaterial:page', stateQuery: ['accountId', 'type'] },
      },
      {
        path: 'wechat-draft',
        name: 'WechatDraftList',
        component: () => import('@/views/wechat/WechatDraftList.vue'),
        meta: { title: 'menu.wechat.wechatDraft', requiresPermission: 'wechatDraft:page', stateQuery: ['accountId'] },
      },
      {
        path: 'wechat-publish',
        name: 'WechatPublishList',
        component: () => import('@/views/wechat/WechatPublishList.vue'),
        meta: { title: 'menu.wechat.wechatPublish', requiresPermission: 'wechatPublish:page', stateQuery: ['accountId'] },
      },
      {
        path: 'wechat-mass',
        name: 'WechatMassList',
        component: () => import('@/views/wechat/WechatMassList.vue'),
        meta: { title: 'menu.wechat.wechatMass', requiresPermission: 'wechatMass:page' },
      },
      {
        path: 'wechat-article',
        name: 'WechatArticleList',
        component: () => import('@/views/wechat/WechatArticleList.vue'),
        meta: { title: 'menu.wechat.wechatArticle', requiresPermission: 'wechatArticle:page' },
      },
      {
        path: 'wechat-menu',
        name: 'WechatMenuForm',
        component: () => import('@/views/wechat/WechatMenuForm.vue'),
        meta: { title: 'menu.wechat.wechatMenu', requiresPermission: 'wechatMenu:page', stateQuery: ['accountId'] },
      },
      {
        path: 'wechat-reply',
        name: 'WechatReplyList',
        component: () => import('@/views/wechat/WechatReplyList.vue'),
        meta: { title: 'menu.wechat.wechatReply', requiresPermission: 'wechatReply:page', stateQuery: ['accountId'] },
      },
      {
        path: 'wechat-message',
        name: 'WechatMessageList',
        component: () => import('@/views/wechat/WechatMessageList.vue'),
        meta: { title: 'menu.wechat.wechatMessage', requiresPermission: 'wechatMessage:page' },
      },
      {
        path: 'wechat-template',
        name: 'WechatTemplateList',
        component: () => import('@/views/wechat/WechatTemplateList.vue'),
        meta: { title: 'menu.wechat.wechatTemplate', requiresPermission: 'wechatTemplate:page', stateQuery: ['accountId', 'tab'] },
      },
      {
        path: 'wechat-fan',
        name: 'WechatFanList',
        component: () => import('@/views/wechat/WechatFanList.vue'),
        meta: { title: 'menu.wechat.wechatFan', requiresPermission: 'wechatFan:page', stateQuery: ['accountId', 'type'] },
      },
      {
        path: 'wechat-tag',
        name: 'WechatTagList',
        component: () => import('@/views/wechat/WechatTagList.vue'),
        meta: { title: 'menu.wechat.wechatTag', requiresPermission: 'wechatTag:page', stateQuery: ['accountId'] },
      },
      {
        path: 'wechat-qrcode',
        name: 'WechatQrcodeList',
        component: () => import('@/views/wechat/WechatQrcodeList.vue'),
        meta: { title: 'menu.wechat.wechatQrcode', requiresPermission: 'wechatQrcode:page' },
      },
      {
        path: 'wechat-stat',
        name: 'WechatStat',
        component: () => import('@/views/wechat/WechatStat.vue'),
        meta: { title: 'menu.wechat.wechatStat', requiresPermission: 'wechatStat:page', stateQuery: ['accountId', 'tab', 'days'] },
      },
    ],
  },
  {
    path: '/stat',
    component: Layout,
    meta: { title: 'menu.stat', icon: DataLine, requiresPermission: 'menu.stat' },
    children: [
      {
        path: 'visit',
        meta: { title: 'menu.stat.visit', requiresPermission: 'menu.stat.visit' },
        children: [
          {
            path: 'visit-trend',
            name: 'VisitTrend',
            component: () => import('@/views/stat/VisitTrend.vue'),
            meta: { title: 'menu.stat.visitTrend', requiresPermission: 'visitTrend:page', stateQuery: ['range'] },
          },
          {
            path: 'visited-page',
            name: 'VisitedPage',
            component: () => import('@/views/stat/VisitedPage.vue'),
            meta: { title: 'menu.stat.visitedPage', requiresPermission: 'visitedPage:page', stateQuery: ['range'] },
          },
          {
            path: 'entry-page',
            name: 'EntryPage',
            component: () => import('@/views/stat/EntryPage.vue'),
            meta: { title: 'menu.stat.entryPage', requiresPermission: 'entryPage:page', stateQuery: ['range'] },
          },
          {
            path: 'visit-source',
            name: 'VisitSource',
            component: () => import('@/views/stat/VisitSource.vue'),
            meta: { title: 'menu.stat.visitSource', requiresPermission: 'visitSource:page', stateQuery: ['range'] },
          },
        ],
      },
      {
        path: 'visitor',
        meta: { title: 'menu.stat.visitor', requiresPermission: 'menu.stat.visitor' },
        children: [
          {
            path: 'visit-visitor',
            name: 'VisitVisitor',
            component: () => import('@/views/stat/VisitVisitor.vue'),
            meta: { title: 'menu.stat.visitVisitor', requiresPermission: 'visitVisitor:page', stateQuery: ['range'] },
          },
          {
            path: 'visit-region',
            name: 'VisitRegion',
            component: () => import('@/views/stat/VisitRegion.vue'),
            meta: { title: 'menu.stat.visitRegion', requiresPermission: 'visitRegion:page', stateQuery: ['range'] },
          },
          {
            path: 'visit-env',
            name: 'VisitEnv',
            component: () => import('@/views/stat/VisitEnv.vue'),
            meta: { title: 'menu.stat.visitEnv', requiresPermission: 'visitEnv:page', stateQuery: ['range'] },
          },
        ],
      },
      {
        path: 'article-stat',
        meta: { title: 'menu.stat.articleStat', requiresPermission: 'menu.stat.articleStat' },
        children: [
          {
            path: 'by-user',
            name: 'ArticleStatByUser',
            component: () => import('@/views/stat/ArticleStatByUser.vue'),
            meta: { title: 'menu.stat.articleStat.byUser', requiresPermission: 'articleStatByUser:page' },
          },
          {
            path: 'by-org',
            name: 'ArticleStatByOrg',
            component: () => import('@/views/stat/ArticleStatByOrg.vue'),
            meta: { title: 'menu.stat.articleStat.byOrg', requiresPermission: 'articleStatByOrg:page' },
          },
          {
            path: 'by-channel',
            name: 'ArticleStatByChannel',
            component: () => import('@/views/stat/ArticleStatByChannel.vue'),
            meta: { title: 'menu.stat.articleStat.byChannel', requiresPermission: 'articleStatByChannel:page' },
          },
        ],
      },
      {
        path: 'performance-stat',
        meta: { title: 'menu.stat.performanceStat', requiresPermission: 'menu.stat.performanceStat' },
        children: [
          {
            path: 'by-user',
            name: 'PerformanceStatByUser',
            component: () => import('@/views/stat/PerformanceStatByUser.vue'),
            meta: { title: 'menu.stat.performanceStat.byUser', requiresPermission: 'performanceStatByUser:page' },
          },
          {
            path: 'by-org',
            name: 'PerformanceStatByOrg',
            component: () => import('@/views/stat/PerformanceStatByOrg.vue'),
            meta: { title: 'menu.stat.performanceStat.byOrg', requiresPermission: 'performanceStatByOrg:page' },
          },
        ],
      },
    ],
  },
  {
    // 设计（theme-marketplace §7.1）：主题市场 + 页面/卡片/形状/健康的一体化设计区。
    // 权限码与路径无关，沿用既有码（组码保持 menu.composition）；全屏构建器在上方 /design/builder。
    path: '/design',
    component: Layout,
    meta: { title: 'menu.design', icon: Brush, requiresPermission: 'menu.composition' },
    children: [
      {
        path: 'theme',
        name: 'ThemeGallery',
        component: () => import('@/views/design/ThemeGallery.vue'),
        meta: { title: 'menu.design.theme', requiresPermission: 'theme:list', stateQuery: ['siteId', 'tab'] },
      },
      {
        // 主题详情（画廊点入；query: site + theme）
        path: 'theme/detail',
        name: 'ThemeDetail',
        component: () => import('@/views/design/ThemeDetail.vue'),
        meta: { title: 'menu.design.themeDetail', requiresPermission: 'theme:list', hidden: true, noCache: true, stateQuery: ['tab'], activeMenu: 'ThemeGallery' },
      },
      {
        // 模板（template-manager P2）：主题上下文的模板管理外壳（文件操作核心复用 WebFileList）；
        // 权限码沿用 webFileTemplate:*
        path: 'template',
        name: 'TemplateManager',
        component: () => import('@/views/design/TemplateManager.vue'),
        meta: { title: 'menu.design.template', requiresPermission: 'webFileTemplate:page' },
      },
      {
        path: 'page',
        name: 'PageList',
        component: () => import('@/views/design/PageList.vue'),
        meta: { title: 'menu.design.page', requiresPermission: 'pageRegion:show' },
      },
      {
        path: 'shape',
        name: 'ShapeCatalog',
        component: () => import('@/views/design/ShapeCatalog.vue'),
        meta: { title: 'menu.design.shape', requiresPermission: 'shape:list' },
      },
      {
        path: 'health',
        name: 'CompositionHealth',
        component: () => import('@/views/design/CompositionHealth.vue'),
        meta: { title: 'menu.design.health', requiresPermission: 'composition:health' },
      },
    ],
  },
  {
    path: '/file',
    component: Layout,
    meta: { title: 'menu.file', icon: Folder, requiresPermission: 'menu.file' },
    children: [
      {
        path: 'web-file-upload',
        name: 'WebFileUploadList',
        component: () => import('@/views/file/WebFileUploadList.vue'),
        meta: { title: 'menu.file.webFileUpload', requiresPermission: 'webFileUpload:page', stateQuery: ['dir', 'global'] },
      },
      {
        path: 'web-file-html',
        name: 'WebFileHtmlList',
        component: () => import('@/views/file/WebFileHtmlList.vue'),
        meta: { title: 'menu.file.webFileHtml', requiresPermission: 'webFileHtml:page', stateQuery: ['dir', 'global'] },
      },
      {
        // 备份管理：模板 / 上传 / 增量 / 数据库四种备份共用一屏（console-menu D24），页签按各自的 :list 码显示
        path: 'backup',
        name: 'BackupList',
        component: () => import('@/views/file/BackupList.vue'),
        meta: { title: 'menu.file.backup', requiresPermission: 'backup:page', stateQuery: ['tab'] },
      },
    ],
  },
  {
    path: '/user',
    component: Layout,
    meta: { title: 'menu.user', icon: User, requiresPermission: 'menu.user' },
    children: [
      {
        path: 'user',
        name: 'UserList',
        component: () => import('@/views/user/UserList.vue'),
        meta: { title: 'menu.user.user', requiresPermission: 'user:page', stateQuery: ['orgId', 'global'] },
      },
      { path: 'role', name: 'RoleList', component: () => import('@/views/user/RoleList.vue'), meta: { title: 'menu.user.role', requiresPermission: 'role:page' } },
      {
        path: 'org',
        name: 'OrgList',
        component: () => import('@/views/user/OrgList.vue'),
        meta: { title: 'menu.user.org', requiresPermission: 'org:page', stateQuery: ['orgId', 'global'] },
      },
      { path: 'group', name: 'GroupList', component: () => import('@/views/user/GroupList.vue'), meta: { title: 'menu.user.group', requiresPermission: 'group:page' } },
      {
        // 系统通知：向所选用户账号推送站内通知（写入会员中心的通知收件箱）。收件人是用户账号，故归 用户 组（console-menu D27）
        path: 'notification',
        name: 'NotificationSend',
        component: () => import('@/views/user/NotificationSend.vue'),
        meta: { title: 'menu.user.notification', requiresPermission: 'notification:send' },
      },
    ],
  },
  {
    path: '/config',
    component: Layout,
    meta: { title: 'menu.config', icon: Setting, requiresPermission: 'menu.config' },
    children: [
      {
        path: 'global-settings',
        name: 'GlobalSettings',
        component: () => import('@/views/config/GlobalSettings.vue'),
        meta: { title: 'menu.config.globalSettings', requiresPermission: 'config:show', stateQuery: ['type'] },
      },
      {
        path: 'site-settings',
        name: 'SiteSettings',
        component: () => import('@/views/config/SiteSettings.vue'),
        meta: { title: 'menu.config.siteSettings', requiresPermission: 'siteSettings:show', stateQuery: ['type'] },
      },
      {
        path: 'model',
        name: 'ModelList',
        component: () => import('@/views/config/ModelList.vue'),
        meta: { title: 'menu.config.model', requiresPermission: 'model:page', stateQuery: ['type'] },
      },
      {
        path: 'dict',
        name: 'DictList',
        component: () => import('@/views/config/DictList.vue'),
        meta: { title: 'menu.config.dict', requiresPermission: 'dict:page', stateQuery: ['typeId'] },
      },
      {
        path: 'performance-type',
        name: 'PerformanceTypeList',
        component: () => import('@/views/config/PerformanceTypeList.vue'),
        meta: { title: 'menu.config.performanceType', requiresPermission: 'performanceType:page' },
      },
      // 配置 = 定义、系统 = 运行（console-menu R8）：流程模型由栏目 / 表单类型表单选用，敏感词 / 易错词在保存时被查询，
      // 都是“定义”；流程实例 / 流程历史是运行记录，留在 /system
      {
        path: 'process-model',
        name: 'ProcessModelList',
        component: () => import('@/views/config/ProcessModelList.vue'),
        meta: { title: 'menu.config.processModel', requiresPermission: 'processModel:page' },
      },
      {
        path: 'sensitive-word',
        name: 'SensitiveWordList',
        component: () => import('@/views/config/SensitiveWordList.vue'),
        meta: { title: 'menu.config.sensitiveWord', requiresPermission: 'sensitiveWord:page' },
      },
      {
        path: 'error-word',
        name: 'ErrorWordList',
        component: () => import('@/views/config/ErrorWordList.vue'),
        meta: { title: 'menu.config.errorWord', requiresPermission: 'errorWord:page' },
      },
    ],
  },
  {
    path: '/system',
    component: Layout,
    meta: { title: 'menu.system', icon: Operation, requiresPermission: 'menu.system' },
    children: [
      {
        path: 'site',
        name: 'SiteList',
        component: () => import('@/views/system/SiteList.vue'),
        meta: { title: 'menu.system.site', requiresPermission: 'site:page', stateQuery: ['siteId', 'global'] },
      },
      {
        // 生成管理与定时任务相邻：同一组生成器，前者立即执行，后者按计划执行
        path: 'generator',
        name: 'GeneratorForm',
        component: () => import('@/views/system/GeneratorForm.vue'),
        meta: { title: 'menu.system.generator', requiresPermission: 'generator:show' },
      },
      {
        path: 'schedule-task',
        name: 'ScheduleTaskList',
        component: () => import('@/views/system/ScheduleTaskList.vue'),
        meta: { title: 'menu.system.scheduleTask', requiresPermission: 'scheduleTask:page' },
      },
      {
        path: 'process-instance',
        name: 'ProcessInstanceList',
        component: () => import('@/views/system/ProcessInstanceList.vue'),
        meta: { title: 'menu.system.processInstance', requiresPermission: 'processInstance:page' },
      },
      {
        path: 'process-history',
        name: 'ProcessHistoryList',
        component: () => import('@/views/system/ProcessHistoryList.vue'),
        meta: { title: 'menu.system.processHistory', requiresPermission: 'processHistory:page' },
      },
      {
        path: 'import-data',
        name: 'ImportData',
        component: () => import('@/views/system/ImportData.vue'),
        meta: { title: 'menu.system.importData', requiresPermission: 'importData:show' },
      },
      // { path: 'task', name: 'TaskList', component: () => import('@/views/system/TaskList.vue'), meta: { title: 'menu.system.task', requiresPermission: 'task:page' } },
    ],
  },
  {
    path: '/log',
    component: Layout,
    meta: { title: 'menu.log', icon: Notebook, requiresPermission: 'menu.log' },
    children: [
      {
        path: 'login-log',
        name: 'LoginLogList',
        component: () => import('@/views/log/LoginLogList.vue'),
        meta: { title: 'menu.log.loginLog', requiresPermission: 'loginLog:page' },
      },
      {
        path: 'operation-log',
        name: 'OperationLogList',
        component: () => import('@/views/log/OperationLogList.vue'),
        meta: { title: 'menu.log.operationLog', requiresPermission: 'operationLog:page' },
      },
      {
        path: 'short-message',
        name: 'ShortMessageList',
        component: () => import('@/views/log/ShortMessageList.vue'),
        meta: { title: 'menu.log.shortMessage', requiresPermission: 'shortMessage:page' },
      },
      {
        path: 'ai-log',
        name: 'AiLogList',
        component: () => import('@/views/log/AiLogList.vue'),
        meta: { title: 'menu.log.aiLog', requiresPermission: 'aiLog:page' },
      },
    ],
  },
  // 404 页面配置必须放在最后
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/404',
    meta: { hidden: true, noCache: true },
  },
];
const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes,
});

export default router;

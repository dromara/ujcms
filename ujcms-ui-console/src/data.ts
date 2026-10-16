import i18n from '@/i18n';
import { isShowPerm } from '@/stores/useCurrentUser';

function filterExcludePerms(arr: any[]): any[] {
  const resultes = arr.filter((item) => (item.perms?.findIndex((perm: string) => !isShowPerm(perm)) ?? -1) === -1);
  for (let i = 0, len = resultes.length; i < len; i += 1) {
    if (resultes[i].children) {
      resultes[i].children = filterExcludePerms(resultes[i].children);
    }
  }
  return resultes;
}

export function getPermsTreeData(): any[] {
  const {
    global: { t },
  } = i18n;
  const perms = [
    {
      label: t('menu.personal'),
      key: 'menu.personal.key',
      perms: ['menu.personal'],
      children: [
        {
          label: t('menu.personal.message'),
          key: 'message.key',
          perms: ['message:page', 'message:list'],
          children: [
            { label: t('list'), key: 'message:page' },
            { label: t('message.op.send'), key: 'message:send', perms: ['message:send'] },
            { label: t('message.op.bulkSend'), key: 'message:bulkSend', perms: ['message:bulkSend'] },
            { label: t('delete'), key: 'message:delete', perms: ['message:delete'] },
          ],
        },
        {
          // 本人通知收件箱（会员接口，无独立后端权限码）：控制台路由标志
          label: t('menu.personal.notification'),
          key: 'notification:page',
          perms: ['notification:page'],
        },
        {
          label: t('menu.personal.password'),
          key: 'password:update',
          perms: ['password:update'],
        },
        {
          label: t('menu.personal.homepage.systemInfo'),
          key: 'homepage:systemInfo',
          perms: ['homepage:systemInfo'],
        },
        {
          label: t('menu.personal.homepage.systemMonitor'),
          key: 'homepage:systemMonitor',
          perms: ['homepage:systemMonitor', 'homepage:systemLoad'],
        },
        {
          label: t('menu.personal.homepage.generatedKey'),
          key: 'homepage:generatedKey',
          perms: ['homepage:generatedKey'],
        },
      ],
    },
    {
      label: t('menu.content'),
      key: 'menu.content.key',
      perms: ['menu.content'],
      children: [
        {
          label: t('menu.content.article'),
          key: 'article.key',
          // topic:list：列表页的专题筛选与文章表单的专题字段（缺权限时不请求、不显示，见 fetchTopicList）。
          // 专题为 T2 功能（2026-09-15）但 topic:list 不分级——分级会让 filterExcludePerms 隐藏整个文章节点；视图另按 commercialTier 门禁
          perms: ['article:page', 'article:list', 'channel:list', 'org:list', 'dict:list', 'model:list', 'block:list', 'tag:list', 'processInstance:task', 'topic:list'],
          children: [
            { label: t('list'), key: 'article:page' },
            { label: t('add'), key: 'article:create', perms: ['article:create', 'jodConvert:doc', 'jodConvert:library'] },
            { label: t('view'), key: 'article:show', perms: ['article:show'] },
            { label: t('edit'), key: 'article:update', perms: ['article:update', 'article:show', 'jodConvert:doc', 'jodConvert:library'] },
            { label: t('article.op.recommendTo'), key: 'article:recommendTo', perms: ['article:recommendTo', 'blockItem:create', 'blockItem:delete'] },
            { label: t('article.op.internalPush'), key: 'article:internalPush', perms: ['article:internalPush'] },
            { label: t('article.op.externalPush'), key: 'article:externalPush', perms: ['article:externalPush'] },
            { label: t('article.op.createTranslation'), key: 'article:createTranslation', perms: ['article:createTranslation'] },
            { label: t('article.i18n.batchTranslate'), key: 'article:translateBatch', perms: ['article:translateBatch'] },
            { label: t('article.op.updateOrder'), key: 'article:updateOrder', perms: ['article:updateOrder'] },
            { label: t('article.op.sticky'), key: 'article:sticky', perms: ['article:sticky'] },
            { label: t('article.op.submit'), key: 'article:submit', perms: ['article:submit'] },
            { label: t('article.op.archive'), key: 'article:archive', perms: ['article:archive'] },
            { label: t('article.op.offline'), key: 'article:offline', perms: ['article:offline'] },
            { label: t('delete'), key: 'article:delete', perms: ['article:delete'] },
            { label: t('completelyDelete'), key: 'article:completelyDelete', perms: ['article:completelyDelete'] },
            { label: t('article.op.export'), key: 'article:export', perms: ['article:export'] },
            { label: t('article.op.import'), key: 'article:import', perms: ['article:import'] },
          ],
        },
        {
          label: t('menu.content.articleReview'),
          key: 'articleReview.key',
          perms: ['articleReview:page', 'articleReview:list', 'channel:list', 'dict:list', 'model:list', 'block:list', 'processInstance:task'],
          children: [
            { label: t('list'), key: 'articleReview:page' },
            { label: t('review.pass'), key: 'articleReview:pass', perms: ['articleReview:pass'] },
            { label: t('review.reject'), key: 'articleReview:reject', perms: ['articleReview:reject'] },
            { label: t('review.back'), key: 'articleReview:back', perms: ['articleReview:back'] },
            { label: t('review.delegate'), key: 'articleReview:delegate', perms: ['articleReview:delegate'] },
            { label: t('review.transfer'), key: 'articleReview:transfer', perms: ['articleReview:transfer'] },
          ],
        },
        {
          label: t('menu.content.channel'),
          key: 'channel.key',
          perms: ['channel:page', 'channel:list', 'processModel:list', 'processDefinition:list', 'dict:list', 'performanceType:list', 'model:list'],
          children: [
            { label: t('list'), key: 'channel:page' },
            { label: t('add'), key: 'channel:create', perms: ['channel:create'] },
            { label: t('edit'), key: 'channel:update', perms: ['channel:update', 'channel:show'] },
            { label: t('delete'), key: 'channel:delete', perms: ['channel:delete'] },
            { label: t('tidyTree'), key: 'channel:tidyTree', perms: ['channel:tidyTree'] },
            { label: t('channel.op.translateBatch'), key: 'channel:translateBatch', perms: ['channel:translateBatch'] },
          ],
        },
        {
          // 专题（article-channels-and-topics phase-2 §11；2026-09-15 起为商业版 T2：topic:page 在 Commercial.PERMISSION_TIERS 分级，
          // 低于 T2 时 filterExcludePerms 隐藏本节点；操作码不分级）。topic:show 进组包：TopicList 选中专题即挂载
          // TopicArticlePanel（queryArticleTopicList）；编辑叶带 channel:list/article:list：面板「添加文章」选择器
          // 需栏目树与文章分页
          label: t('menu.content.topic'),
          key: 'topic.key',
          perms: ['topic:page', 'topic:list', 'topic:show', 'model:list'],
          children: [
            { label: t('list'), key: 'topic:page' },
            { label: t('add'), key: 'topic:create', perms: ['topic:create'] },
            { label: t('edit'), key: 'topic:update', perms: ['topic:update', 'channel:list', 'article:list'] },
            { label: t('delete'), key: 'topic:delete', perms: ['topic:delete'] },
            { label: t('tidyTree'), key: 'topic:tidyTree', perms: ['topic:tidyTree'] },
          ],
        },
        {
          label: t('menu.content.tag'),
          key: 'tag.key',
          perms: ['tag:page', 'tag:list'],
          children: [
            { label: t('list'), key: 'tag:page' },
            { label: t('add'), key: 'tag:create', perms: ['tag:create'] },
            { label: t('edit'), key: 'tag:update', perms: ['tag:update', 'tag:show'] },
            { label: t('delete'), key: 'tag:delete', perms: ['tag:delete'] },
          ],
        },
        {
          // 区块：区块与区块项同屏（docs/spec/console-master-detail）——blockItem:* 为区块项权限，block:* 为区块权限。
          // block:validation：顶栏据“其它站点是否仍有区块项”决定全局区块可否删除；
          // model:list：区块项表单按区块所绑定的区块项模型取字段定义，区块表单选择区块项模型
          label: t('menu.content.blockItem'),
          key: 'blockItem.key',
          perms: ['blockItem:page', 'blockItem:list', 'block:list', 'block:validation', 'model:list'],
          children: [
            { label: t('list'), key: 'blockItem:page' },
            { label: t('add'), key: 'blockItem:create', perms: ['blockItem:create'] },
            { label: t('edit'), key: 'blockItem:update', perms: ['blockItem:update', 'blockItem:show'] },
            { label: t('delete'), key: 'blockItem:delete', perms: ['blockItem:delete'] },
            { label: t('block.op.create'), key: 'block:create', perms: ['block:create'] },
            { label: t('block.op.update'), key: 'block:update', perms: ['block:update', 'block:show'] },
            { label: t('block.op.delete'), key: 'block:delete', perms: ['block:delete'] },
          ],
        },
        {
          // 链接：链接组与链接同屏（docs/spec/console-master-detail）——link:* 为链接权限，linkGroup:* 为链接组权限。
          // channel:list / article:list：链接目标可选栏目或文章，表单内的选择器须取其列表
          label: t('menu.content.link'),
          key: 'link.key',
          perms: ['link:page', 'link:list', 'linkGroup:list', 'linkGroup:validation'],
          children: [
            { label: t('list'), key: 'link:page' },
            { label: t('add'), key: 'link:create', perms: ['link:create', 'channel:list', 'article:list'] },
            { label: t('edit'), key: 'link:update', perms: ['link:update', 'link:show', 'channel:list', 'article:list'] },
            { label: t('delete'), key: 'link:delete', perms: ['link:delete'] },
            { label: t('linkGroup.op.create'), key: 'linkGroup:create', perms: ['linkGroup:create'] },
            { label: t('linkGroup.op.update'), key: 'linkGroup:update', perms: ['linkGroup:update', 'linkGroup:show'] },
            { label: t('linkGroup.op.delete'), key: 'linkGroup:delete', perms: ['linkGroup:delete'] },
          ],
        },
        {
          label: t('menu.content.resourceLibrary'),
          key: 'attachment.key',
          // attachment:show：行点击打开附件详情（AttachmentList / AttachmentDetail 的 queryAttachment）
          perms: ['attachment:page', 'attachment:list', 'attachment:show'],
          children: [
            { label: t('list'), key: 'attachment:page' },
            { label: t('add'), key: 'attachment:create', perms: ['attachment:create'] },
            { label: t('delete'), key: 'attachment:delete', perms: ['attachment:delete'] },
            { label: t('resource.addToShared'), key: 'resource:share', perms: ['resource:share'] },
            { label: t('resource.favorite'), key: 'resource:favorite', perms: ['resource:favorite'] },
            { label: t('resource.import'), key: 'resource:import', perms: ['resource:import'] },
          ],
        },
        {
          label: t('menu.content.resourceCategory'),
          key: 'resourceCategory.key',
          perms: ['resourceCategory:list'],
          children: [
            { label: t('list'), key: 'resourceCategory:list' },
            { label: t('add'), key: 'resourceCategory:create', perms: ['resourceCategory:create'] },
            { label: t('edit'), key: 'resourceCategory:update', perms: ['resourceCategory:update', 'resourceCategory:show'] },
            { label: t('delete'), key: 'resourceCategory:delete', perms: ['resourceCategory:delete'] },
          ],
        },
        {
          label: t('menu.content.collection'),
          key: 'collection.key',
          perms: ['collection:page', 'collection:list'],
          children: [
            { label: t('list'), key: 'collection:page' },
            { label: t('add'), key: 'collection:create', perms: ['collection:create'] },
            { label: t('edit'), key: 'collection:update', perms: ['collection:update', 'collection:show'] },
            { label: t('delete'), key: 'collection:delete', perms: ['collection:delete'] },
            { label: t('collection.op.start'), key: 'collection:start', perms: ['collection:start'] },
            { label: t('collection.op.stop'), key: 'collection:stop', perms: ['collection:stop'] },
            { label: t('collection.op.clearRecords'), key: 'collection:clearRecords', perms: ['collection:clearRecords'] },
          ],
        },
      ],
    },
    {
      label: t('menu.interaction'),
      key: 'menu.interaction.key',
      perms: ['menu.interaction'],
      children: [
        {
          // 留言：留言类型经本页的“类型管理”对话框维护（docs/spec/console-master-detail R3）——messageBoardType:* 为类型权限
          label: t('menu.interaction.messageBoard'),
          key: 'messageBoard.key',
          perms: ['messageBoard:page', 'messageBoard:list', 'messageBoardType:list'],
          children: [
            { label: t('list'), key: 'messageBoard:page' },
            { label: t('add'), key: 'messageBoard:create', perms: ['messageBoard:create'] },
            { label: t('edit'), key: 'messageBoard:update', perms: ['messageBoard:update', 'messageBoard:show'] },
            { label: t('updateStatus'), key: 'messageBoard:updateStatus', perms: ['messageBoard:updateStatus'] },
            { label: t('delete'), key: 'messageBoard:delete', perms: ['messageBoard:delete'] },
            { label: t('messageBoardType.op.create'), key: 'messageBoardType:create', perms: ['messageBoardType:create'] },
            { label: t('messageBoardType.op.update'), key: 'messageBoardType:update', perms: ['messageBoardType:update', 'messageBoardType:show'] },
            { label: t('messageBoardType.op.delete'), key: 'messageBoardType:delete', perms: ['messageBoardType:delete'] },
          ],
        },
        {
          label: t('menu.interaction.comment'),
          key: 'comment.key',
          perms: ['comment:page', 'comment:list'],
          children: [
            { label: t('list'), key: 'comment:page' },
            { label: t('edit'), key: 'comment:update', perms: ['comment:update', 'comment:show'] },
            { label: t('updateStatus'), key: 'comment:updateStatus', perms: ['comment:updateStatus'] },
            { label: t('delete'), key: 'comment:delete', perms: ['comment:delete'] },
          ],
        },
        {
          label: t('menu.interaction.aiModeration'),
          key: 'aiModeration.key',
          perms: ['aiModeration:page', 'aiModeration:list'],
          children: [
            { label: t('list'), key: 'aiModeration:list' },
            { label: t('aiModeration.op.retry'), key: 'aiModeration:review', perms: ['aiModeration:review'] },
            { label: t('aiModeration.op.draft'), key: 'ai:replyDraft', perms: ['ai:replyDraft'] },
          ],
        },
        {
          // 表单：表单类型与表单同屏（docs/spec/console-master-detail）——form:* 为表单权限，formType:* 为表单类型权限。
          // processDefinition:list：类型表单选择审核流程（sys_form），顶栏据此显示流程名；挂在类型的新增/编辑上，不随页面下发
          label: t('menu.interaction.form'),
          key: 'form.key',
          perms: ['form:page', 'form:list', 'formType:list', 'dict:list', 'model:list', 'processInstance:task'],
          children: [
            { label: t('list'), key: 'form:page' },
            { label: t('add'), key: 'form:create', perms: ['form:create'] },
            { label: t('edit'), key: 'form:update', perms: ['form:update', 'form:show'] },
            { label: t('form.op.updateOrder'), key: 'form:updateOrder', perms: ['form:updateOrder'] },
            { label: t('form.op.submit'), key: 'form:submit', perms: ['form:submit'] },
            { label: t('delete'), key: 'form:delete', perms: ['form:delete'] },
            { label: t('completelyDelete'), key: 'form:completelyDelete', perms: ['form:completelyDelete'] },
            { label: t('formType.op.create'), key: 'formType:create', perms: ['formType:create', 'processDefinition:list'] },
            { label: t('formType.op.update'), key: 'formType:update', perms: ['formType:update', 'formType:show', 'processDefinition:list'] },
            { label: t('formType.op.delete'), key: 'formType:delete', perms: ['formType:delete'] },
          ],
        },
        {
          label: t('menu.interaction.formReview'),
          key: 'formReview.key',
          perms: ['formReview:page', 'formReview:list', 'formType:list', 'dict:list', 'model:list', 'processInstance:task'],
          children: [
            { label: t('list'), key: 'formReview:page' },
            { label: t('review.pass'), key: 'formReview:pass', perms: ['formReview:pass'] },
            { label: t('review.reject'), key: 'formReview:reject', perms: ['formReview:reject'] },
            { label: t('review.back'), key: 'formReview:back', perms: ['formReview:back'] },
            { label: t('review.delegate'), key: 'formReview:delegate', perms: ['formReview:delegate'] },
            { label: t('review.transfer'), key: 'formReview:transfer', perms: ['formReview:transfer'] },
          ],
        },
        {
          label: t('menu.interaction.vote'),
          key: 'vote.key',
          perms: ['vote:page', 'vote:list'],
          children: [
            { label: t('list'), key: 'vote:page' },
            { label: t('add'), key: 'vote:create', perms: ['vote:create'] },
            { label: t('edit'), key: 'vote:update', perms: ['vote:update', 'vote:show'] },
            { label: t('delete'), key: 'vote:delete', perms: ['vote:delete'] },
          ],
        },
        {
          label: t('menu.interaction.survey'),
          key: 'survey.key',
          perms: ['survey:page', 'survey:list'],
          children: [
            { label: t('list'), key: 'survey:page' },
            { label: t('add'), key: 'survey:create', perms: ['survey:create'] },
            { label: t('edit'), key: 'survey:update', perms: ['survey:update', 'survey:show'] },
            { label: t('delete'), key: 'survey:delete', perms: ['survey:delete'] },
          ],
        },
        // {
        //   label: t('menu.interaction.example'),
        //   key: 'example.key',
        //   perms: ['example:page', 'example:list'],
        //   children: [
        //     { label: t('list'), key: 'example:page' },
        //     { label: t('add'), key: 'example:create', perms: ['example:create'] },
        //     { label: t('edit'), key: 'example:update', perms: ['example:update', 'example:show'] },
        //     { label: t('delete'), key: 'example:delete', perms: ['example:delete'] },
        //   ],
        // },
      ],
    },
    {
      // 政务（console-menu）：依申请公开 / 领导信箱 / 在线访谈 / 网站普查四项 T4 功能合为一组；
      // 组权限码沿用 menu.census（码与路径、标签无关，不迁移角色数据；同 menu.composition 先例）
      label: t('menu.government'),
      key: 'menu.government.key',
      perms: ['menu.census'],
      children: [
        {
          label: t('menu.government.disclosure'),
          key: 'disclosure.key',
          perms: ['disclosure:page', 'disclosure:list'],
          children: [
            { label: t('list'), key: 'disclosure:page' },
            { label: t('add'), key: 'disclosure:create', perms: ['disclosure:create'] },
            { label: t('edit'), key: 'disclosure:update', perms: ['disclosure:update', 'disclosure:show'] },
            { label: t('disclosure.op.accept'), key: 'disclosure:accept', perms: ['disclosure:accept'] },
            { label: t('disclosure.op.extend'), key: 'disclosure:extend', perms: ['disclosure:extend'] },
            { label: t('disclosure.op.correct'), key: 'disclosure:correct', perms: ['disclosure:correct', 'disclosure:show'] },
            { label: t('disclosure.op.correctReceived'), key: 'disclosure:correctReceived', perms: ['disclosure:correctReceived'] },
            { label: t('disclosure.op.reply'), key: 'disclosure:reply', perms: ['disclosure:reply', 'disclosure:show'] },
            { label: t('delete'), key: 'disclosure:delete', perms: ['disclosure:delete'] },
          ],
        },
        {
          // 领导信箱：信箱类型经本页的“类型管理”对话框维护（docs/spec/console-master-detail R3）——mailboxType:* 为类型权限
          label: t('menu.government.mailbox'),
          key: 'mailbox.key',
          perms: ['mailbox:page', 'mailbox:list', 'mailboxType:list'],
          children: [
            { label: t('list'), key: 'mailbox:page' },
            { label: t('add'), key: 'mailbox:create', perms: ['mailbox:create'] },
            { label: t('edit'), key: 'mailbox:update', perms: ['mailbox:update', 'mailbox:show'] },
            { label: t('mailbox.op.accept'), key: 'mailbox:accept', perms: ['mailbox:accept'] },
            { label: t('mailbox.op.reject'), key: 'mailbox:reject', perms: ['mailbox:reject', 'mailbox:show'] },
            { label: t('mailbox.op.extend'), key: 'mailbox:extend', perms: ['mailbox:extend'] },
            { label: t('mailbox.op.reply'), key: 'mailbox:reply', perms: ['mailbox:reply', 'mailbox:show'] },
            { label: t('mailbox.op.publish'), key: 'mailbox:publish', perms: ['mailbox:publish'] },
            { label: t('delete'), key: 'mailbox:delete', perms: ['mailbox:delete'] },
            { label: t('mailboxType.op.create'), key: 'mailboxType:create', perms: ['mailboxType:create'] },
            { label: t('mailboxType.op.update'), key: 'mailboxType:update', perms: ['mailboxType:update', 'mailboxType:show'] },
            { label: t('mailboxType.op.delete'), key: 'mailboxType:delete', perms: ['mailboxType:delete'] },
          ],
        },
        {
          label: t('menu.government.mailboxStat'),
          key: 'mailboxStat:page',
          perms: ['mailboxStat:page', 'mailboxStat:list'],
        },
        {
          label: t('menu.government.interview'),
          key: 'interview.key',
          perms: ['interview:page', 'interview:list', 'interviewMessage:list', 'interviewQuestion:list'],
          children: [
            { label: t('list'), key: 'interview:page' },
            { label: t('add'), key: 'interview:create', perms: ['interview:create'] },
            { label: t('edit'), key: 'interview:update', perms: ['interview:update', 'interview:show'] },
            { label: t('interview.op.live'), key: 'interview:live', perms: ['interview:live'] },
            { label: t('interview.op.publish'), key: 'interview:publish', perms: ['interview:publish'] },
            { label: t('delete'), key: 'interview:delete', perms: ['interview:delete'] },
            {
              label: t('interview.perm.message'),
              key: 'interviewMessage.key',
              perms: ['interviewMessage:list', 'interviewMessage:create', 'interviewMessage:update', 'interviewMessage:delete'],
            },
            {
              label: t('interview.perm.questionReview'),
              key: 'interviewQuestion:review',
              perms: ['interviewQuestion:list', 'interviewQuestion:show', 'interviewQuestion:review'],
            },
            {
              label: t('interview.perm.questionUpdate'),
              key: 'interviewQuestion:update',
              perms: ['interviewQuestion:update', 'interviewQuestion:create', 'interviewQuestion:show'],
            },
            { label: t('interview.perm.questionDelete'), key: 'interviewQuestion:delete', perms: ['interviewQuestion:delete'] },
          ],
        },
        {
          label: t('menu.government.census'),
          key: 'census.key',
          perms: ['census:page', 'census:list', 'census:show'],
          children: [
            { label: t('list'), key: 'census:page' },
            { label: t('add'), key: 'census:create', perms: ['census:create'] },
            { label: t('census.op.stop'), key: 'census:stop', perms: ['census:stop'] },
            { label: t('census.op.score'), key: 'census:score', perms: ['census:score'] },
            { label: t('delete'), key: 'census:delete', perms: ['census:delete'] },
          ],
        },
        {
          label: t('menu.government.censusIssue'),
          key: 'censusIssue.key',
          perms: ['censusIssue:page', 'censusIssue:list', 'censusIssue:show'],
          children: [
            { label: t('list'), key: 'censusIssue:page' },
            { label: t('censusIssue.op.process'), key: 'censusIssue:process', perms: ['censusIssue:process'] },
            { label: t('delete'), key: 'censusIssue:delete', perms: ['censusIssue:delete'] },
          ],
        },
      ],
    },
    {
      label: t('menu.wechat'),
      key: 'menu.wechat.key',
      perms: ['menu.wechat'],
      children: [
        {
          label: t('menu.wechat.wechatAccount'),
          key: 'wechatAccount.key',
          perms: ['wechatAccount:page', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatAccount:page' },
            { label: t('add'), key: 'wechatAccount:create', perms: ['wechatAccount:create'] },
            { label: t('edit'), key: 'wechatAccount:update', perms: ['wechatAccount:update', 'wechatAccount:show'] },
            { label: t('delete'), key: 'wechatAccount:delete', perms: ['wechatAccount:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatMaterial'),
          key: 'wechatMaterial.key',
          perms: ['wechatMaterial:page', 'wechatMaterial:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatMaterial:page' },
            { label: t('wechatMaterial.op.upload'), key: 'wechatMaterial:upload', perms: ['wechatMaterial:upload'] },
            { label: t('delete'), key: 'wechatMaterial:delete', perms: ['wechatMaterial:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatDraft'),
          key: 'wechatDraft.key',
          perms: ['wechatDraft:page', 'wechatDraft:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatDraft:page' },
            { label: t('wechatDraft.op.publish'), key: 'wechatDraft:publish', perms: ['wechatDraft:publish'] },
            { label: t('delete'), key: 'wechatDraft:delete', perms: ['wechatDraft:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatPublish'),
          key: 'wechatPublish.key',
          perms: ['wechatPublish:page', 'wechatPublish:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatPublish:page' },
            { label: t('delete'), key: 'wechatPublish:delete', perms: ['wechatPublish:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatMass'),
          key: 'wechatMass.key',
          perms: ['wechatMass:page', 'wechatMass:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatMass:page' },
            { label: t('wechatMass.op.send'), key: 'wechatMass:send', perms: ['wechatMass:send', 'wechatDraft:list', 'wechatTag:list'] },
            { label: t('wechatMass.op.preview'), key: 'wechatMass:preview', perms: ['wechatMass:preview'] },
            { label: t('wechatMass.op.refresh'), key: 'wechatMass:refresh', perms: ['wechatMass:refresh'] },
            { label: t('wechatMass.op.revoke'), key: 'wechatMass:revoke', perms: ['wechatMass:revoke'] },
            { label: t('delete'), key: 'wechatMass:delete', perms: ['wechatMass:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatArticle'),
          key: 'wechatArticle.key',
          // wechatAccount:list：列表挂载即取公众号列表（账号切换）
          perms: ['wechatArticle:page', 'wechatArticle:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatArticle:page' },
            { label: t('article.op.wechatPush'), key: 'wechatArticle:push', perms: ['wechatArticle:push', 'wechatAccount:list'] },
            { label: t('wechatArticle.op.publish'), key: 'wechatArticle:publish', perms: ['wechatArticle:publish'] },
            { label: t('wechatArticle.op.refresh'), key: 'wechatArticle:refresh', perms: ['wechatArticle:refresh'] },
            { label: t('delete'), key: 'wechatArticle:delete', perms: ['wechatArticle:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatMenu'),
          key: 'wechatMenu.key',
          perms: ['wechatMenu:page', 'wechatMenu:show', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatMenu:page' },
            { label: t('wechatMenu.op.publish'), key: 'wechatMenu:update', perms: ['wechatMenu:update'] },
            { label: t('wechatMenu.op.delete'), key: 'wechatMenu:delete', perms: ['wechatMenu:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatReply'),
          key: 'wechatReply.key',
          perms: ['wechatReply:page', 'wechatReply:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatReply:page' },
            { label: t('add'), key: 'wechatReply:create', perms: ['wechatReply:create'] },
            { label: t('edit'), key: 'wechatReply:update', perms: ['wechatReply:update', 'wechatReply:show'] },
            { label: t('delete'), key: 'wechatReply:delete', perms: ['wechatReply:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatMessage'),
          key: 'wechatMessage.key',
          perms: ['wechatMessage:page', 'wechatMessage:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatMessage:page' },
            { label: t('delete'), key: 'wechatMessage:delete', perms: ['wechatMessage:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatTemplate'),
          key: 'wechatTemplate.key',
          perms: ['wechatTemplate:page', 'wechatTemplate:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatTemplate:page' },
            { label: t('wechatTemplate.op.send'), key: 'wechatTemplate:send', perms: ['wechatTemplate:send', 'wechatFan:list'] },
            { label: t('delete'), key: 'wechatTemplate:delete', perms: ['wechatTemplate:delete'] },
            { label: t('wechatTemplate.tab.log'), key: 'wechatTemplateLog:list', perms: ['wechatTemplateLog:list'] },
            { label: t('wechatTemplateLog.op.delete'), key: 'wechatTemplateLog:delete', perms: ['wechatTemplateLog:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatFan'),
          key: 'wechatFan.key',
          perms: ['wechatFan:page', 'wechatFan:list', 'wechatAccount:list', 'wechatTag:list'],
          children: [
            { label: t('list'), key: 'wechatFan:page' },
            { label: t('wechatFan.op.remark'), key: 'wechatFan:remark', perms: ['wechatFan:remark'] },
            { label: t('wechatFan.op.tagging'), key: 'wechatFan:tagging', perms: ['wechatFan:tagging'] },
            { label: t('wechatFan.op.blacklist'), key: 'wechatFan:blacklist', perms: ['wechatFan:blacklist'] },
          ],
        },
        {
          label: t('menu.wechat.wechatTag'),
          key: 'wechatTag.key',
          perms: ['wechatTag:page', 'wechatTag:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatTag:page' },
            { label: t('add'), key: 'wechatTag:create', perms: ['wechatTag:create'] },
            { label: t('edit'), key: 'wechatTag:update', perms: ['wechatTag:update'] },
            { label: t('delete'), key: 'wechatTag:delete', perms: ['wechatTag:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatQrcode'),
          key: 'wechatQrcode.key',
          perms: ['wechatQrcode:page', 'wechatQrcode:list', 'wechatAccount:list'],
          children: [
            { label: t('list'), key: 'wechatQrcode:page' },
            { label: t('add'), key: 'wechatQrcode:create', perms: ['wechatQrcode:create'] },
            { label: t('edit'), key: 'wechatQrcode:update', perms: ['wechatQrcode:update'] },
            { label: t('delete'), key: 'wechatQrcode:delete', perms: ['wechatQrcode:delete'] },
          ],
        },
        {
          label: t('menu.wechat.wechatStat'),
          key: 'wechatStat.key',
          perms: ['wechatStat:page', 'wechatStat:list', 'wechatAccount:list'],
          children: [{ label: t('list'), key: 'wechatStat:page' }],
        },
      ],
    },
    {
      label: t('menu.stat'),
      key: 'menu.stat.key',
      perms: ['menu.stat'],
      children: [
        {
          label: t('menu.stat.visit'),
          key: 'menu.stat.visit.key',
          perms: ['menu.stat.visit'],
          children: [
            {
              label: t('menu.stat.visitTrend'),
              key: 'visitTrend:page',
              perms: ['visitTrend:page', 'visitTrend:list'],
            },
            {
              label: t('menu.stat.visitedPage'),
              key: 'visitedPage:page',
              perms: ['visitedPage:page', 'visitedPage:list'],
            },
            {
              label: t('menu.stat.entryPage'),
              key: 'entryPage:page',
              perms: ['entryPage:page', 'entryPage:list'],
            },
            {
              label: t('menu.stat.visitSource'),
              key: 'visitSource:page',
              perms: ['visitSource:page', 'visitSource:list'],
            },
          ],
        },
        {
          label: t('menu.stat.visitor'),
          key: 'menu.stat.visitor.key',
          perms: ['menu.stat.visitor'],
          children: [
            {
              label: t('menu.stat.visitVisitor'),
              key: 'visitVisitor:page',
              perms: ['visitVisitor:page', 'visitVisitor:list'],
            },
            {
              label: t('menu.stat.visitRegion'),
              key: 'visitRegion:page',
              perms: ['visitRegion:page', 'visitCountry:list', 'visitProvince:list'],
            },
            {
              label: t('menu.stat.visitEnv'),
              key: 'visitEnv:page',
              perms: ['visitEnv:page', 'visitDevice:list', 'visitOs:list', 'visitBrowser:list'],
            },
          ],
        },
        {
          label: t('menu.stat.articleStat'),
          key: 'menu.stat.articleStat.key',
          perms: ['menu.stat.articleStat'],
          children: [
            {
              label: t('menu.stat.articleStat.byUser'),
              key: 'articleStatByUser:page',
              perms: ['articleStatByUser:page', 'articleStatByUser:list'],
            },
            {
              label: t('menu.stat.articleStat.byOrg'),
              key: 'articleStatByOrg:page',
              perms: ['articleStatByOrg:page', 'articleStatByOrg:list'],
            },
            {
              label: t('menu.stat.articleStat.byChannel'),
              key: 'articleStatByChannel:page',
              perms: ['articleStatByChannel:page', 'articleStatByChannel:list'],
            },
          ],
        },
        {
          label: t('menu.stat.performanceStat'),
          key: 'menu.stat.performanceStat.key',
          perms: ['menu.stat.performanceStat'],
          children: [
            {
              label: t('menu.stat.performanceStat.byUser'),
              key: 'performanceStatByUser:page',
              perms: ['performanceStatByUser:page', 'performanceStatByUser:list', 'performanceType:list'],
            },
            {
              label: t('menu.stat.performanceStat.byOrg'),
              key: 'performanceStatByOrg:page',
              perms: ['performanceStatByOrg:page', 'performanceStatByOrg:list', 'performanceType:list'],
            },
          ],
        },
      ],
    },
    {
      // 设计（theme-marketplace §7.1）：组权限码沿用 menu.composition（码与路径无关，不迁移角色数据）
      label: t('menu.design'),
      key: 'menu.composition.key',
      perms: ['menu.composition'],
      children: [
        {
          label: t('menu.design.theme'),
          key: 'theme.key',
          perms: ['theme:list', 'site:list'],
          children: [
            { label: t('list'), key: 'theme:list' },
            { label: t('theme.op.install'), key: 'theme:install', perms: ['theme:install'] },
            { label: t('theme.op.activate'), key: 'theme:activate', perms: ['theme:activate'] },
            { label: t('theme.op.settings'), key: 'theme:settings', perms: ['theme:settings'] },
            // 沙箱预览站（sandbox-preview-site §7，T2）：主题画廊/详情/应用市场的卡片动作。档位锁定在
            // Commercial.PERMISSION_TIERS；低于 T2 时 filterExcludePerms 只隐藏本叶，不影响主题组
            { label: t('theme.sandbox.action'), key: 'theme:sandbox', perms: ['theme:sandbox'] },
            // 演示数据管理（A4）：手动导入 / 更新未修改项 / 重置 / 移除，独立于安装与卸载授权
            { label: t('theme.op.seed'), key: 'theme:seed', perms: ['theme:seed'] },
            // 种子导出（theme-seed-export §8）：干跑预演 / 刷新 seed/ / ZIP 打包，核心全版本（D5）
            // 「选择导出」模式的栏目/专题/链接组选择器需对应 list 权限（问卷/表单为 T2，按 perm() 降级为空列表；
            // 专题亦为 T2 但 topic:list 不分级，视图按 commercialTier 门禁）
            { label: t('theme.op.export'), key: 'theme:export', perms: ['theme:export', 'channel:list', 'topic:list', 'linkGroup:list'] },
            { label: t('theme.op.uninstall'), key: 'theme:uninstall', perms: ['theme:uninstall'] },
            // 主题包库（install-flow §6 P3 / Q6）：核心权限码，无商业档位——包库页签可见性/上传/删除；
            // 页签内「安装到站点」沿用上方 theme:install
            { label: t('theme.tab.package'), key: 'theme:package', perms: ['theme:package'] },
          ],
        },
        {
          // 应用市场（remote-marketplace C1/C2）：浏览、安装、账号连接三权分立（D12 先例——
          // 连接是对外授权动作，与安装授权可分离委派）
          label: t('marketplace.tab'),
          key: 'marketplace.key',
          perms: ['marketplace:browse', 'theme:list', 'site:list'],
          children: [
            { label: t('marketplace.op.browse'), key: 'marketplace:browse' },
            { label: t('marketplace.op.install'), key: 'marketplace:install', perms: ['marketplace:install'] },
            { label: t('marketplace.op.connect'), key: 'marketplace:connect', perms: ['marketplace:connect'] },
          ],
        },
        {
          // 模板（template-manager P1）：自 文件›模板文件 迁入设计组；权限码沿用 webFileTemplate:*，不迁移角色数据
          label: t('menu.design.template'),
          key: 'webFileTemplate.key',
          perms: ['webFileTemplate:page', 'webFileTemplate:list'],
          children: [
            { label: t('list'), key: 'webFileTemplate:page' },
            { label: t('add'), key: 'webFileTemplate:create', perms: ['webFileTemplate:create'] },
            { label: t('edit'), key: 'webFileTemplate:update', perms: ['webFileTemplate:update', 'webFileTemplate:show'] },
            { label: t('webFile.op.mkdir'), key: 'webFileTemplate:mkdir', perms: ['webFileTemplate:mkdir'] },
            { label: t('webFile.op.copy'), key: 'webFileTemplate:copy', perms: ['webFileTemplate:copy'] },
            { label: t('webFile.op.move'), key: 'webFileTemplate:move', perms: ['webFileTemplate:move'] },
            { label: t('webFile.op.upload'), key: 'webFileTemplate:upload', perms: ['webFileTemplate:upload'] },
            { label: t('webFile.op.uploadZip'), key: 'webFileTemplate:uploadZip', perms: ['webFileTemplate:uploadZip'] },
            { label: t('webFile.op.downloadZip'), key: 'webFileTemplate:downloadZip', perms: ['webFileTemplate:downloadZip'] },
            { label: t('delete'), key: 'webFileTemplate:delete', perms: ['webFileTemplate:delete'] },
          ],
        },
        {
          label: t('menu.design.page'),
          key: 'pageRegion.key',
          perms: ['pageRegion:show', 'shape:list'],
          children: [
            { label: t('region.op.edit'), key: 'pageRegion:show' },
            { label: t('region.op.save'), key: 'pageRegion:update', perms: ['pageRegion:update'] },
          ],
        },
        {
          label: t('menu.design.shape'),
          key: 'shape.key',
          perms: ['shape:list'],
          children: [{ label: t('list'), key: 'shape:list' }],
        },
        {
          label: t('menu.design.health'),
          key: 'composition.health.key',
          perms: ['composition:health', 'pageRegion:show', 'shape:list'],
          children: [
            { label: t('list'), key: 'composition:health' },
            { label: t('composition.op.rescan'), key: 'composition:rescan', perms: ['composition:rescan'] },
          ],
        },
      ],
    },
    {
      label: t('menu.file'),
      key: 'menu.file.key',
      perms: ['menu.file'],
      children: [
        {
          label: t('menu.file.webFileUpload'),
          key: 'webFileUpload.key',
          perms: ['webFileUpload:page', 'webFileUpload:list'],
          children: [
            { label: t('list'), key: 'webFileUpload:page' },
            { label: t('add'), key: 'webFileUpload:create', perms: ['webFileUpload:create'] },
            { label: t('edit'), key: 'webFileUpload:update', perms: ['webFileUpload:update', 'webFileUpload:show'] },
            { label: t('webFile.op.mkdir'), key: 'webFileUpload:mkdir', perms: ['webFileUpload:mkdir'] },
            { label: t('webFile.op.copy'), key: 'webFileUpload:copy', perms: ['webFileUpload:copy'] },
            { label: t('webFile.op.move'), key: 'webFileUpload:move', perms: ['webFileUpload:move'] },
            { label: t('webFile.op.upload'), key: 'webFileUpload:upload', perms: ['webFileUpload:upload'] },
            { label: t('webFile.op.uploadZip'), key: 'webFileUpload:uploadZip', perms: ['webFileUpload:uploadZip'] },
            { label: t('webFile.op.downloadZip'), key: 'webFileUpload:downloadZip', perms: ['webFileUpload:downloadZip'] },
            { label: t('delete'), key: 'webFileUpload:delete', perms: ['webFileUpload:delete'] },
          ],
        },
        {
          label: t('menu.file.webFileHtml'),
          key: 'webFileHtml.key',
          perms: ['webFileHtml:page', 'webFileHtml:list'],
          children: [
            { label: t('list'), key: 'webFileHtml:page' },
            { label: t('add'), key: 'webFileHtml:create', perms: ['webFileHtml:create'] },
            { label: t('edit'), key: 'webFileHtml:update', perms: ['webFileHtml:update', 'webFileHtml:show'] },
            { label: t('webFile.op.mkdir'), key: 'webFileHtml:mkdir', perms: ['webFileHtml:mkdir'] },
            { label: t('webFile.op.copy'), key: 'webFileHtml:copy', perms: ['webFileHtml:copy'] },
            { label: t('webFile.op.move'), key: 'webFileHtml:move', perms: ['webFileHtml:move'] },
            { label: t('webFile.op.upload'), key: 'webFileHtml:upload', perms: ['webFileHtml:upload'] },
            { label: t('webFile.op.uploadZip'), key: 'webFileHtml:uploadZip', perms: ['webFileHtml:uploadZip'] },
            { label: t('webFile.op.downloadZip'), key: 'webFileHtml:downloadZip', perms: ['webFileHtml:downloadZip'] },
            { label: t('delete'), key: 'webFileHtml:delete', perms: ['webFileHtml:delete'] },
          ],
        },
        {
          // 备份管理一屏承载四种备份（console-menu D24）：路由只由 backup:page 把守，四个权限族原样保留，
          // 页签按各自的 :list 码显示。每种备份的“列表”叶以 :list 码为 key，重新打开角色时据此回勾
          label: t('menu.file.backup'),
          key: 'backup.key',
          perms: ['backup:page'],
          children: [
            {
              label: t('backup.tab.templates'),
              key: 'backupTemplates.key',
              perms: ['backupTemplates:list'],
              children: [
                { label: t('list'), key: 'backupTemplates:list' },
                { label: t('backupTemplates.op.backup'), key: 'backupTemplates:backup', perms: ['backupTemplates:backup'] },
                { label: t('backupTemplates.op.restore'), key: 'backupTemplates:restore', perms: ['backupTemplates:restore'] },
                { label: t('delete'), key: 'backupTemplates:delete', perms: ['backupTemplates:delete'] },
              ],
            },
            {
              label: t('backup.tab.uploads'),
              key: 'backupUploads.key',
              perms: ['backupUploads:list'],
              children: [
                { label: t('list'), key: 'backupUploads:list' },
                { label: t('backupUploads.op.backup'), key: 'backupUploads:backup', perms: ['backupUploads:backup'] },
                { label: t('backupUploads.op.restore'), key: 'backupUploads:restore', perms: ['backupUploads:restore'] },
                { label: t('delete'), key: 'backupUploads:delete', perms: ['backupUploads:delete'] },
              ],
            },
            {
              label: t('backup.tab.incremental'),
              key: 'incrementalUploads.key',
              perms: ['incrementalUploads:list'],
              children: [
                { label: t('list'), key: 'incrementalUploads:list' },
                { label: t('incrementalUploads.op.backup'), key: 'incrementalUploads:backup', perms: ['incrementalUploads:backup'] },
                { label: t('incrementalUploads.op.merge'), key: 'incrementalUploads:merge', perms: ['incrementalUploads:merge'] },
                { label: t('incrementalUploads.op.restore'), key: 'incrementalUploads:restore', perms: ['incrementalUploads:restore'] },
                { label: t('delete'), key: 'incrementalUploads:delete', perms: ['incrementalUploads:delete'] },
              ],
            },
            {
              label: t('backup.tab.database'),
              key: 'backupDatabase.key',
              perms: ['backupDatabase:list'],
              children: [
                { label: t('list'), key: 'backupDatabase:list' },
                { label: t('backupDatabase.op.backup'), key: 'backupDatabase:backup', perms: ['backupDatabase:backup'] },
                { label: t('backupDatabase.op.restore'), key: 'backupDatabase:restore', perms: ['backupDatabase:restore'] },
                { label: t('delete'), key: 'backupDatabase:delete', perms: ['backupDatabase:delete'] },
              ],
            },
          ],
        },
      ],
    },
    {
      label: t('menu.user'),
      key: 'menu.user.key',
      perms: ['menu.user'],
      children: [
        {
          label: t('menu.user.user'),
          key: 'user.key',
          perms: ['user:page', 'user:list', 'role:list', 'group:list', 'org:list'],
          children: [
            { label: t('list'), key: 'user:page' },
            { label: t('add'), key: 'user:create', perms: ['user:create'] },
            { label: t('edit'), key: 'user:update', perms: ['user:update', 'user:show'] },
            { label: t('changePassword'), key: 'user:updatePassword', perms: ['user:updatePassword', 'user:show'] },
            { label: t('updateStatus'), key: 'user:updateStatus', perms: ['user:updateStatus', 'user:show'] },
            { label: t('permissionSettings'), key: 'user:updatePermission', perms: ['user:updatePermission', 'user:show'] },
            { label: t('delete'), key: 'user:delete', perms: ['user:delete'] },
          ],
        },
        {
          label: t('menu.user.role'),
          key: 'role.key',
          perms: ['role:page', 'role:list', 'role:validation', 'channel:list'],
          children: [
            { label: t('list'), key: 'role:page' },
            { label: t('add'), key: 'role:create', perms: ['role:create'] },
            { label: t('edit'), key: 'role:update', perms: ['role:update', 'role:show'] },
            { label: t('permissionSettings'), key: 'role:updatePermission', perms: ['role:updatePermission', 'role:show'] },
            { label: t('delete'), key: 'role:delete', perms: ['role:delete'] },
          ],
        },
        {
          label: t('menu.user.org'),
          key: 'org.key',
          perms: ['org:page', 'org:list'],
          children: [
            { label: t('list'), key: 'org:page' },
            { label: t('add'), key: 'org:create', perms: ['org:create'] },
            { label: t('edit'), key: 'org:update', perms: ['org:update', 'org:show'] },
            // channel:list：权限设置抽屉的文章权限、栏目权限两棵树要取栏目列表
            { label: t('permissionSettings'), key: 'org:updatePermission', perms: ['org:updatePermission', 'org:show', 'channel:list'] },
            { label: t('delete'), key: 'org:delete', perms: ['org:delete'] },
            { label: t('tidyTree'), key: 'org:tidyTree', perms: ['org:tidyTree'] },
          ],
        },
        {
          label: t('menu.user.group'),
          key: 'group.key',
          perms: ['group:page', 'group:list'],
          children: [
            { label: t('list'), key: 'group:page' },
            { label: t('add'), key: 'group:create', perms: ['group:create'] },
            { label: t('edit'), key: 'group:update', perms: ['group:update', 'group:show'] },
            { label: t('permissionSettings'), key: 'group:updatePermission', perms: ['group:updatePermission', 'group:show'] },
            { label: t('delete'), key: 'group:delete', perms: ['group:delete'] },
          ],
        },
        {
          label: t('menu.user.notification'),
          key: 'notification.key',
          perms: ['notification:send'],
          children: [{ label: t('notification.op.send'), key: 'notification:send' }],
        },
      ],
    },
    {
      label: t('menu.config'),
      key: 'menu.config.key',
      perms: ['menu.config'],
      children: [
        {
          label: t('menu.config.globalSettings'),
          key: 'config.key',
          perms: ['config:show'],
          children: [
            { label: t('config.settings.base'), key: 'config:base:update', perms: ['config:base:update', 'site:list'] },
            { label: t('config.settings.upload'), key: 'config:upload:update', perms: ['config:upload:update'] },
            {
              label: t('config.settings.uploadStorage'),
              key: 'config:uploadStorage:update',
              perms: ['config:uploadStorage:show', 'config:uploadStorage:update'],
            },
            {
              label: t('config.settings.htmlStorage'),
              key: 'config:htmlStorage:update',
              perms: ['config:htmlStorage:show', 'config:htmlStorage:update'],
            },
            {
              label: t('config.settings.templateStorage'),
              key: 'config:templateStorage:update',
              perms: ['config:templateStorage:show', 'config:templateStorage:update'],
            },
            {
              label: t('config.settings.packageStorage'),
              key: 'config:packageStorage:update',
              perms: ['config:packageStorage:show', 'config:packageStorage:update'],
            },
            { label: t('config.settings.register'), key: 'config:register:update', perms: ['config:register:update'] },
            { label: t('config.settings.security'), key: 'config:security:update', perms: ['config:security:update'] },
            { label: t('config.settings.oauth'), key: 'config:oauth:update', perms: ['config:oauth:show', 'config:oauth:update'] },
            { label: t('config.settings.sms'), key: 'config:sms:update', perms: ['config:sms:show', 'config:sms:update'] },
            { label: t('config.settings.email'), key: 'config:email:update', perms: ['config:email:show', 'config:email:update'] },
            { label: t('config.settings.ai'), key: 'config:ai:update', perms: ['config:ai:show', 'config:ai:update'] },
            // generator:html：保存后重新生成所有站点的首页静态页（html-all-home）
            { label: t('config.settings.grey'), key: 'config:grey:update', perms: ['config:grey:show', 'config:grey:update', 'generator:html'] },
            { label: t('config.settings.customs'), key: 'config:customs:update', perms: ['config:customs:update'] },
          ],
        },
        {
          label: t('menu.config.siteSettings'),
          key: 'siteSettings.key',
          // dict:list：站点语言下拉取自字典 sys_site_lang（缺权限时降级为自由输入）
          // org:list：挂载即取组织树（站点所属组织）
          perms: ['siteSettings:show', 'dict:list', 'org:list'],
          children: [
            { label: t('site.settings.base'), key: 'siteSettings:base:update', perms: ['siteSettings:base:update', 'site:theme'] },
            { label: t('site.settings.permalink'), key: 'siteSettings:permalink:update', perms: ['siteSettings:permalink:update'] },
            // generator:html：保存后重新生成本站全部静态页（html-all）
            { label: t('site.settings.html'), key: 'siteSettings:html:update', perms: ['siteSettings:html:show', 'siteSettings:html:update', 'generator:html'] },
            { label: t('site.settings.geo'), key: 'siteSettings:geo:update', perms: ['siteSettings:geo:update'] },
            { label: t('site.settings.watermark'), key: 'siteSettings:watermark:update', perms: ['siteSettings:watermark:update'] },
            { label: t('site.settings.editor'), key: 'siteSettings:editor:update', perms: ['siteSettings:editor:update'] },
            { label: t('site.settings.ai'), key: 'siteSettings:ai:update', perms: ['siteSettings:ai:update'] },
            { label: t('site.settings.messageBoard'), key: 'siteSettings:messageBoard:update', perms: ['siteSettings:messageBoard:update'] },
            { label: t('site.settings.comment'), key: 'siteSettings:comment:update', perms: ['siteSettings:comment:update'] },
            { label: t('site.settings.disclosure'), key: 'siteSettings:disclosure:update', perms: ['siteSettings:disclosure:update'] },
            { label: t('site.settings.mailbox'), key: 'siteSettings:mailbox:update', perms: ['siteSettings:mailbox:update'] },
            { label: t('site.settings.interview'), key: 'siteSettings:interview:update', perms: ['siteSettings:interview:update'] },
            { label: t('site.settings.census'), key: 'siteSettings:census:update', perms: ['siteSettings:census:update'] },
            { label: t('site.settings.accessibility'), key: 'siteSettings:accessibility:update', perms: ['siteSettings:accessibility:update'] },
            { label: t('site.settings.customs'), key: 'siteSettings:customs:update', perms: ['siteSettings:customs:update'] },
          ],
        },
        {
          // dictType:list / dict:list：自定义字段的单选/多选/下拉类型须选择所绑定的字典类型，并以其条目为默认值候选（FieldAttribute.vue）
          label: t('menu.config.model'),
          key: 'model.key',
          perms: ['model:page', 'model:list', 'dictType:list', 'dict:list'],
          children: [
            { label: t('list'), key: 'model:page' },
            { label: t('add'), key: 'model:create', perms: ['model:create'] },
            { label: t('edit'), key: 'model:update', perms: ['model:update', 'model:show'] },
            { label: t('delete'), key: 'model:delete', perms: ['model:delete'] },
          ],
        },
        {
          // 字典：类型与条目同屏（docs/spec/dictionary）——dict:* 为条目权限，dictType:* 为类型权限
          label: t('menu.config.dict'),
          key: 'dict.key',
          perms: ['dict:page', 'dict:list', 'dict:validation', 'dictType:list', 'dictType:validation'],
          children: [
            { label: t('list'), key: 'dict:page' },
            { label: t('add'), key: 'dict:create', perms: ['dict:create'] },
            { label: t('edit'), key: 'dict:update', perms: ['dict:update', 'dict:show'] },
            { label: t('delete'), key: 'dict:delete', perms: ['dict:delete'] },
            { label: t('dictType.op.create'), key: 'dictType:create', perms: ['dictType:create'] },
            { label: t('dictType.op.update'), key: 'dictType:update', perms: ['dictType:update', 'dictType:show'] },
            { label: t('dictType.op.delete'), key: 'dictType:delete', perms: ['dictType:delete'] },
          ],
        },
        {
          label: t('menu.config.performanceType'),
          key: 'performanceType.key',
          perms: ['performanceType:page', 'performanceType:list'],
          children: [
            { label: t('list'), key: 'performanceType:page' },
            { label: t('add'), key: 'performanceType:create', perms: ['performanceType:create'] },
            { label: t('edit'), key: 'performanceType:update', perms: ['performanceType:update', 'performanceType:show'] },
            { label: t('delete'), key: 'performanceType:delete', perms: ['performanceType:delete'] },
          ],
        },
        // 配置 = 定义、系统 = 运行（console-menu R8）：流程模型、敏感词、易错词属“定义”，流程实例 / 流程历史留在系统
        {
          label: t('menu.config.processModel'),
          key: 'processModel.key',
          perms: ['processModel:page', 'processModel:list', 'processDefinition:list', 'dict:list', 'processDefinition:xml'],
          children: [
            { label: t('list'), key: 'processModel:page' },
            { label: t('add'), key: 'processModel:create', perms: ['processModel:create'] },
            { label: t('edit'), key: 'processModel:update', perms: ['processModel:update', 'processModel:show', 'processModel:validate'] },
            { label: t('processModel.op.deploy'), key: 'processModel:deploy', perms: ['processModel:deploy'] },
            { label: t('delete'), key: 'processModel:delete', perms: ['processModel:delete', 'processDefinition:delete'] },
          ],
        },
        {
          label: t('menu.config.sensitiveWord'),
          key: 'sensitiveWord.key',
          perms: ['sensitiveWord:page', 'sensitiveWord:list'],
          children: [
            { label: t('list'), key: 'sensitiveWord:page' },
            { label: t('add'), key: 'sensitiveWord:create', perms: ['sensitiveWord:create'] },
            { label: t('edit'), key: 'sensitiveWord:update', perms: ['sensitiveWord:update', 'sensitiveWord:show'] },
            { label: t('delete'), key: 'sensitiveWord:delete', perms: ['sensitiveWord:delete'] },
          ],
        },
        {
          label: t('menu.config.errorWord'),
          key: 'errorWord.key',
          perms: ['errorWord:page', 'errorWord:list'],
          children: [
            { label: t('list'), key: 'errorWord:page' },
            { label: t('add'), key: 'errorWord:create', perms: ['errorWord:create'] },
            { label: t('edit'), key: 'errorWord:update', perms: ['errorWord:update', 'errorWord:show'] },
            { label: t('delete'), key: 'errorWord:delete', perms: ['errorWord:delete'] },
          ],
        },
      ],
    },
    {
      label: t('menu.system'),
      key: 'menu.system.key',
      perms: ['menu.system'],
      children: [
        {
          label: t('menu.system.site'),
          key: 'site.key',
          perms: ['site:page', 'site:list', 'org:list', 'model:list', 'site:theme', 'dict:list'],
          children: [
            { label: t('list'), key: 'site:page' },
            { label: t('add'), key: 'site:create', perms: ['site:create'] },
            { label: t('edit'), key: 'site:update', perms: ['site:update', 'site:show'] },
            { label: t('site.op.open'), key: 'site:open', perms: ['site:open'] },
            { label: t('site.op.close'), key: 'site:close', perms: ['site:close'] },
            { label: t('delete'), key: 'site:delete', perms: ['site:delete'] },
            { label: t('tidyTree'), key: 'site:tidyTree', perms: ['site:tidyTree'] },
          ],
        },
        {
          label: t('menu.system.generator'),
          key: 'generator.key',
          // 生成管理只负责执行：静态化设置、一键置灰的节点分别在站点设置、全局设置下（console-menu D23）
          perms: ['generator:show', 'task:list', 'task:show', 'task:delete'],
          children: [
            { label: t('generator.op.fulltext.reindexAll'), key: 'generator:fulltext:reindexAll', perms: ['generator:fulltext:reindexAll'] },
            { label: t('generator.op.fulltext.reindexSite'), key: 'generator:fulltext:reindexSite', perms: ['generator:fulltext:reindexSite'] },
            { label: t('generator.html'), key: 'generator:html', perms: ['generator:html'] },
          ],
        },
        {
          label: t('menu.system.scheduleTask'),
          key: 'scheduleTask.key',
          perms: ['scheduleTask:page', 'scheduleTask:list', 'scheduleTask:validation'],
          children: [
            { label: t('list'), key: 'scheduleTask:page' },
            { label: t('add'), key: 'scheduleTask:create', perms: ['scheduleTask:create'] },
            { label: t('edit'), key: 'scheduleTask:update', perms: ['scheduleTask:update', 'scheduleTask:show'] },
            { label: t('delete'), key: 'scheduleTask:delete', perms: ['scheduleTask:delete'] },
            { label: t('scheduleTask.op.run'), key: 'scheduleTask:run', perms: ['scheduleTask:run', 'scheduleTask:show'] },
          ],
        },
        {
          label: t('menu.system.processInstance'),
          key: 'processInstance.key',
          perms: ['processInstance:page', 'processInstance:list', 'processInstance:task', 'dict:list'],
          children: [
            { label: t('list'), key: 'processInstance:page' },
            { label: t('delete'), key: 'processInstance:delete', perms: ['processInstance:delete'] },
          ],
        },
        {
          label: t('menu.system.processHistory'),
          key: 'processHistory.key',
          perms: ['processHistory:page', 'processHistory:list', 'processInstance:task', 'dict:list'],
          children: [
            { label: t('list'), key: 'processHistory:page' },
            { label: t('delete'), key: 'processHistory:delete', perms: ['processHistory:delete'] },
          ],
        },
        {
          label: t('menu.system.importData'),
          key: 'importData:show',
          perms: ['importData:show'],
        },
        // {
        //   label: t('menu.system.task'),
        //   key: 'task.key',
        //   perms: ['task:page', 'task:list'],
        //   children: [
        //     { label: t('list'), key: 'task:page' },
        //     { label: t('add'), key: 'task:create', perms: ['task:create'] },
        //     { label: t('edit'), key: 'task:update', perms: ['task:update', 'task:show'] },
        //     { label: t('delete'), key: 'task:delete', perms: ['task:delete'] },
        //   ],
        // },
      ],
    },
    {
      label: t('menu.log'),
      key: 'menu.log.key',
      perms: ['menu.log'],
      children: [
        {
          label: t('menu.log.loginLog'),
          key: 'loginLog.key',
          perms: ['loginLog:page', 'loginLog:list', 'loginLog:show'],
          children: [
            { label: t('list'), key: 'loginLog:page' },
            { label: t('delete'), key: 'loginLog:delete', perms: ['loginLog:delete'] },
          ],
        },
        {
          label: t('menu.log.operationLog'),
          key: 'operationLog.key',
          perms: ['operationLog:page', 'operationLog:list', 'operationLog:show'],
          children: [
            { label: t('list'), key: 'operationLog:page' },
            { label: t('delete'), key: 'operationLog:delete', perms: ['operationLog:delete'] },
          ],
        },
        {
          label: t('menu.log.shortMessage'),
          key: 'shortMessage.key',
          perms: ['shortMessage:page', 'shortMessage:list', 'shortMessage:show'],
          children: [
            { label: t('list'), key: 'shortMessage:page' },
            { label: t('delete'), key: 'shortMessage:delete', perms: ['shortMessage:delete'] },
          ],
        },
        {
          label: t('menu.log.aiLog'),
          key: 'aiLog.key',
          perms: ['aiLog:page', 'aiLog:list'],
          children: [
            { label: t('list'), key: 'aiLog:list' },
            { label: t('aiLog.op.assist'), key: 'ai:assist', perms: ['ai:assist'] },
            { label: t('aiLog.op.translate'), key: 'ai:translate', perms: ['ai:translate'] },
          ],
        },
      ],
    },
  ];
  return filterExcludePerms(perms);
}

export function getModelData(): any {
  return {
    article: {
      mains: [
        { code: 'title', must: true, show: true, double: false, required: true },
        { code: 'slug', must: false, show: true, double: false, required: false },
        { code: 'subtitle', must: false, show: false, double: false, required: false },
        { code: 'fullTitle', must: false, show: false, double: false, required: false },
        { code: 'linkUrl', must: false, show: true, double: false, required: false },
        { code: 'tags', must: false, show: false, double: true, required: false },
        { code: 'source', must: false, show: false, double: true, required: false },
        { code: 'topics', must: false, show: true, double: false, required: false, commercialTier: 2 },
        { code: 'seoKeywords', must: false, show: false, double: false, required: false },
        { code: 'seoDescription', must: false, show: true, double: false, required: false },
        { code: 'author', must: false, show: false, double: true, required: false },
        { code: 'editor', must: false, show: false, double: true, required: false },
        { code: 'image', must: false, show: true, double: false, required: false, type: 'image', imageWidth: 300, imageHeight: 200, imageMode: 'manual' },
        { code: 'file', must: false, show: false, double: false, required: false },
        { code: 'video', must: false, show: false, double: false, required: false },
        { code: 'audio', must: false, show: false, double: false, required: false },
        { code: 'doc', must: false, show: false, double: false, required: false },
        { code: 'imageList', must: false, show: false, double: false, required: false, type: 'imageList', imageListType: 'pictureCard', imageMaxWidth: 1920, imageMaxHeight: 1920 },
        { code: 'fileList', must: false, show: false, double: false, required: false },
        { code: 'text', must: false, show: true, double: false, required: true, type: 'editor', editorType: 1, editorSwitch: true },
        { code: 'related', must: false, show: true, double: false, required: false },
      ],
      asides: [
        { code: 'channel', must: true, show: true, required: true },
        { code: 'channels', must: false, show: true, required: false },
        // { code: 'copies', must: false, show: false, required: false },
        { code: 'org', must: false, show: true, required: true },
        { code: 'publishDate', must: true, show: true, required: true },
        { code: 'onlineDate', must: false, show: true, required: false },
        { code: 'offlineDate', must: false, show: true, required: false },
        { code: 'articleTemplate', must: false, show: true, required: false },
        { code: 'articleStaticPath', must: false, show: false, required: false },
        // { code: 'allowComment', must: false, show: true, required: true },
        { code: 'user', must: false, show: false, required: true },
        { code: 'created', must: false, show: false, required: true },
        { code: 'modifiedUser', must: false, show: false, required: false },
        { code: 'modified', must: false, show: false, required: false },
      ],
    },
    user: {
      mains: [
        { code: 'location', must: false, show: true, double: false, required: false },
        { code: 'bio', must: false, show: true, double: false, required: false },
      ],
      asides: [],
    },
    blockItem: {
      mains: [
        { code: 'title', must: true, show: true, double: false, required: true },
        { code: 'subtitle', must: false, show: false, double: false, required: false },
        { code: 'description', must: false, show: false, double: false, required: false },
        { code: 'linkUrl', must: false, show: true, double: false, required: false },
        { code: 'image', must: false, show: true, double: false, required: false, type: 'image', imageWidth: 1200, imageHeight: 600, imageMode: 'manual' },
        { code: 'mobileImage', must: false, show: false, double: false, required: false, type: 'image', imageWidth: 1200, imageHeight: 600, imageMode: 'manual' },
        { code: 'video', must: false, show: false, double: false, required: false },
        { code: 'startDate', must: false, show: true, double: false, required: false },
        { code: 'endDate', must: false, show: true, double: false, required: false },
      ],
      asides: [],
    },
    channel: {
      mains: [
        { code: 'name', must: true, show: true, double: true, required: true },
        { code: 'slug', must: true, show: true, double: true, required: false },
        { code: 'linkUrl', must: true, show: true, double: false, required: true },
        { code: 'seoTitle', must: false, show: true, double: true, required: false },
        { code: 'seoKeywords', must: false, show: true, double: true, required: false },
        { code: 'seoDescription', must: false, show: true, double: false, required: false },
        { code: 'image', must: false, show: false, double: false, required: false, type: 'image', imageWidth: 300, imageHeight: 200, imageMode: 'manual' },
        { code: 'channelModel', must: true, show: true, double: true, required: true },
        { code: 'articleModel', must: true, show: true, double: true, required: true },
        // 模板留空即“主题默认”，是有效取值：不设必填，也不可在“系统字段”里改成必填（requiredLocked）
        { code: 'channelTemplate', must: false, show: true, double: true, required: false, requiredLocked: true },
        { code: 'articleTemplate', must: false, show: true, double: true, required: false, requiredLocked: true },
        { code: 'nav', must: false, show: true, double: true, required: true },
        { code: 'real', must: false, show: true, double: true, required: true },
        { code: 'allowSearch', must: false, show: true, double: true, required: true },
        // { code: 'allowComment', must: false, show: true, double: true, required: true },
        // { code: 'allowContribute', must: false, show: true, double: true, required: true },
        { code: 'book', must: false, show: false, double: true, required: true },
        { code: 'text', must: false, show: false, double: false, required: false, type: 'editor', editorType: 1, editorSwitch: true },
      ],
      asides: [
        { code: 'parent', must: true, show: true, required: false },
        { code: 'type', must: true, show: true, required: true },
        { code: 'processKey', must: false, show: true, required: false },
        { code: 'performanceType', must: false, show: true, required: false, commercialTier: 2 },
        { code: 'channelStaticPath', must: false, show: false, required: false },
        { code: 'pageSize', must: true, show: true, required: true },
        { code: 'orderDesc', must: true, show: true, required: true },
      ],
    },
    topic: {
      mains: [
        { code: 'name', must: true, show: true, double: true, required: true },
        { code: 'slug', must: true, show: true, double: true, required: false },
        { code: 'seoTitle', must: false, show: true, double: true, required: false },
        { code: 'seoKeywords', must: false, show: true, double: true, required: false },
        { code: 'seoDescription', must: false, show: true, double: false, required: false },
        { code: 'image', must: false, show: true, double: true, required: false, type: 'image', imageWidth: 300, imageHeight: 200, imageMode: 'manual' },
        { code: 'text', must: false, show: false, double: false, required: false, type: 'editor', editorType: 1, editorSwitch: true },
      ],
      asides: [
        { code: 'parent', must: true, show: true, required: false },
        { code: 'topicModel', must: true, show: true, required: true },
        { code: 'topicTemplate', must: false, show: true, required: false, requiredLocked: true },
        { code: 'topicStaticPath', must: false, show: false, required: false },
        { code: 'pageSize', must: true, show: true, required: true },
        { code: 'orderDesc', must: true, show: true, required: true },
      ],
    },
  };
}

export function mergeModelFields(defaultFields: any[], str: string | null | undefined, type: string): any[] {
  let fields = JSON.parse(str || '[]');
  const defaults = defaultFields.map((item: any) => ({ ...item, label: `${type}.${item.code}` }));
  // 去除默认字段中不存在的字段
  fields = fields.filter((field: any) => defaults.findIndex((item) => item.code === field.code) !== -1);
  defaults.forEach((item, i) => {
    const index = fields.findIndex((it: any) => it.code === item.code);
    if (index !== -1) {
      // 加上缺失属性，覆盖不可改属性。requiredLocked 的字段，必填与否以默认值为准：模型里早先存下的 required 不再生效
      fields[index] = { ...item, ...fields[index], must: item.must, label: item.label, type: item.type, requiredLocked: item.requiredLocked };
      if (item.requiredLocked) {
        fields[index].required = item.required;
      }
    } else {
      // 加上没有的字段
      fields.splice(i, 0, item);
    }
  });
  return fields;
}

export function arr2obj(arr: any[]): Record<string, any> {
  const obj: Record<string, any> = {};
  arr.forEach((item: any) => {
    obj[item.code] = item;
  });
  return obj;
}

export const logModules = [
  'article',
  'channel',
  'topic',
  'articleTopic',
  'blockItem',
  'link',
  'linkGroup',
  'dict',
  'tag',
  'form',
  'attachment',
  'resourceCategory',
  'fulltext',
  'html',
  'messageBoard',
  'comment',
  'aiModeration',
  'notification',
  'message',
  'messageBoardType',
  'disclosure',
  'mailbox',
  'mailboxType',
  'interview',
  'interviewMessage',
  'interviewQuestion',
  'census',
  'censusIssue',
  'wechatAccount',
  'wechatArticle',
  'wechatMaterial',
  'wechatDraft',
  'wechatPublish',
  'wechatMenu',
  'wechatFan',
  'wechatTag',
  'wechatReply',
  'wechatMessage',
  'wechatQrcode',
  'wechatTemplate',
  'wechatTemplateLog',
  'wechatMass',
  'vote',
  'survey',
  'collection',
  'example',
  'webFileTemplate',
  'webFileUpload',
  'webFileHtml',
  'backupTemplates',
  'backupUploads',
  'incrementalUploads',
  'backupDatabase',
  'config',
  'siteSettings',
  'theme',
  'themePackage',
  'marketplace',
  'themeSandbox',
  'composition',
  'pageRegion',
  'model',
  'block',
  'dictType',
  'formType',
  'performanceType',
  'user',
  'role',
  'org',
  'group',
  'shortMessage',
  'loginLog',
  'operationLog',
  'site',
  'process',
  'task',
  'scheduleTask',
  'sensitiveWord',
  'errorWord',
  'importData',
  'personal',
];

export const logNames = [
  'article.create',
  'article.update',
  'article.submit',
  'article.pass',
  'article.reject',
  'article.delegate',
  'article.transfer',
  'article.completelyDelete',
  'article.delete',
  'article.restore',
  'article.updateOrder',
  'article.sticky',
  'article.move',
  'article.export',
  'article.import',
  'article.importStop',
  'article.internalPush',
  'article.externalPush',
  'article.offline',
  'article.back',
  'article.archive',
  'article.updateI18n',
  'article.createTranslation',
  'article.aiTranslate',
  'article.translateBatch',
  'article.translateBatchStop',

  'channel.create',
  'channel.batchCreate',
  'channel.update',
  'channel.batchUpdate',
  'channel.updateNav',
  'channel.batchMove',
  'channel.batchMerge',
  'channel.delete',
  'channel.move',
  'channel.tidyTree',
  'channel.updateReal',
  'channel.translateBatch',

  'blockItem.create',
  'blockItem.update',
  'blockItem.updateOrder',
  'blockItem.delete',

  'dict.create',
  'dict.update',
  'dict.updateOrder',
  'dict.delete',

  'tag.create',
  'tag.update',
  'tag.delete',

  'topic.create',
  'topic.update',
  'topic.delete',
  'topic.move',
  'topic.tidyTree',

  'articleTopic.bulkReplace',

  'form.create',
  'form.update',
  'form.updateOrder',
  'form.delete',
  'form.pass',
  'form.reject',
  'form.completelyDelete',
  'form.submit',
  'form.back',
  'form.delegate',
  'form.transfer',

  'attachment.delete',
  'attachment.deleteUnused',
  'attachment.share',
  'attachment.unshare',
  'attachment.favorite',
  'attachment.unfavorite',
  'attachment.favoriteCategory',
  'attachment.importWebFile',

  'resourceCategory.create',
  'resourceCategory.update',
  'resourceCategory.delete',

  'fulltext.reindexAll',
  'fulltext.reindexSite',

  'html.updateAll',
  'html.updateHome',
  'html.updateChannel',
  'html.updateArticle',
  'html.updateAllHome',

  'messageBoard.create',
  'messageBoard.update',
  'messageBoard.updateStatus',
  'messageBoard.delete',

  'comment.update',
  'comment.updateStatus',
  'comment.delete',

  'aiModeration.retry',

  'notification.sendSystem',

  'message.send',
  'message.bulkSend',
  'message.deleteConversation',

  'messageBoardType.create',
  'messageBoardType.update',
  'messageBoardType.updateOrder',
  'messageBoardType.delete',

  'link.create',
  'link.update',
  'link.updateOrder',
  'link.delete',

  'linkGroup.create',
  'linkGroup.update',
  'linkGroup.updateOrder',
  'linkGroup.delete',

  'disclosure.create',
  'disclosure.update',
  'disclosure.accept',
  'disclosure.extend',
  'disclosure.correct',
  'disclosure.correctReceived',
  'disclosure.reply',
  'disclosure.delete',

  'mailbox.create',
  'mailbox.update',
  'mailbox.accept',
  'mailbox.reject',
  'mailbox.extend',
  'mailbox.reply',
  'mailbox.publish',
  'mailbox.unpublish',
  'mailbox.delete',

  'mailboxType.create',
  'mailboxType.update',
  'mailboxType.updateOrder',
  'mailboxType.delete',

  'interview.create',
  'interview.update',
  'interview.begin',
  'interview.end',
  'interview.publish',
  'interview.unpublish',
  'interview.delete',
  'interviewMessage.create',
  'interviewMessage.update',
  'interviewMessage.delete',
  'interviewQuestion.create',
  'interviewQuestion.update',
  'interviewQuestion.review',
  'interviewQuestion.approve',
  'interviewQuestion.reject',
  'interviewQuestion.delete',

  'census.create',
  'census.stop',
  'census.score',
  'census.delete',
  'censusIssue.process',
  'censusIssue.delete',

  'wechatAccount.create',
  'wechatAccount.update',
  'wechatAccount.updateOrder',
  'wechatAccount.delete',

  'wechatArticle.push',
  'wechatArticle.publish',
  'wechatArticle.refresh',
  'wechatArticle.delete',

  'wechatMaterial.upload',
  'wechatMaterial.delete',

  'wechatDraft.publish',
  'wechatDraft.delete',

  'wechatPublish.delete',

  'wechatMenu.update',
  'wechatMenu.delete',

  'wechatFan.remark',
  'wechatFan.tagging',
  'wechatFan.blacklist',

  'wechatTag.create',
  'wechatTag.update',
  'wechatTag.delete',

  'wechatReply.create',
  'wechatReply.update',
  'wechatReply.updateOrder',
  'wechatReply.delete',

  'wechatMessage.delete',

  'wechatQrcode.create',
  'wechatQrcode.update',
  'wechatQrcode.delete',

  'wechatTemplate.send',
  'wechatTemplate.delete',

  'wechatTemplateLog.delete',

  'wechatMass.send',
  'wechatMass.preview',
  'wechatMass.refresh',
  'wechatMass.revoke',
  'wechatMass.delete',

  'vote.create',
  'vote.update',
  'vote.updateOrder',
  'vote.delete',

  'survey.create',
  'survey.update',
  'survey.updateOrder',
  'survey.delete',

  'collection.create',
  'collection.update',
  'collection.updateOrder',
  'collection.start',
  'collection.stop',
  'collection.clearRecords',
  'collection.delete',
  'collection.pause',

  'example.create',
  'example.update',
  'example.delete',

  'webFileTemplate.create',
  'webFileTemplate.update',
  'webFileTemplate.mkdir',
  'webFileTemplate.rename',
  'webFileTemplate.copy',
  'webFileTemplate.move',
  'webFileTemplate.upload',
  'webFileTemplate.uploadZip',
  'webFileTemplate.delete',
  'webFileTemplate.restoreHistory',

  'webFileUpload.create',
  'webFileUpload.update',
  'webFileUpload.mkdir',
  'webFileUpload.rename',
  'webFileUpload.copy',
  'webFileUpload.move',
  'webFileUpload.upload',
  'webFileUpload.uploadZip',
  'webFileUpload.delete',

  'webFileHtml.create',
  'webFileHtml.update',
  'webFileHtml.mkdir',
  'webFileHtml.rename',
  'webFileHtml.copy',
  'webFileHtml.move',
  'webFileHtml.upload',
  'webFileHtml.uploadZip',
  'webFileHtml.delete',

  'backupTemplates.backup',
  'backupTemplates.restore',
  'backupTemplates.delete',

  'backupUploads.backup',
  'backupUploads.restore',
  'backupUploads.delete',

  'incrementalUploads.backup',
  'incrementalUploads.restore',
  'incrementalUploads.merge',
  'incrementalUploads.delete',

  'backupDatabase.backup',
  'backupDatabase.restore',
  'backupDatabase.delete',

  'config.updateBase',
  'config.updateUpload',
  'config.updateGrey',
  'config.updateRegister',
  'config.updateSecurity',
  'config.updateOauth',
  'config.updateSms',
  'config.sendSms',
  'config.updateEmail',
  'config.sendEmail',
  'config.updateAi',
  'config.testAi',
  'config.testAiMt',
  'config.updateUploadStorage',
  'config.updateHtmlStorage',
  'config.updateTemplateStorage',
  'config.updatePackageStorage',
  'config.updateCustoms',
  'config.applySecurityL3',

  'siteSettings.updateBase',
  'siteSettings.updateWatermark',
  'siteSettings.updateEditor',
  'siteSettings.updateMessageBoard',
  'siteSettings.updateDisclosure',
  'siteSettings.updateMailbox',
  'siteSettings.updateInterview',
  'siteSettings.updateCensus',
  'siteSettings.updateAccessibility',
  'siteSettings.updatePermalink',
  'siteSettings.updateAi',
  'siteSettings.updateGeo',
  'siteSettings.updateCustoms',
  'siteSettings.updateHtml',
  'siteSettings.pushTest',
  'siteSettings.updateComment',

  'theme.install',
  'theme.update',
  'theme.seedImport',
  'theme.seedRemove',
  'theme.uninstall',
  'theme.activate',
  'theme.settings',
  'theme.export',
  'theme.registerDir',

  'themePackage.upload',
  'themePackage.install',
  'themePackage.delete',

  'marketplace.connect',
  'marketplace.disconnect',
  'marketplace.install',
  'marketplace.update',

  'themeSandbox.create',
  'themeSandbox.createFromStore',
  'themeSandbox.discard',

  'composition.rescan',
  'composition.purgeCache',
  'composition.repairSeedTokens',

  'pageRegion.saveDraft',
  'pageRegion.publish',
  'pageRegion.discard',
  'pageRegion.deleteOverride',
  'pageRegion.restoreSnapshot',
  'pageRegion.restorePanel',
  'pageRegion.deleteArchivedPanel',
  'pageRegion.deleteArchivedPanels',
  'pageRegion.pruneDanglingRefs',

  'model.create',
  'model.update',
  'model.updateOrder',
  'model.delete',

  'block.create',
  'block.update',
  'block.updateOrder',
  'block.delete',

  'dictType.create',
  'dictType.update',
  'dictType.updateOrder',
  'dictType.delete',

  'formType.create',
  'formType.update',
  'formType.updateOrder',
  'formType.delete',

  'performanceType.create',
  'performanceType.update',
  'performanceType.updateOrder',
  'performanceType.delete',

  'user.create',
  'user.update',
  'user.updatePermission',
  'user.updateStatus',
  'user.updatePassword',
  'user.delete',
  'user.rebuildPiiIndex',

  'role.create',
  'role.update',
  'role.updateOrder',
  'role.updatePermission',
  'role.delete',

  'org.create',
  'org.update',
  'org.updatePermission',
  'org.delete',
  'org.move',
  'org.tidyTree',

  'group.create',
  'group.update',
  'group.updatePermission',
  'group.updateOrder',
  'group.delete',

  'shortMessage.delete',

  'loginLog.delete',

  'operationLog.delete',

  'site.create',
  'site.update',
  'site.open',
  'site.close',
  'site.delete',
  'site.move',
  'site.tidyTree',

  'process.createProcessModel',
  'process.updateProcessModel',
  'process.updateProcessXml',
  'process.deployProcessModel',
  'process.deleteProcessModel',
  'process.deleteProcessDefinition',
  'process.deleteProcessHistory',
  'process.deleteProcessInstance',

  'task.delete',
  'task.create',
  'task.update',

  'scheduleTask.create',
  'scheduleTask.update',
  'scheduleTask.updateOrder',
  'scheduleTask.delete',
  'scheduleTask.run',

  'sensitiveWord.create',
  'sensitiveWord.import',
  'sensitiveWord.update',
  'sensitiveWord.delete',

  'errorWord.create',
  'errorWord.import',
  'errorWord.update',
  'errorWord.delete',

  'importData.importChannel',
  'importData.importArticle',
  'importData.deleteCorrespond',
  'importData.testConnection',
  'importData.stop',
  'importData.exportCorrespond',

  'personal.updatePassword',
];

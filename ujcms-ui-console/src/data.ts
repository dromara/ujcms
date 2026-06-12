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
          label: t('menu.personal.password'),
          key: 'password:update',
          perms: ['password:update', 'password:matches'],
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
        {
          label: t('menu.personal.machine.code'),
          key: 'machine:code',
          perms: ['machine:code'],
        },
        {
          label: t('menu.personal.machine.license'),
          key: 'machine:license',
          perms: ['machine:license'],
        },
      ],
    },
    {
      label: t('menu.content'),
      key: 'menu.content.kay',
      perms: ['menu.content'],
      children: [
        {
          label: t('menu.content.article'),
          key: 'article.key',
          perms: ['article:page', 'article:list', 'channel:list', 'org:list', 'dict:list', 'model:list', 'block:list', 'tag:list', 'processInstance:task'],
          children: [
            { label: t('list'), key: 'article:page' },
            { label: t('add'), key: 'article:create', perms: ['article:create', 'article.submit', 'jodConvert:doc', 'jodConvert:library'] },
            { label: t('view'), key: 'article:show', perms: ['article:show'] },
            { label: t('edit'), key: 'article:update', perms: ['article:update', 'article.submit', 'article:show', 'jodConvert:doc', 'jodConvert:library'] },
            { label: t('article.op.recommendTo'), key: 'article:recommendTo', perms: ['article:recommendTo', 'blockItem:create', 'blockItem:delete'] },
            { label: t('article.op.internalPush'), key: 'article:internalPush', perms: ['article:internalPush'] },
            { label: t('article.op.externalPush'), key: 'article:externalPush', perms: ['article:externalPush'] },
            { label: t('article.op.updateOrder'), key: 'article:updateOrder', perms: ['article:updateOrder'] },
            { label: t('article.op.sticky'), key: 'article:sticky', perms: ['article:sticky'] },
            { label: t('article.op.submit'), key: 'article:submit', perms: ['article:submit'] },
            { label: t('article.op.archive'), key: 'article:archive', perms: ['article:archive'] },
            { label: t('article.op.offline'), key: 'article:offline', perms: ['article:offline'] },
            { label: t('delete'), key: 'article:delete', perms: ['article:delete'] },
            { label: t('completelyDelete'), key: 'article:completelyDelete', perms: ['article:completelyDelete'] },
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
          ],
        },
        {
          label: t('menu.content.blockItem'),
          key: 'blockItem.key',
          perms: ['blockItem:page', 'blockItem:list', 'block:list'],
          children: [
            { label: t('list'), key: 'blockItem:page' },
            { label: t('add'), key: 'blockItem:create', perms: ['blockItem:create'] },
            { label: t('edit'), key: 'blockItem:update', perms: ['blockItem:update', 'blockItem:show'] },
            { label: t('delete'), key: 'blockItem:delete', perms: ['blockItem:delete'] },
          ],
        },
        {
          label: t('menu.content.dict'),
          key: 'dict.key',
          perms: ['dict:page', 'dict:list', 'dictType:list'],
          children: [
            { label: t('list'), key: 'dict:page' },
            { label: t('add'), key: 'dict:create', perms: ['dict:create'] },
            { label: t('edit'), key: 'dict:update', perms: ['dict:update', 'dict:show'] },
            { label: t('delete'), key: 'dict:delete', perms: ['dict:delete'] },
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
          label: t('menu.content.form'),
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
          ],
        },
        {
          label: t('menu.content.formReview'),
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
          label: t('menu.content.attachment'),
          key: 'attachment.key',
          perms: ['attachment:page', 'attachment:list'],
          children: [
            { label: t('list'), key: 'attachment:page' },
            { label: t('add'), key: 'attachment:create', perms: ['attachment:create'] },
            { label: t('edit'), key: 'attachment:update', perms: ['attachment:update', 'attachment:show'] },
            { label: t('delete'), key: 'attachment:delete', perms: ['attachment:delete'] },
          ],
        },
        {
          label: t('menu.content.generator'),
          key: 'generator.key',
          perms: ['generator:show', 'siteSettings:html:show', 'task:list', 'task:show', 'task:delete'],
          children: [
            { label: t('generator.op.fulltext.reindexAll'), key: 'generator:fulltext:reindexAll', perms: ['generator:fulltext:reindexAll'] },
            { label: t('generator.op.fulltext.reindexSite'), key: 'generator:fulltext:reindexSite', perms: ['generator:fulltext:reindexSite'] },
            { label: t('generator.html'), key: 'generator:html', perms: ['generator:html'] },
            { label: t('site.settings.html'), key: 'siteSettings:html:update', perms: ['siteSettings:html:update', 'generator:html'] },
            { label: t('config.settings.grey'), key: 'config:grey:update', perms: ['config:grey:update', 'config:grey:show', 'generator:allHtml'] },
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
          label: t('menu.interaction.messageBoard'),
          key: 'messageBoard.key',
          perms: ['messageBoard:page', 'messageBoard:list', 'messageBoardType:list'],
          children: [
            { label: t('list'), key: 'messageBoard:page' },
            { label: t('add'), key: 'messageBoard:create', perms: ['messageBoard:create'] },
            { label: t('edit'), key: 'messageBoard:update', perms: ['messageBoard:update', 'messageBoard:show'] },
            { label: t('updateStatus'), key: 'messageBoard:updateStatus', perms: ['messageBoard:updateStatus'] },
            { label: t('delete'), key: 'messageBoard:delete', perms: ['messageBoard:delete'] },
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
        {
          label: t('menu.interaction.collection'),
          key: 'collection.key',
          perms: ['collection:page', 'collection:list'],
          children: [
            { label: t('list'), key: 'collection:page' },
            { label: t('add'), key: 'collection:create', perms: ['collection:create'] },
            { label: t('edit'), key: 'collection:update', perms: ['collection:update', 'collection:show'] },
            { label: t('delete'), key: 'collection:delete', perms: ['collection:delete'] },
            { label: t('collection.op.start'), key: 'collection:start', perms: ['collection:start'] },
            { label: t('collection.op.stop'), key: 'collection:stop', perms: ['collection:stop'] },
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
      label: t('menu.file'),
      key: 'menu.file.key',
      perms: ['menu.file'],
      children: [
        {
          label: t('menu.file.webFileTemplate'),
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
          label: t('menu.file.backupTemplates'),
          key: 'backupTemplates.key',
          perms: ['backupTemplates:page', 'backupTemplates:list'],
          children: [
            { label: t('list'), key: 'backupTemplates:page' },
            { label: t('backupTemplates.op.backup'), key: 'backupTemplates:backup', perms: ['backupTemplates:backup'] },
            { label: t('backupTemplates.op.restore'), key: 'backupTemplates:restore', perms: ['backupTemplates:restore'] },
            { label: t('delete'), key: 'backupTemplates:delete', perms: ['backupTemplates:delete'] },
          ],
        },
        {
          label: t('menu.file.backupUploads'),
          key: 'backupUploads.key',
          perms: ['backupUploads:page', 'backupUploads:list'],
          children: [
            { label: t('list'), key: 'backupUploads:page' },
            { label: t('backupUploads.op.backup'), key: 'backupUploads:backup', perms: ['backupUploads:backup'] },
            { label: t('backupUploads.op.restore'), key: 'backupUploads:restore', perms: ['backupUploads:restore'] },
            { label: t('delete'), key: 'backupUploads:delete', perms: ['backupUploads:delete'] },
          ],
        },
        {
          label: t('menu.file.incrementalUploads'),
          key: 'incrementalUploads.key',
          perms: ['incrementalUploads:page', 'incrementalUploads:list'],
          children: [
            { label: t('list'), key: 'incrementalUploads:page' },
            { label: t('incrementalUploads.op.backup'), key: 'incrementalUploads:backup', perms: ['incrementalUploads:backup'] },
            { label: t('incrementalUploads.op.merge'), key: 'incrementalUploads:merge', perms: ['incrementalUploads:merge'] },
            { label: t('incrementalUploads.op.restore'), key: 'incrementalUploads:restore', perms: ['incrementalUploads:restore'] },
            { label: t('delete'), key: 'incrementalUploads:delete', perms: ['incrementalUploads:delete'] },
          ],
        },
        {
          label: t('menu.file.backupDatabase'),
          key: 'backupDatabase.key',
          perms: ['backupDatabase:page', 'backupDatabase:list'],
          children: [
            { label: t('list'), key: 'backupDatabase:page' },
            { label: t('backupDatabase.op.backup'), key: 'backupDatabase:backup', perms: ['backupDatabase:backup'] },
            { label: t('backupDatabase.op.restore'), key: 'backupDatabase:restore', perms: ['backupDatabase:restore'] },
            { label: t('delete'), key: 'backupDatabase:delete', perms: ['backupDatabase:delete'] },
          ],
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
            { label: t('config.settings.register'), key: 'config:register:update', perms: ['config:register:update'] },
            { label: t('config.settings.security'), key: 'config:security:update', perms: ['config:security:update'] },
            { label: t('config.settings.sms'), key: 'config:sms:update', perms: ['config:sms:show', 'config:sms:update'] },
            { label: t('config.settings.email'), key: 'config:email:update', perms: ['config:email:show', 'config:email:update'] },
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
            { label: t('config.settings.customs'), key: 'config:customs:update', perms: ['config:customs:update'] },
          ],
        },
        {
          label: t('menu.config.siteSettings'),
          key: 'siteSettings.key',
          perms: ['siteSettings:show'],
          children: [
            { label: t('site.settings.base'), key: 'siteSettings:base:update', perms: ['siteSettings:base:update', 'site:theme'] },
            { label: t('site.settings.watermark'), key: 'siteSettings:watermark:update', perms: ['siteSettings:watermark:update'] },
            { label: t('site.settings.editor'), key: 'siteSettings:editor:update', perms: ['siteSettings:editor:update'] },
            { label: t('site.settings.messageBoard'), key: 'siteSettings:messageBoard:update', perms: ['siteSettings:messageBoard:update'] },
            { label: t('site.settings.customs'), key: 'siteSettings:customs:update', perms: ['siteSettings:customs:update'] },
          ],
        },
        {
          label: t('menu.config.model'),
          key: 'model.key',
          perms: ['model:page', 'model:list'],
          children: [
            { label: t('list'), key: 'model:page' },
            { label: t('add'), key: 'model:create', perms: ['model:create'] },
            { label: t('edit'), key: 'model:update', perms: ['model:update', 'model:show'] },
            { label: t('delete'), key: 'model:delete', perms: ['model:delete'] },
          ],
        },
        {
          label: t('menu.config.block'),
          key: 'block.key',
          perms: ['block:page', 'block:list', 'block:validation'],
          children: [
            { label: t('list'), key: 'block:page' },
            { label: t('add'), key: 'block:create', perms: ['block:create'] },
            { label: t('edit'), key: 'block:update', perms: ['block:update', 'block:show'] },
            { label: t('delete'), key: 'block:delete', perms: ['block:delete'] },
          ],
        },
        {
          label: t('menu.config.dictType'),
          key: 'dictType.key',
          perms: ['dictType:page', 'dictType:list', 'dictType:validation'],
          children: [
            { label: t('list'), key: 'dictType:page' },
            { label: t('add'), key: 'dictType:create', perms: ['dictType:create'] },
            { label: t('edit'), key: 'dictType:update', perms: ['dictType:update', 'dictType:show'] },
            { label: t('delete'), key: 'dictType:delete', perms: ['dictType:delete'] },
          ],
        },
        {
          label: t('menu.config.formType'),
          key: 'formType.key',
          perms: ['formType:page', 'formType:list'],
          children: [
            { label: t('list'), key: 'formType:page' },
            { label: t('add'), key: 'formType:create', perms: ['formType:create'] },
            { label: t('edit'), key: 'formType:update', perms: ['formType:update', 'formType:show'] },
            { label: t('delete'), key: 'formType:delete', perms: ['formType:delete'] },
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
        {
          label: t('menu.config.messageBoardType'),
          key: 'messageBoardType.key',
          perms: ['messageBoardType:page', 'messageBoardType:list'],
          children: [
            { label: t('list'), key: 'messageBoardType:page' },
            { label: t('add'), key: 'messageBoardType:create', perms: ['messageBoardType:create'] },
            { label: t('edit'), key: 'messageBoardType:update', perms: ['messageBoardType:update', 'messageBoardType:show'] },
            { label: t('delete'), key: 'messageBoardType:delete', perms: ['messageBoardType:delete'] },
          ],
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
              perms: ['performanceStatByUser:page', 'performanceStatByUser:list'],
            },
            {
              label: t('menu.stat.performanceStat.byOrg'),
              key: 'performanceStatByOrg:page',
              perms: ['performanceStatByOrg:page', 'performanceStatByOrg:list'],
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
            { label: t('permissionSettings'), key: 'org:updatePermission', perms: ['org:updatePermission', 'org:show'] },
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
      ],
    },
    {
      label: t('menu.log'),
      key: 'menu.log.key',
      perms: ['menu.log'],
      children: [
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
          perms: ['site:page', 'site:list', 'org:list', 'model:list', 'site:theme'],
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
          label: t('menu.system.processModel'),
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
          label: t('menu.system.processInstance'),
          key: 'processInstance.key',
          perms: ['processInstance:page', 'processInstance:list', 'processInstance:task', 'processHistory:activity', 'dict:list'],
          children: [
            { label: t('list'), key: 'processInstance:page' },
            { label: t('delete'), key: 'processInstance:delete', perms: ['processInstance:delete'] },
          ],
        },
        {
          label: t('menu.system.processHistory'),
          key: 'processHistory.key',
          perms: ['processHistory:page', 'processHistory:list', 'processInstance:task', 'processHistory:activity', 'dict:list'],
          children: [
            { label: t('list'), key: 'processHistory:page' },
            { label: t('delete'), key: 'processHistory:delete', perms: ['processHistory:delete'] },
          ],
        },
        {
          label: t('menu.system.sensitiveWord'),
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
          label: t('menu.system.errorWord'),
          key: 'errorWord.key',
          perms: ['errorWord:page', 'errorWord:list'],
          children: [
            { label: t('list'), key: 'errorWord:page' },
            { label: t('add'), key: 'errorWord:create', perms: ['errorWord:create'] },
            { label: t('edit'), key: 'errorWord:update', perms: ['errorWord:update', 'errorWord:show'] },
            { label: t('delete'), key: 'errorWord:delete', perms: ['errorWord:delete'] },
          ],
        },
        {
          label: t('menu.system.importData'),
          key: 'importData.key',
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
  ];
  return filterExcludePerms(perms);
}

export function getModelData(): any {
  return {
    article: {
      mains: [
        { code: 'title', must: true, show: true, double: false, required: true },
        { code: 'subtitle', must: false, show: false, double: false, required: false },
        { code: 'fullTitle', must: false, show: false, double: false, required: false },
        { code: 'linkUrl', must: false, show: true, double: false, required: false },
        { code: 'tags', must: false, show: false, double: false, required: false },
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
      ],
      asides: [
        { code: 'channel', must: true, show: true, required: true },
        { code: 'org', must: false, show: true, required: true },
        { code: 'publishDate', must: true, show: true, required: true },
        { code: 'onlineDate', must: false, show: true, required: false },
        { code: 'offlineDate', must: false, show: true, required: false },
        { code: 'source', must: false, show: true, required: false },
        { code: 'articleTemplate', must: false, show: true, required: false },
        { code: 'articleStaticPath', must: false, show: false, required: false },
        // { code: 'allowComment', must: false, show: true, required: true },
        { code: 'user', must: false, show: false, required: true },
        { code: 'created', must: false, show: false, required: true },
        { code: 'modifiedUser', must: false, show: false, required: false },
        { code: 'modified', must: false, show: false, required: false },
      ],
    },
    channel: {
      mains: [
        { code: 'name', must: true, show: true, double: true, required: true },
        { code: 'alias', must: true, show: true, double: true, required: true },
        { code: 'linkUrl', must: true, show: true, double: false, required: true },
        { code: 'seoTitle', must: false, show: true, double: true, required: false },
        { code: 'seoKeywords', must: false, show: true, double: true, required: false },
        { code: 'seoDescription', must: false, show: true, double: false, required: false },
        { code: 'image', must: false, show: false, double: false, required: false, type: 'image', imageWidth: 300, imageHeight: 200, imageMode: 'manual' },
        { code: 'channelModel', must: true, show: true, double: true, required: true },
        { code: 'articleModel', must: true, show: true, double: true, required: true },
        { code: 'channelTemplate', must: false, show: true, double: true, required: true },
        { code: 'articleTemplate', must: false, show: true, double: true, required: true },
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
        { code: 'performanceType', must: false, show: true, required: false, epRank: 3 },
        { code: 'channelStaticPath', must: false, show: false, required: false },
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
      // 加上缺失属性，覆盖不可改属性
      fields[index] = { ...item, ...fields[index], must: item.must, label: item.label, type: item.type };
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
  'blockItem',
  'dict',
  'tag',
  'form',
  'attachment',
  'fulltext',
  'html',
  'messageBoard',
  'messageBoardType',
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
  'article.updateOrder',
  'article.sticky',
  'article.internalPush',
  'article.externalPush',

  'channel.create',
  'channel.update',
  'channel.updateNav',
  'channel.batchMove',
  'channel.batchMerge',
  'channel.delete',
  'channel.move',
  'channel.tidyTree',

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

  'form.create',
  'form.update',
  'form.updateOrder',
  'form.delete',
  'form.pass',
  'form.reject',

  'attachment.delete',

  'fulltext.reindexAll',
  'fulltext.reindexSite',

  'html.updateAll',
  'html.updateHome',
  'html.updateChannel',
  'html.updateArticle',

  'messageBoard.create',
  'messageBoard.update',
  'messageBoard.updateStatus',
  'messageBoard.delete',

  'messageBoardType.create',
  'messageBoardType.update',
  'messageBoardType.updateOrder',
  'messageBoardType.delete',

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
  'collection.delete',

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
  'config.updateSms',
  'config.sendSms',
  'config.updateEmail',
  'config.sendEmail',
  'config.updateUploadStorage',
  'config.updateHtmlStorage',
  'config.updateTemplateStorage',
  'config.updateCustoms',

  'siteSettings.updateBase',
  'siteSettings.updateWatermark',
  'siteSettings.updateEditor',
  'siteSettings.updateMessageBoard',
  'siteSettings.updateCustoms',
  'siteSettings.updateHtml',

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

  'sensitiveWord.create',
  'sensitiveWord.update',
  'sensitiveWord.delete',

  'errorWord.create',
  'errorWord.update',
  'errorWord.delete',

  'importData.importChannel',
  'importData.importArticle',
  'importData.deleteCorrespond',

  'personal.updatePassword',
];

import i18n from '@/i18n';
import Plugin from './plugin';

const defaultOptions = {
  id: 'indent2em',
  name: i18n.global.t('editor.indent2em'),
  tooltip: i18n.global.t('editor.indent2em.tooltip'),
  // 本项目自绘（24 网格）：箭头把首行推向右侧，其余两行顶格
  icon: '<svg viewBox="0 0 24 24" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M4 5h3V3l4 3-4 3V7H4zM13 5h7v2h-7zM4 11h16v2H4zM4 17h16v2H4z"/></svg>',
};

Plugin(defaultOptions);

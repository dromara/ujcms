import DirectEditingModule from 'diagram-js-direct-editing';
import ContextPadModule from 'diagram-js/lib/features/context-pad';
import SelectionModule from 'diagram-js/lib/features/selection';
import ConnectModule from 'diagram-js/lib/features/connect';
import CreateModule from 'diagram-js/lib/features/create';
import AppendPreviewModule from 'bpmn-js/lib/features/append-preview';
import PopupMenuModule from 'bpmn-js/lib/features/popup-menu';
import FlowableContextPadProvider from './FlowableContextPadProvider';

/**
 * https://github.com/bpmn-io/bpmn-js/blob/develop/lib/features/context-pad/index.js
 */
export default {
  __depends__: [AppendPreviewModule, DirectEditingModule, ContextPadModule, SelectionModule, ConnectModule, CreateModule, PopupMenuModule],
  // 覆盖自带的 contextPadProvider 全部自己定义
  __init__: ['contextPadProvider'],
  contextPadProvider: ['type', FlowableContextPadProvider],
};

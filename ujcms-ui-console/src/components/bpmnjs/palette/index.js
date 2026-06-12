import PaletteModule from 'diagram-js/lib/features/palette';
import CreateModule from 'diagram-js/lib/features/create';
import LassoToolModule from 'diagram-js/lib/features/lasso-tool';
import HandToolModule from 'diagram-js/lib/features/hand-tool';
import GlobalConnectModule from 'diagram-js/lib/features/global-connect';
import translate from 'diagram-js/lib/i18n/translate';
import SpaceToolModule from 'bpmn-js/lib/features/space-tool';
import FlowablePaletteProvider from './FlowablePaletteProvider';

/**
 * https://github.com/bpmn-io/bpmn-js/blob/develop/lib/features/palette/index.js
 */
export default {
  __depends__: [PaletteModule, CreateModule, SpaceToolModule, LassoToolModule, HandToolModule, GlobalConnectModule, translate],
  // 覆盖自带的 paletteProvider 全部自己定义
  __init__: ['paletteProvider'],
  paletteProvider: ['type', FlowablePaletteProvider],
};

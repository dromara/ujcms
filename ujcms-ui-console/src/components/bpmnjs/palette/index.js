/*
 * Derived from bpmn-js (https://github.com/bpmn-io/bpmn-js), lib/features/palette/index.js.
 * Copyright (c) 2014-present Camunda Services GmbH. Distributed under the bpmn.io license (licenses/bpmn-io.txt):
 * the bpmn.io watermark must not be removed, changed or covered.
 *
 * Modified for UJCMS by 南昌蓝智科技有限公司 (https://www.ujcms.com): registers FlowablePaletteProvider
 * (./FlowablePaletteProvider.js) in place of the stock PaletteProvider. First modified before 2026-04-19.
 */
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

<script setup lang="ts">
import { ref, toRefs, markRaw } from 'vue';
import { RefreshLeft, RefreshRight, Aim, ZoomIn, ZoomOut } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { queryProcessModelXml, updateProcessModelXml, validateProcessModelXml } from '@/api/system';
import { queryRoleList, queryOrgList } from '@/api/user';
import FlowablePropertiesPannel from '@/components/bpmnjs/properties-panel/FlowablePropertiesPannel.vue';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import flowableModdleDescriptors from '@/components/bpmnjs/descriptors/flowable';
import FlowablwPaletteModule from '@/components/bpmnjs/palette';
import FlowablwContextPadModule from '@/components/bpmnjs/context-pad';
import customTranslate from '@/components/bpmnjs/custom-translate/customTranslate';
import defaultBpmnXml from '@/components/bpmnjs/custom-translate/defaultBpmnXml';

import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

/**
 * bpmn.js 文档: https://bpmn.io/toolkit/bpmn-js/walkthrough/
 *
 * 关于 moddle (@/components/bpmnjs/descriptors/flowable)
 * https://github.com/bpmn-io/bpmn-moddle/blob/main/resources/bpmn/json/bpmn.json
 * https://github.com/camunda/zeebe-bpmn-moddle/blob/main/resources/zeebe.json
 *
 * 关于 Canvas
 * https://github.com/bpmn-io/diagram-js/blob/main/lib/core/Canvas.js
 * https://github.com/bpmn-io/diagram-js/blob/main/lib/navigation/zoomscroll/ZoomScroll.js
 *
 * 关于 CommandStack
 * https://github.com/bpmn-io/diagram-js/blob/main/lib/command/CommandStack.js
 *
 * 关于 Viewer
 * https://github.com/bpmn-io/bpmn-js/blob/develop/lib/Viewer.js
 * https://github.com/bpmn-io/bpmn-js/blob/develop/lib/BaseViewer.js
 *
 * 中文文档
 * https://github.com/LinDaiDai/bpmn-chinese-document
 */
defineOptions({
  name: 'ProcessModelDesign',
});
const visible = defineModel<boolean>();
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
});
const emit = defineEmits({ 'update:modelValue': null });

const { beanId } = toRefs(props);
const { t, locale } = useI18n();
const bpmnContainer = ref<any>();
const bpmnProperties = ref<any>();
const bpmnModeler = ref<any>();
const bpmnCanvas = ref<any>();
const commandStack = ref<any>();
const buttonLoading = ref<boolean>(false);
const xmlPreviewVisible = ref<boolean>(false);
const xmlValidateVisible = ref<boolean>(false);
const canRedo = ref<boolean>(false);
const canUndo = ref<boolean>(false);
const bpmnXml = ref<string>();
const bpmnKey = ref<string>('');
const bpmnName = ref<string>('');
const bpmnCategory = ref<string>('');
const validationErrors = ref<any[]>([]);
const TranslateModule = {
  translate: ['value', customTranslate],
};

const handlerOpen = async () => {
  if (bpmnModeler.value == null) {
    // 必须使用markRaw，否则属性面板会出现"ID must be unique."的错误提示
    bpmnModeler.value = markRaw(
      new BpmnModeler({
        container: bpmnContainer.value,
        // 允许使用键盘操作，如del、undo、redo、copy
        keyboard: { bindTo: document },
        propertiesPanel: {
          parent: bpmnProperties.value,
        },
        // bpmn-js 自带英文界面；控制台为中文时才挂中文翻译模块
        additionalModules: [...(locale.value.startsWith('zh') ? [TranslateModule] : []), FlowablwPaletteModule, FlowablwContextPadModule],
        moddleExtensions: { flowable: flowableModdleDescriptors },
      }),
    );
    commandStack.value = bpmnModeler.value.get('commandStack');
    bpmnCanvas.value = bpmnModeler.value.get('canvas');
    // 必须使用事件监听，不能用computed。因为使用markRaw之后，computed无法起作用。
    bpmnModeler.value.on('commandStack.changed', () => {
      canRedo.value = commandStack.value.canRedo();
      canUndo.value = commandStack.value.canUndo();
    });
  }
  if (beanId?.value == null) {
    return;
  }
  const model = await queryProcessModelXml(beanId.value);
  bpmnKey.value = model.key;
  bpmnName.value = model.name;
  bpmnCategory.value = model.category;
  const xml = model.xml || defaultBpmnXml(bpmnKey.value, bpmnName.value, bpmnCategory.value);
  importXml(xml);
};
const importXml = async (xml: string) => {
  await bpmnModeler.value.importXML(xml);
};
const canvasReset = () => {
  // 这里不使用 bpmnCanvas.value.zoom('fit-viewport')，该方法为了完整显示流程图（流程图内容可能超出边界），可能对视图进行缩放
  const zoomedAndScrolledViewbox = bpmnCanvas.value.viewbox();
  bpmnCanvas.value.viewbox({
    x: 0,
    y: 0,
    width: zoomedAndScrolledViewbox.outer.width,
    height: zoomedAndScrolledViewbox.outer.height,
  });
};
const handleXmlEditorSave = async () => {
  importXml(bpmnXml.value || defaultBpmnXml(bpmnKey.value, bpmnName.value, bpmnCategory.value));
  xmlPreviewVisible.value = false;
};
const handleXmlPreview = async () => {
  bpmnXml.value = (await bpmnModeler.value.saveXML({ format: true })).xml;
  xmlPreviewVisible.value = true;
};
const handleXmlValidate = async () => {
  buttonLoading.value = true;
  try {
    const xml = (await bpmnModeler.value.saveXML()).xml;
    validationErrors.value = await validateProcessModelXml(xml);
    if (validationErrors.value.length > 0) {
      xmlValidateVisible.value = true;
    } else {
      ElMessage.success(t('success'));
    }
  } finally {
    buttonLoading.value = false;
  }
};
const handleSubmit = async () => {
  buttonLoading.value = true;
  try {
    const { xml } = await bpmnModeler.value.saveXML();
    updateProcessModelXml({ id: beanId?.value, xml });
    ElMessage.success(t('success'));
  } finally {
    buttonLoading.value = false;
    emit('update:modelValue', false);
  }
};
</script>

<template>
  <div class="dialog-full">
    <el-dialog v-model="visible" :title="$t('processModel.op.design')" fullscreen @opened="handlerOpen()">
      <el-container class="border-t" style="height: calc(100vh - 65px)">
        <el-container class="border-r">
          <el-header height="auto" class="px-2 py-1">
            <el-button :loading="buttonLoading" type="primary" @click.prevent="handleSubmit">{{ $t('save') }}</el-button>
            <el-button :loading="buttonLoading" @click.prevent="handleXmlPreview">{{ $t('processModel.op.xmlPreview') }}</el-button>
            <el-button :loading="buttonLoading" @click.prevent="handleXmlValidate">{{ $t('processModel.op.xmlValidate') }}</el-button>
            <el-button-group class="ml-2">
              <el-button :icon="RefreshRight" :disabled="!canRedo" :title="$t('redo')" @click="bpmnModeler.get('commandStack').redo()" />
              <el-button :icon="RefreshLeft" :disabled="!canUndo" :title="$t('undo')" @click="bpmnModeler.get('commandStack').undo()" />
            </el-button-group>
            <el-button-group class="ml-2">
              <el-button :icon="ZoomIn" :title="$t('zoom.in')" @click="() => bpmnModeler.get('zoomScroll').stepZoom(1)" />
              <el-button :icon="ZoomOut" :title="$t('zoom.out')" @click="() => bpmnModeler.get('zoomScroll').stepZoom(-1)" />
              <el-button :icon="Aim" :title="$t('zoom.reset')" @click="() => canvasReset()" />
            </el-button-group>
          </el-header>
          <el-main class="p-0 border-t">
            <el-scrollbar class="h-full py-2 pr-2 drawing-board">
              <div ref="bpmnContainer" class="w-full h-full"></div>
            </el-scrollbar>
          </el-main>
        </el-container>
        <el-aside class="right-panel w-80">
          <el-scrollbar class="h-full pb-1">
            <flowable-properties-pannel :modeler="bpmnModeler" :query-roles="queryRoleList" :query-orgs="queryOrgList" />
          </el-scrollbar>
        </el-aside>
      </el-container>
    </el-dialog>
    <el-dialog v-model="xmlPreviewVisible" :title="$t('processModel.op.xmlPreview')" width="98%" top="2vh" append-to-body>
      <el-input v-model="bpmnXml" autosize type="textarea" />
      <el-button type="primary" class="mt-2" @click="handleXmlEditorSave()">{{ $t('save') }}</el-button>
    </el-dialog>
    <el-dialog v-model="xmlValidateVisible" :title="$t('processModel.op.xmlValidate')" width="768px" append-to-body>
      <el-table :data="validationErrors">
        <el-table-column property="problem" label="problem" width="300" />
        <el-table-column property="defaultDescription" label="defaultDescription" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.dialog-full {
  :deep(.el-dialog__body) {
    height: calc(100vh - 55px);
    padding-bottom: 0;
  }
  :deep(.el-scrollbar__view) {
    @apply h-full;
  }
  :deep(.djs-container) {
    /* 画布网格：40px 主线叠加 10px 细线，纯 CSS 绘制；线色 #e0e0e0，主线 40%、细线 20% 不透明度 */
    background-color: #fff;
    background-image:
      linear-gradient(to right, rgb(224 224 224 / 40%) 1px, transparent 1px), linear-gradient(to bottom, rgb(224 224 224 / 40%) 1px, transparent 1px),
      linear-gradient(to right, rgb(224 224 224 / 20%) 1px, transparent 1px), linear-gradient(to bottom, rgb(224 224 224 / 20%) 1px, transparent 1px);
    background-size:
      40px 40px,
      40px 40px,
      10px 10px,
      10px 10px;
  }
}
</style>

<script setup lang="ts">
import { watch, type PropType } from 'vue';
import { ElMessage } from 'element-plus';
import type { PanelVM } from '@/composables/useRegionDraft';
import { usePanelSettingsForm } from './usePanelSettingsForm';
import PanelSettingsFields from './PanelSettingsFields.vue';

/**
 * 面板设置对话框（component-settings §3，Phase B）：结构视图与检查器「在对话框中编辑」的入口；
 * 表单模型与字段渲染同检查器内联设置（usePanelSettingsForm / PanelSettingsFields）。
 * 不直接调接口——只编辑内存值块并经 finished 回传 {enabled, visibility, settings}，由 BuilderPage
 * 保存（save-per-op；新面板延迟提交：确定首存、取消回滚）。
 */
defineOptions({
  name: 'PanelForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 编辑中的面板（PanelVM v2）：{ id, shape, settings, enabled, visibility }
  panel: { type: Object as PropType<PanelVM>, required: true },
  // 面板 Shape 的显示名
  shapeLabel: { type: String, default: '' },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const form = usePanelSettingsForm();

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) void form.load(props.panel);
  },
  { immediate: true },
);

const handleSave = (): void => {
  const problem = form.firstProblemMessage();
  if (problem != null) {
    ElMessage.error(problem);
    return;
  }
  emit('finished', form.result());
  emit('update:modelValue', false);
};
</script>

<template>
  <el-dialog :model-value="modelValue" width="760" append-to-body destroy-on-close @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <template #header>
      <div class="flex min-w-0 items-center gap-2 pr-6">
        <span class="truncate text-base font-semibold text-gray-primary">{{ $t('region.panel.config') }}{{ $t('punct.colon') }}{{ shapeLabel || panel.shape }}</span>
        <span class="shrink-0 rounded bg-gray-100 px-1.5 font-mono text-xs text-gray-secondary">{{ panel.shape }}</span>
      </div>
    </template>
    <panel-settings-fields :form="form" layout="dialog" />
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" @click="() => handleSave()">{{ $t('ok') }}</el-button>
    </template>
  </el-dialog>
</template>

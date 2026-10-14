<script setup lang="ts">
import { computed, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import LabelTip from '@/components/LabelTip.vue';
import ShapeFieldItem from './fields/ShapeFieldItem.vue';
import InspectorSection from './builder/InspectorSection.vue';
import type { PanelVisibility } from '@/composables/useRegionDraft';
import type { PanelSettingsForm } from './usePanelSettingsForm';

/**
 * 面板设置表单体（PanelForm 对话框 / 检查器内联共用）：启用 + 显示设备 + 组件声明的设置字段
 * （ShapeFieldItem 渲染器，含 visibleWhen 条件显示与分组节）。纯展示——状态全在 usePanelSettingsForm。
 * layout: dialog = 右侧标签 120px；inline = 顶部标签、小尺寸（检查器 360px 宽）。启用/显示设备沿用 EP 表单控件（开关、单选），
 * 与组件声明字段的渲染器一致。
 */
defineOptions({
  name: 'PanelSettingsFields',
});
const props = defineProps({
  form: { type: Object as PropType<PanelSettingsForm>, required: true },
  layout: { type: String as PropType<'dialog' | 'inline'>, default: 'dialog' },
});

const { t } = useI18n();
const inline = computed<boolean>(() => props.layout === 'inline');
const visibilityOptions = computed(() => [
  { value: 'all', label: t('placement.visibility.all') },
  { value: 'desktop', label: t('placement.visibility.desktop') },
  { value: 'mobile', label: t('placement.visibility.mobile') },
]);
</script>

<template>
  <el-form v-loading="form.loading" :label-position="inline ? 'top' : 'right'" :label-width="inline ? undefined : '120px'" :size="inline ? 'small' : 'default'" @submit.prevent>
    <!-- 启用 / 显示设备（design-review P2-5）：框架级、组件无关的一档（组件不可解析时仍可用） -->
    <el-form-item :label="$t('placement.enabled')">
      <el-switch :model-value="form.values.enabled" @update:model-value="(val: string | number | boolean) => form.setEnabled(!!val)"></el-switch>
    </el-form-item>
    <!-- 显示设备与下方组件声明的 radio 字段同一控件语言（ShapeFieldItem 的 el-radio）；分段控件在 360px 检查器内会截断长标签 -->
    <el-form-item :label="$t('placement.visibility')">
      <el-radio-group
        :model-value="form.values.visibility"
        @update:model-value="(val: string | number | boolean | undefined) => form.setVisibility(String(val) as PanelVisibility)"
      >
        <el-radio v-for="item in visibilityOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
      </el-radio-group>
    </el-form-item>

    <el-alert v-if="form.shapeMissing" :title="$t('region.panel.shapeMissing')" type="warning" :closable="false" class="mb-3" />
    <div v-else-if="!form.loading && !form.hasSettings" class="rounded-md bg-gray-50 px-3 py-2 text-xs leading-5 text-gray-secondary">
      {{ $t('region.panel.settings.empty') }}
    </div>

    <template v-for="(section, index) in form.sections" :key="index">
      <inspector-section v-if="section.label !== ''" :title="section.label" :class="index > 0 ? 'mt-4' : ''" />
      <el-form-item v-for="[fieldName, fieldDef] in section.fields" :key="fieldName">
        <template #label>
          <label-tip :label="form.fieldLabel(fieldName, fieldDef)" :tooltip="form.fieldHelp(fieldDef)" :help="form.fieldHelp(fieldDef) != null" />
          <span v-if="fieldDef.required" class="text-danger">*</span>
        </template>
        <shape-field-item
          :def="fieldDef"
          :model-value="form.values.settings[fieldName] ?? fieldDef.default ?? null"
          @update:model-value="(val: any) => form.setSetting(fieldName, val)"
          @video-poster="(url: string) => form.handleVideoPoster(fieldName, url)"
        ></shape-field-item>
      </el-form-item>
    </template>
  </el-form>
</template>

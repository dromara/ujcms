<script setup lang="ts">
import { computed, ref, toRaw, toRefs, watch } from 'vue';
import { CircleClose, WarningFilled } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { queryDictTypeList, queryModel, updateModelCustomFields } from '@/api/config';
import FieldItem from './components/FieldItem.vue';
import FieldAttribute from './components/FieldAttribute.vue';
import LabelTip from '@/components/LabelTip.vue';
import { loadOrClose } from '@/utils/common';
import { createField, FIELD_GROUPS, normalizeField, validateField } from './components/fieldRules';
import { isReadonlyModel } from './components/modelRules';

// 自定义字段设计器：左侧字段面板（点击或拖入）、中间画布（即所见表单）、右侧属性。
// 保存前逐字段校验（不只是选中的那个）；只保存字段集（model-management M2）；未保存守卫；保存失败不关。
defineOptions({
  name: 'ModelCustomFields',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { beanId, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const bean = ref<any>({});
const drawingFormData = ref<any>({});
const selectedForm = ref<any>();
const drag = ref<boolean>(false);
const selected = ref<any>();
const cloned = ref<any>();
const buttonLoading = ref<boolean>(false);
const customs = ref<any[]>([]);
const dictTypes = ref<any[]>([]);
// 保存被拦下之后，画布上标出未通过校验的字段；随编辑实时重算，改对了标记即消失
const attempted = ref<boolean>(false);
// 行键：字段代码可编辑，重名时不能当键；给每个字段对象发一个稳定序号。
// WeakMap 以原始对象为键（toRaw）：响应式数组交回来的是代理，直接拿代理查会落空
const rowKeys = new WeakMap<object, number>();
let nextKey = 1;
const keyOf = (field: object) => {
  const raw = toRaw(field);
  let key = rowKeys.get(raw);
  if (key == null) {
    key = nextKey;
    nextKey += 1;
    rowKeys.set(raw, key);
  }
  return key;
};
// 已保存字段装载时的代码：改动代码、删除字段都要提示（已录入内容中该字段的数据将不再显示）
const persistedCodes = new WeakMap<object, string>();
const persistedCodeOf = (field: object | undefined): string | undefined => (field ? persistedCodes.get(toRaw(field)) : undefined);
const markPersisted = (field: object) => persistedCodes.set(toRaw(field), (field as any).code);
// 未保存守卫的基线：规范化之后的字段集
const baseline = ref<string>('');
const dirty = computed(() => JSON.stringify(customs.value) !== baseline.value);
const readonly = computed(() => isReadonlyModel(bean.value));
const title = computed(() => (bean.value?.name ? `${t('model.fun.customFields')} - ${bean.value.name}` : t('model.fun.customFields')));
const groups = computed(() => FIELD_GROUPS.map((group) => ({ key: group.key, items: group.types.map((type) => ({ type, label: t(`model.fieldType.${type}`) })) })));

watch(visible, async () => {
  if (visible.value && beanId?.value != null) {
    bean.value = await loadOrClose(
      () => queryModel(beanId.value),
      () => emit('update:modelValue', false),
    );
    const fields: any[] = JSON.parse(bean.value.customs || '[]');
    fields.forEach((field) => {
      normalizeField(field);
      markPersisted(field);
    });
    customs.value = fields;
    [selected.value] = fields;
    attempted.value = false;
    baseline.value = JSON.stringify(fields);
    dictTypes.value = await queryDictTypeList().catch(() => []);
  }
});
// 切换选中不再要求当前字段先通过校验：未通过的在画布上有标记，保存时统一拦截
const changeSelected = (element: any) => {
  selected.value = element;
};
const addField = (item: { type: string; label: string }) => {
  const field = createField(item.type, item.label, customs.value);
  customs.value.push(field);
  selected.value = field;
};
const clone = (element: { type: string; label: string }) => {
  const field = createField(element.type, element.label, customs.value);
  cloned.value = field;
  return field;
};
const onEnd = (evt: any) => {
  if (evt.from !== evt.to) {
    selected.value = cloned.value;
  }
};
const removeAt = (index: number) => {
  customs.value.splice(index, 1);
  const { length } = customs.value;
  if (length <= 0) {
    selected.value = undefined;
  } else if (index < length) {
    selected.value = customs.value[index];
  } else {
    selected.value = customs.value[length - 1];
  }
};
const deleteElement = async (element: any) => {
  const index = customs.value.indexOf(element);
  if (index < 0) {
    return;
  }
  if (persistedCodeOf(element) != null) {
    try {
      await ElMessageBox.confirm(t('model.field.confirmDelete'), { type: 'warning' });
    } catch {
      return;
    }
  }
  removeAt(index);
};
// 逐字段校验：返回未通过的字段
const validateAll = () => customs.value.filter((field) => Object.keys(validateField(field, customs.value, t)).length > 0);
const invalid = computed(() => (attempted.value ? new Set(validateAll()) : new Set()));
const handleSubmit = async () => {
  const failed = validateAll();
  attempted.value = true;
  if (failed.length > 0) {
    [selected.value] = failed;
    ElMessage.warning(t('model.designer.invalid', { count: failed.length }));
    // 属性面板随即显示该字段的错误
    setTimeout(() => selectedForm.value?.validate().catch(() => undefined));
    return;
  }
  buttonLoading.value = true;
  try {
    await updateModelCustomFields(bean.value.id, { customs: JSON.stringify(customs.value) });
    baseline.value = JSON.stringify(customs.value);
    customs.value.forEach((field) => markPersisted(field));
    ElMessage.success(t('success'));
    emit('finished');
    emit('update:modelValue', false);
  } finally {
    buttonLoading.value = false;
  }
};
const close = () => emit('update:modelValue', false);
const handleBeforeClose = async (done: () => void) => {
  if (!dirty.value) {
    done();
    return;
  }
  try {
    await ElMessageBox.confirm(t('model.confirmDiscard'), { type: 'warning' });
    done();
  } catch {
    // 留在设计器里
  }
};
const handleCancel = () => handleBeforeClose(close);
</script>

<template>
  <div class="dialog-full">
    <el-dialog
      :title="title"
      :model-value="modelValue"
      :close-on-click-modal="!dirty"
      :close-on-press-escape="!dirty"
      :before-close="handleBeforeClose"
      destroy-on-close
      fullscreen
      @update:model-value="(event) => $emit('update:modelValue', event)"
    >
      <el-container class="border-t" style="height: calc(100vh - 65px)">
        <el-aside v-if="!readonly" width="240px" class="border-r">
          <el-scrollbar class="h-full">
            <div v-for="group in groups" :key="group.key" class="px-2 pt-3">
              <div class="px-1 mb-1 text-xs text-gray-secondary">{{ $t(`model.fieldGroup.${group.key}`) }}</div>
              <draggable :list="group.items" :group="{ name: 'components', pull: 'clone', put: false }" :sort="false" :clone="clone" item-key="type" class="flex flex-wrap" @end="onEnd">
                <template #item="{ element }">
                  <div class="drag-component" :title="element.label" @click="() => addField(element)">{{ element.label }}</div>
                </template>
              </draggable>
            </div>
          </el-scrollbar>
        </el-aside>
        <el-container class="border-r">
          <el-header height="auto" class="flex items-center px-2 py-1">
            <template v-if="!readonly">
              <el-button :loading="buttonLoading" type="primary" @click.prevent="handleSubmit">{{ $t('save') }}</el-button>
              <el-button @click="handleCancel">{{ $t('cancel') }}</el-button>
              <el-tag v-if="dirty" type="danger" class="ml-2" disable-transitions>{{ $t('form.unsaved') }}</el-tag>
            </template>
            <template v-else>
              <el-button @click="close">{{ $t('close') }}</el-button>
              <el-tag type="info" class="ml-2" disable-transitions>{{ $t('model.readonly') }}</el-tag>
            </template>
          </el-header>
          <el-main class="p-0 border-t">
            <el-scrollbar class="h-full p-2 drawing-board">
              <el-form :model="drawingFormData" :disabled="readonly" label-width="150px" class="h-full relative">
                <div v-if="customs.length <= 0" class="absolute inset-0 flex items-center justify-center text-gray-placeholder pointer-events-none">{{ $t('model.designer.empty') }}</div>
                <draggable
                  :list="customs"
                  class="content-start min-h-full mx-0"
                  tag="el-row"
                  :component-data="{ gutter: 8 }"
                  :animation="250"
                  :disabled="readonly"
                  group="components"
                  :item-key="(field: any) => keyOf(field)"
                  @start="() => (drag = true)"
                  @end="() => (drag = false)"
                >
                  <template #item="{ element: field }">
                    <el-col :span="field.double ? 12 : 24" class="relative">
                      <el-form-item :required="field.required" :show-message="false" class="py-3 mb-0">
                        <template #label><label-tip :label="field.name" /></template>
                        <field-item v-model="field.defaultValue" v-model:model-key="field.defaultValueKey" :field="field"></field-item>
                      </el-form-item>
                      <div
                        :class="['drag-mask', !drag && selected !== field ? 'hover:opacity-10' : null, selected === field ? 'drag-mask-selected' : null, invalid.has(field) ? 'drag-mask-invalid' : null]"
                        @click="() => changeSelected(field)"
                      ></div>
                      <div v-if="invalid.has(field)" class="drag-badge" :title="$t('model.designer.invalid', { count: 1 })">
                        <el-icon class="text-danger"><warning-filled /></el-icon>
                      </div>
                      <div v-if="!readonly" :class="['drag-close', selected !== field ? 'hidden' : null]" @click="() => deleteElement(field)">
                        <el-icon class="text-danger"><circle-close /></el-icon>
                      </div>
                    </el-col>
                  </template>
                </draggable>
              </el-form>
            </el-scrollbar>
          </el-main>
        </el-container>
        <el-aside class="w-64 right-panel">
          <el-scrollbar class="h-full pb-3">
            <div class="px-2 py-2 border-b font-medium">{{ $t('model.attribute') }}</div>
            <el-form ref="selectedForm" :model="selected" :disabled="readonly" class="px-2 pt-3">
              <field-attribute
                v-if="selected"
                :selected="selected"
                :fields="customs"
                :model-type="bean.type"
                :model-global="bean.scope === 2"
                :dict-types="dictTypes"
                :original-code="persistedCodeOf(selected)"
              />
            </el-form>
          </el-scrollbar>
        </el-aside>
      </el-container>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
// 主色是 CSS 变量，border-opacity-* 修饰符无效：用 transparent / primary 切换
.drag-component {
  @apply relative w-[100px] m-1 p-2 text-xs rounded-sm cursor-pointer bg-gray-100 border border-dashed border-transparent hover:border-primary hover:text-primary;
}
.drag-mask {
  @apply absolute top-0 left-0 w-full h-full cursor-move z-10 opacity-0 bg-primary-light;
}
.drag-mask-selected {
  @apply bg-primary-light opacity-30 rounded-sm border border-primary border-dashed;
}
.drag-mask-invalid {
  @apply opacity-20 bg-danger border border-danger border-dashed;
}
.drag-close {
  @apply absolute -top-0.5 right-0 z-10 cursor-pointer opacity-70;
}
.drag-badge {
  @apply absolute -top-0.5 right-5 z-10 cursor-default;
}

.dialog-full {
  :deep(.el-dialog__body) {
    padding-bottom: 0;
  }
}
.right-panel {
  :deep(.el-form-item) {
    margin-bottom: 14px;
  }
}

.drawing-board {
  .sortable-ghost {
    &::before {
      content: ' ';
      @apply absolute w-full top-0 left-0 h-1 bg-warning;
    }
    .drag-mask {
      opacity: 0.1 !important;
    }
    &.drag-component {
      @apply w-full ml-0 mr-0;
    }
  }
  :deep(.el-scrollbar__view) {
    @apply h-full;
  }
}
</style>

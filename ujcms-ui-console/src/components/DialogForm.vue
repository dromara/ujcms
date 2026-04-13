<script setup lang="ts">
import { computed, onMounted, PropType, ref, toRefs, watch } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import _ from 'lodash';
import { perm } from '@/stores/useCurrentUser';

const CONTINUOUS_SETTINGS = 'ujcms_continuous_settings';
function fetchContinuous(): Record<string, boolean> {
  const settings = localStorage.getItem(CONTINUOUS_SETTINGS);
  return settings ? JSON.parse(settings) : {};
}
function storeContinuous(settings: Record<string, boolean>) {
  localStorage.setItem(CONTINUOUS_SETTINGS, JSON.stringify(settings));
}
function getContinuous(name: string) {
  const settings = fetchContinuous();
  return settings[name] ?? false;
}
function setContinuous(name: string, continuous: boolean) {
  const settings = fetchContinuous();
  settings[name] = continuous;
  storeContinuous(settings);
}

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  values: { type: Object, required: true },
  name: { type: String, required: true },
  beanId: { type: [Number, String], default: null },
  beanIds: { type: Array as PropType<string[] | number[]>, required: true },
  initValues: { type: Function as PropType<(bean?: any, isEditor?: boolean) => any>, required: true },
  toValues: { type: Function as PropType<(bean: any) => any>, required: true },
  queryBean: { type: Function as PropType<(id: any) => Promise<any>>, required: true },
  createBean: { type: Function as PropType<(bean: any) => Promise<any>>, required: true },
  updateBean: { type: Function as PropType<(bean: any) => Promise<any>>, required: true },
  deleteBean: { type: Function as PropType<(ids: any[]) => Promise<any>>, required: true },
  disableDelete: { type: Function as PropType<(bean: any) => boolean>, default: null },
  disableEdit: { type: Function as PropType<(bean: any) => boolean>, default: null },
  beforeValidate: { type: Function as PropType<(values: any) => Promise<boolean> | void>, default: null },
  preventSubmit: { type: Function as PropType<(values: any) => Promise<boolean>>, default: null },
  addable: { type: Boolean, default: true },
  action: { type: String as PropType<'add' | 'copy' | 'edit'>, default: 'edit' },
  showId: { type: Boolean, default: true },
  perms: { type: String, default: null },
  focus: { type: Object, default: null },
  large: { type: Boolean, default: false },
  labelPosition: { type: String as PropType<'top' | 'right' | 'left'>, default: 'right' },
  labelWidth: { type: String, default: '150px' },
});
const emit = defineEmits({
  'update:modelValue': null,
  'update:values': null,
  finished: null,
  beanChange: null,
  beforeSubmit: null,
});

const { name, beanId, beanIds, focus, values, action, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const continuous = ref<boolean>(getContinuous(name.value));
const form = ref<any>();
const bean = ref<any>(props.initValues());
const origValues = ref<any>();
const id = ref<any>();
const ids = ref<Array<any>>([]);
const isEdit = computed(() => id.value != null && action.value === 'edit');
const unsaved = computed(() => {
  // 调试 未保存
  // if (!_.isEqual(origValues.value, values.value)) {
  //   console.log(JSON.stringify(origValues.value));
  //   console.log(JSON.stringify(values.value));
  // }
  return !loading.value && !_.isEqual(origValues.value, values.value);
});
const disabled = computed(() => props.disableEdit?.(bean.value) ?? false);
const title = computed(() => `${name.value} - ${isEdit.value ? `${t(disabled.value ? 'detail' : 'edit')} (ID: ${id.value})` : `${t('add')}`}`);
const loadBean = async () => {
  loading.value = true;
  try {
    bean.value = id.value != null ? await props.queryBean(id.value) : props.initValues(values.value, isEdit.value);
    origValues.value = id.value != null ? props.toValues(bean.value) : bean.value;
    emit('update:values', _.cloneDeep(origValues.value));
    emit('beanChange', bean.value);
    form.value?.resetFields();
  } finally {
    loading.value = false;
  }
};
onMounted(() => emit('update:values', props.initValues()));
watch(visible, () => {
  if (visible.value) {
    ids.value = beanIds.value;
    if (id.value !== beanId.value) {
      id.value = beanId.value;
    } else {
      loadBean();
    }
  }
});
watch(id, () => {
  loadBean();
});
watch(continuous, () => setContinuous(name.value, continuous.value));
const index = computed(() => ids.value.indexOf(id.value));
const hasPrev = computed(() => index.value > 0);
const hasNext = computed(() => index.value < ids.value.length - 1);
const handlePrev = () => {
  if (hasPrev.value) {
    id.value = ids.value[index.value - 1];
  }
};
const handleNext = () => {
  if (hasNext.value) {
    id.value = ids.value[index.value + 1];
  }
};
const handleAdd = () => {
  focus.value?.focus?.();
  id.value = undefined;
};
const handleCancel = () => {
  emit('update:modelValue', false);
};
const handleDelete = async () => {
  buttonLoading.value = true;
  try {
    await props.deleteBean([id.value]);
    if (!continuous.value) emit('update:modelValue', false);
    if (hasNext.value) {
      handleNext();
      ids.value.splice(index.value - 1, 1);
    } else if (hasPrev.value) {
      handlePrev();
      ids.value.splice(index.value + 1, 1);
    } else {
      emit('update:modelValue', false);
    }
    ElMessage.success(t('success'));
    emit('finished');
  } finally {
    buttonLoading.value = false;
  }
};
const resetOrigValues = () => {
  origValues.value = props.toValues(values.value);
};
const handleSubmit = async (stay = false) => {
  await props.beforeValidate?.(values.value);
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      if ((await props.preventSubmit?.(values.value)) ?? false) {
        return;
      }
      emit('beforeSubmit', values.value);
      if (isEdit.value) {
        await props.updateBean(values.value);
        resetOrigValues();
      } else {
        await props.createBean(values.value);
        focus.value?.focus?.();
        emit('update:values', props.initValues(values.value, isEdit.value));
        form.value.resetFields();
      }
      ElMessage.success(t('success'));
      if (!continuous.value && !stay) emit('update:modelValue', false);
      emit('finished', bean.value);
    } finally {
      buttonLoading.value = false;
    }
  });
};
const submit = (
  executor: (
    values: any,
    payload: { isEdit: boolean; continuous: boolean; form: any; props: any; focus: any; loadBean: () => Promise<any>; resetOrigValues: () => void; emit: any },
  ) => Promise<boolean | undefined>,
) => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    buttonLoading.value = true;
    try {
      if ((await props.preventSubmit?.(values.value)) ?? false) {
        return;
      }
      emit('beforeSubmit', values.value);

      const stay = await executor(values.value, {
        isEdit: isEdit.value,
        continuous: continuous.value,
        form: form.value,
        props,
        focus: focus.value,
        loadBean,
        resetOrigValues,
        emit,
      });

      if (!continuous.value && !stay) emit('update:modelValue', false);
      emit('finished', bean.value);
    } finally {
      buttonLoading.value = false;
    }
  });
};
const remove = async (
  executor: (values: any, payload: { isEdit: boolean; continuous: boolean; form: any; props: any; focus: any; loadBean: () => Promise<any>; emit: any }) => Promise<any>,
) => {
  buttonLoading.value = true;
  try {
    await executor(values.value, { isEdit: isEdit.value, continuous: continuous.value, form: form.value, props, focus: focus.value, loadBean, emit });
    if (!continuous.value) {
      emit('update:modelValue', false);
    }
    if (hasNext.value) {
      handleNext();
      ids.value.splice(index.value - 1, 1);
    } else if (hasPrev.value) {
      handlePrev();
      ids.value.splice(index.value + 1, 1);
    } else {
      emit('update:modelValue', false);
    }
    ElMessage.success(t('success'));
    emit('finished');
  } finally {
    buttonLoading.value = false;
  }
};
defineExpose({ form, submit, remove, defaultSubmit: handleSubmit });
</script>

<template>
  <el-dialog
    :title="title"
    :close-on-click-modal="!unsaved"
    :model-value="modelValue"
    :close-on-press-escape="!unsaved"
    :width="large ? '98%' : '768px'"
    :top="large ? '16px' : '8vh'"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @opened="() => !isEdit && focus?.focus()"
  >
    <template #header>
      {{ name }} -
      <span v-if="isEdit">
        {{ $t(disabled ? 'detail' : 'edit') }}
        <span v-if="showId">(ID: {{ id }})</span>
      </span>
      <span v-else>{{ $t('add') }}</span>
    </template>
    <div v-loading="loading || buttonLoading" class="space-x-2">
      <el-button v-if="isEdit && addable" :disabled="perm(`${perms}:create`)" type="primary" :icon="Plus" @click="handleAdd">{{ $t('add') }}</el-button>
      <slot name="header-action" :bean="bean" :is-edit="isEdit" :disabled="disabled" :unsaved="unsaved" :disable-delete="disableDelete" :handle-delete="handleDelete">
        <el-popconfirm v-if="isEdit" :title="$t('confirmDelete')" @confirm="() => handleDelete()">
          <template #reference>
            <el-button :disabled="disableDelete?.(bean) || perm(`${perms}:delete`)" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
      </slot>
      <el-button-group v-if="isEdit">
        <el-button :disabled="!hasPrev" @click="handlePrev">{{ $t('form.prev') }}</el-button>
        <el-button :disabled="!hasNext" @click="handleNext">{{ $t('form.next') }}</el-button>
      </el-button-group>
      <el-button type="primary" @click="handleCancel">{{ $t('back') }}</el-button>
      <el-tooltip :content="$t('form.continuous')" placement="top">
        <el-switch v-model="continuous" size="small"></el-switch>
      </el-tooltip>
      <el-tag v-if="unsaved" type="danger">{{ $t('form.unsaved') }}</el-tag>
      <slot name="header-status" :bean="bean" :is-edit="isEdit" :disabled="disabled"></slot>
    </div>
    <el-form ref="form" :class="['mt-5', 'pr-5']" :model="values" :disabled="disabled" :label-width="labelWidth" :label-position="labelPosition" scroll-to-error>
      <slot :bean="bean" :is-edit="isEdit" :disabled="disabled"></slot>
      <div v-if="!disabled" v-loading="buttonLoading">
        <slot name="footer-action" :bean="bean" :is-edit="isEdit" :disabled="disabled" :handle-submit="handleSubmit">
          <el-button :disabled="perm(isEdit ? `${perms}:update` : `${perms}:create`)" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
            {{ $t('save') }}
          </el-button>
        </slot>
      </div>
    </el-form>
  </el-dialog>
</template>

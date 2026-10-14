<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, type PropType, reactive, ref, toRaw, toRefs, watch } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { cloneDeep, isEqual } from 'lodash-es';
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
  // 默认随页面就地渲染（保持既有行为）。开启后对话框 teleport 到 body：
  // 内嵌 Markdown(ByteMD) 编辑器的表单需开启，否则其工具栏下拉（标题级别等）
  // 会被带左侧菜单偏移的 .el-main（overflow:auto）当作 Popper 裁剪边界，
  // 导致下拉被 preventOverflow 横向挤偏约一个图标宽度。
  appendToBody: { type: Boolean, default: false },
  labelPosition: { type: String as PropType<'top' | 'right' | 'left'>, default: 'right' },
  labelWidth: { type: String, default: '150px' },
});
const emit = defineEmits({
  'update:modelValue': null,
  'update:values': null,
  finished: null,
  beanChange: null,
  beforeSubmit: null,
  discard: null,
});

const { name, beanId, beanIds, focus, values, action, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const continuous = ref<boolean>(getContinuous(name.value));
const form = ref<any>();
const bean = ref<any>(props.initValues());
// 基线：表单最近一次与服务端一致时的样子。永远是一份独立的副本——表单上的改动动不到它，“未保存”才可信
const origValues = ref<any>();
const id = ref<any>();
const ids = ref<Array<any>>([]);
// 新增（或复制）保存后留在对话框里继续编辑（adopt）：此后这条就是已有数据，按编辑处理。下次打开对话框时复位
const stayed = ref<boolean>(false);
const isEdit = computed(() => id.value != null && (action.value === 'edit' || stayed.value));
const unsaved = computed(() => {
  // 调试 未保存
  // if (!isEqual(origValues.value, values.value)) {
  //   console.log(JSON.stringify(origValues.value));
  //   console.log(JSON.stringify(values.value));
  // }
  return !loading.value && !isEqual(origValues.value, values.value);
});
const disabled = computed(() => props.disableEdit?.(bean.value) ?? false);
const title = computed(() => `${name.value} - ${isEdit.value ? `${t(disabled.value ? 'detail' : 'edit')} (ID: ${id.value})` : `${t('add')}`}`);
// 最近一次交给父组件的表单对象。父组件的 ref 立刻换成了它，本组件的 props.values 却要等父组件重新渲染后才跟上
let emitted: any;
const emitValues = (next: any) => {
  emitted = next;
  emit('update:values', next);
};
const loadBean = async () => {
  loading.value = true;
  try {
    bean.value = id.value != null ? await props.queryBean(id.value) : props.initValues(values.value, isEdit.value);
    const initial = id.value != null ? props.toValues(bean.value) : bean.value;
    origValues.value = cloneDeep(initial);
    emitValues(cloneDeep(initial));
    emit('beanChange', bean.value, false);
    form.value?.resetFields();
  } catch (e) {
    // 读取失败（数据已删除、不在数据权限之内等）：拦截器已提示错误。关闭对话框——表单里留着的还是上一条数据，不能让人接着保存
    emit('update:modelValue', false);
    throw e;
  } finally {
    loading.value = false;
  }
};
onMounted(() => emitValues(props.initValues()));
watch(visible, () => {
  if (visible.value) {
    // 自己的一份：删除后要从中摘掉已删的 ID，不去改父组件的数组
    ids.value = [...beanIds.value];
    stayed.value = false;
    if (id.value !== beanId.value) {
      id.value = beanId.value;
    } else {
      loadBean();
    }
  }
});
// adopt 把 ID 换成刚创建的那条时不重新读取：表单就是用户正在编辑的那一份
let skipNextLoad = false;
watch(id, () => {
  if (skipNextLoad) {
    skipNextLoad = false;
    return;
  }
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

// 有未保存的修改时，离开当前这条（关闭、返回、上一条、下一条、新增）先问一句。删除、审核等操作之后的自动切换
// 不经过这里：那时表单本来就要丢弃
const confirmDiscard = async (): Promise<boolean> => {
  if (!unsaved.value) {
    return true;
  }
  try {
    await ElMessageBox.confirm(t('form.unsaved.confirm'), t('form.unsaved'), {
      type: 'warning',
      confirmButtonText: t('form.unsaved.discard'),
      cancelButtonText: t('form.unsaved.keep'),
    });
    emit('discard');
    return true;
  } catch {
    return false;
  }
};
const leave = async (proceed: () => void) => {
  if (await confirmDiscard()) {
    proceed();
  }
};
const beforeClose = (done: () => void) => leave(done);
// 刷新、关闭页签：浏览器自己的确认框
const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (visible.value && unsaved.value) {
    event.preventDefault();
    event.returnValue = '';
  }
};
onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload));

// 删除、审核通过等操作之后：连续模式下切到相邻的一条，否则关闭——关闭了就不必再读下一条
const afterRemoved = () => {
  const removedIndex = index.value;
  if (continuous.value && (hasNext.value || hasPrev.value)) {
    if (hasNext.value) {
      handleNext();
    } else {
      handlePrev();
    }
    ids.value.splice(removedIndex, 1);
  } else {
    emit('update:modelValue', false);
  }
};
const handleDelete = async () => {
  buttonLoading.value = true;
  try {
    await props.deleteBean([id.value]);
    afterRemoved();
    ElMessage.success(t('success'));
    emit('finished');
  } finally {
    buttonLoading.value = false;
  }
};

const validateForm = async (): Promise<boolean> => {
  try {
    await form.value.validate();
    return true;
  } catch {
    return false;
  }
};
// 保存成功：基线换成提交那一刻的表单快照。不能取“现在”的表单——请求往返期间用户可能又改了（在富文本里接着打字）
const markSaved = (snapshot?: any) => {
  origValues.value = snapshot ?? cloneDeep(values.value);
};
// 新增成功后换一张空白表单接着录：表单与基线一起换，否则带着上一条留下的默认值就成了“未保存”
const resetForm = () => {
  const blank = props.initValues(values.value, false);
  bean.value = blank;
  origValues.value = cloneDeep(blank);
  emitValues(cloneDeep(blank));
  form.value?.resetFields();
  focus.value?.focus?.();
};
// 新增（或复制）保存后留下来接着编辑：表单原样不动，只补上新 ID；基线取提交时的快照；数据按服务端现值在后台刷新
// （状态、是否可编辑等只读信息），不替换表单
const adopt = async (newId: any, snapshot: any) => {
  stayed.value = true;
  values.value.id = newId;
  snapshot.id = newId;
  origValues.value = snapshot;
  // 列表按新旧排，新建的排在最前
  if (!ids.value.includes(newId)) {
    ids.value.unshift(newId);
  }
  if (id.value !== newId) {
    skipNextLoad = true;
    id.value = newId;
  }
  try {
    bean.value = await props.queryBean(newId);
    emit('beanChange', bean.value, true);
  } catch {
    // 拦截器已提示；表单与基线不受影响
  }
};
// 不算用户改动的表单变更（补默认值等）：表单与基线一起改，不出“未保存”
const patch = (mutate: (target: any) => void) => {
  mutate(values.value);
  // 父组件在换表单的同一轮里（它重新渲染之前，例如它的 watch 回调）调用时，props.values 还是换下来的旧对象：
  // 新对象也要改，否则只改了基线，表单反倒成了“未保存”
  if (emitted != null && emitted !== toRaw(values.value)) {
    mutate(reactive(emitted));
  }
  if (origValues.value != null) {
    mutate(origValues.value);
  }
};

// 一次只处理一个保存请求：连按快捷键、请求未返回时再点按钮，都不发第二次
let saving = false;
// stay：保存后留在对话框里。新增时要留下，创建接口须返回新数据的 ID（Body.result.id）；否则换成空白表单
const handleSubmit = async (stay = false, options: { create?: (data: any) => Promise<any>; message?: string } = {}) => {
  if (saving) {
    return;
  }
  saving = true;
  try {
    await props.beforeValidate?.(values.value);
    if (!(await validateForm())) {
      return;
    }
    buttonLoading.value = true;
    if ((await props.preventSubmit?.(values.value)) ?? false) {
      return;
    }
    // 提交的是副本：before-submit 对它的加工（只为请求服务）不落到打开着的表单上
    const snapshot = cloneDeep(values.value);
    const payload = cloneDeep(snapshot);
    emit('beforeSubmit', payload);
    if (isEdit.value) {
      await props.updateBean(payload);
      markSaved(snapshot);
    } else {
      const created = await (options.create ?? props.createBean)(payload);
      const newId = stay ? created?.result?.id : undefined;
      if (newId != null) {
        await adopt(newId, snapshot);
      } else {
        resetForm();
      }
    }
    ElMessage.success(options.message ?? t('success'));
    if (!continuous.value && !stay) emit('update:modelValue', false);
    emit('finished', bean.value);
  } finally {
    buttonLoading.value = false;
    saving = false;
  }
};
const submit = async (
  // 执行器收到的是表单的副本（已经过 before-submit）。保存了表单内容的执行器成功后调用 markSaved()（编辑）、
  // resetForm()（新增后换空白表单）或 adopt(id)（新增后留下接着编辑）；返回 true 表示留在对话框里
  executor: (
    values: any,
    payload: {
      isEdit: boolean;
      continuous: boolean;
      form: any;
      props: any;
      focus: any;
      loadBean: () => Promise<any>;
      markSaved: () => void;
      resetOrigValues: () => void;
      resetForm: () => void;
      adopt: (newId: any) => Promise<void>;
      emit: any;
    },
  ) => Promise<boolean | undefined | void>,
  // skipPreventSubmit、skipValidate：执行器不保存表单内容时（删除、归档等状态操作）跳过保存前检查与表单校验——
  // 表单里填了一半、校验不过，不该连删除都点不了
  options: { skipPreventSubmit?: boolean; skipValidate?: boolean } = {},
) => {
  if (saving) {
    return;
  }
  saving = true;
  try {
    if (!options.skipValidate && !(await validateForm())) {
      return;
    }
    buttonLoading.value = true;
    if (!options.skipPreventSubmit && ((await props.preventSubmit?.(values.value)) ?? false)) {
      return;
    }
    const snapshot = cloneDeep(values.value);
    const payload = cloneDeep(snapshot);
    emit('beforeSubmit', payload);

    const stay = await executor(payload, {
      isEdit: isEdit.value,
      continuous: continuous.value,
      form: form.value,
      props,
      focus: focus.value,
      loadBean,
      markSaved: () => markSaved(snapshot),
      resetOrigValues: () => markSaved(snapshot),
      resetForm,
      adopt: (newId: any) => adopt(newId, snapshot),
      emit,
    });

    if (!continuous.value && !stay) emit('update:modelValue', false);
    emit('finished', bean.value);
  } finally {
    buttonLoading.value = false;
    saving = false;
  }
};
const remove = async (
  executor: (values: any, payload: { isEdit: boolean; continuous: boolean; form: any; props: any; focus: any; loadBean: () => Promise<any>; emit: any }) => Promise<any>,
) => {
  buttonLoading.value = true;
  try {
    await executor(values.value, { isEdit: isEdit.value, continuous: continuous.value, form: form.value, props, focus: focus.value, loadBean, emit });
    afterRemoved();
    ElMessage.success(t('success'));
    emit('finished');
  } finally {
    buttonLoading.value = false;
  }
};
defineExpose({ form, submit, remove, defaultSubmit: handleSubmit, patch, isEdit, unsaved, disabled });
</script>

<template>
  <el-dialog
    :title="title"
    :close-on-click-modal="!unsaved"
    :model-value="modelValue"
    :close-on-press-escape="!unsaved"
    :width="large ? '98%' : '768px'"
    :top="large ? '16px' : '8vh'"
    :append-to-body="appendToBody"
    :before-close="beforeClose"
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
      <el-button v-if="isEdit && addable" :disabled="perm(`${perms}:create`)" type="primary" :icon="Plus" @click="() => leave(handleAdd)">{{ $t('add') }}</el-button>
      <slot name="header-action" :bean="bean" :is-edit="isEdit" :disabled="disabled" :unsaved="unsaved" :disable-delete="disableDelete" :handle-delete="handleDelete">
        <el-popconfirm v-if="isEdit" :title="$t('confirmDelete')" @confirm="() => handleDelete()">
          <template #reference>
            <el-button :disabled="disableDelete?.(bean) || perm(`${perms}:delete`)" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
      </slot>
      <el-button-group v-if="isEdit">
        <el-button :disabled="!hasPrev" @click="() => leave(handlePrev)">{{ $t('form.prev') }}</el-button>
        <el-button :disabled="!hasNext" @click="() => leave(handleNext)">{{ $t('form.next') }}</el-button>
      </el-button-group>
      <el-button type="primary" @click="() => leave(handleCancel)">{{ $t('back') }}</el-button>
      <el-tooltip :content="$t('form.continuous')" placement="top">
        <el-switch v-model="continuous" size="small"></el-switch>
      </el-tooltip>
      <el-tag v-if="unsaved" type="danger">{{ $t('form.unsaved') }}</el-tag>
      <slot name="header-status" :bean="bean" :is-edit="isEdit" :disabled="disabled"></slot>
    </div>
    <el-form ref="form" :class="['mt-5', 'pr-5']" :model="values" :disabled="disabled" :label-width="labelWidth" :label-position="labelPosition" scroll-to-error>
      <slot :bean="bean" :is-edit="isEdit" :disabled="disabled"></slot>
      <div v-if="!disabled" v-loading="buttonLoading">
        <slot name="footer-action" :bean="bean" :is-edit="isEdit" :disabled="disabled" :unsaved="unsaved" :handle-submit="handleSubmit">
          <el-button :disabled="perm(isEdit ? `${perms}:update` : `${perms}:create`)" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
            {{ $t('save') }}
          </el-button>
        </slot>
      </div>
    </el-form>
  </el-dialog>
</template>

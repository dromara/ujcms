<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { currentUser } from '@/stores/useCurrentUser';
import { queryModel, createModel, updateModel, deleteModel } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import { isReadonlyModel, isSystemModel } from './components/modelRules';
import type { ModelUsage } from './components/modelRules';

// 模型基本信息（名称、共享范围）。字段集由“系统字段”“自定义字段”各自保存（model-management M2）：
// 这里的保存只回传名称与共享范围，服务端也只写这两列。
defineOptions({
  name: 'ModelForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  modelType: { type: String, required: true },
  // copy：以 beanId 所指模型的字段集为初值新建（DialogForm 的 copy 模式）
  action: { type: String as PropType<'edit' | 'copy'>, default: 'edit' },
  addable: { type: Boolean, default: true },
  // 当前页签已有的名称：同类型可见集内不重名（服务端同规，M7）
  existingNames: { type: Array as PropType<string[]>, default: () => [] },
  // 使用统计（按模型 ID 索引）：被其它站点使用的全局模型不可改为本站私有；使用中的模型不可删除
  usages: { type: Object as PropType<Record<string, ModelUsage>>, default: () => ({}) },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const focus = ref<any>();
const values = ref<any>({});
const usageOf = (bean: any): ModelUsage => props.usages[String(bean?.id)] ?? { modelId: String(bean?.id), count: 0, external: 0 };
// 全局模型被其它站点的栏目、区块等绑定时不可改为本站私有（服务端同规）
const privateLocked = (bean: any) => bean?.scope === 2 && usageOf(bean).external > 0;
const readonly = (bean: any) => props.action !== 'copy' && isReadonlyModel(bean);
const toValues = (bean: any) => {
  if (props.action === 'copy') {
    // 副本：名称加后缀、默认本站私有；字段集原样带入（新增接口接受字段集）
    return { type: bean.type, scope: 0, name: t('model.copyName', { name: bean.name }), mains: bean.mains, asides: bean.asides, customs: bean.customs };
  }
  return { ...bean };
};
// 编辑时打开的名称：自己的名称不算重名。副本与新增没有“自己的名称”
const originalName = ref<string>();
const nameRules = [
  { required: true, message: () => t('v.required') },
  {
    validator: (rule: any, value: string, callback: any) => {
      if (value && value !== originalName.value && props.existingNames.includes(value)) {
        callback(t('model.error.nameExist'));
        return;
      }
      callback();
    },
  },
];
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.model')"
    :query-bean="queryModel"
    :create-bean="createModel"
    :update-bean="updateModel"
    :delete-bean="deleteModel"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :action="action"
    :addable="addable"
    :init-values="(): any => ({ type: modelType, scope: 0 })"
    :to-values="toValues"
    :disable-edit="(bean: any) => readonly(bean)"
    :disable-delete="(bean: any) => isSystemModel(bean) || usageOf(bean).count > 0"
    perms="model"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @bean-change="(bean: any) => (originalName = action === 'edit' ? bean?.name : undefined)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ bean, isEdit }">
      <el-form-item prop="name" :label="$t('model.name')" :rules="nameRules">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="scope" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="model.scope" help /></template>
        <el-radio-group v-model="values.scope" :disabled="isEdit && isSystemModel(values)">
          <el-radio :value="0" :disabled="isEdit && privateLocked(bean)">{{ $t('model.scope.0') }}</el-radio>
          <el-radio :value="2" :disabled="!currentUser.globalPermission">{{ $t('model.scope.2') }}</el-radio>
        </el-radio-group>
        <div v-if="isEdit && privateLocked(bean) && !isSystemModel(values)" class="w-full text-xs text-gray-secondary leading-5">{{ $t('model.scope.lockedTip') }}</div>
      </el-form-item>
      <el-form-item :label="$t('model.type')">
        <span>{{ $t(`model.type.${values.type ?? modelType}`) }}</span>
      </el-form-item>
    </template>
  </dialog-form>
</template>

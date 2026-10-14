<script setup lang="ts">
import { ref, watch, toRefs, type PropType } from 'vue';
import { queryFormType, createFormType, updateFormType, deleteFormType, queryModelList, queryFormListTemplates, queryFormItemTemplates } from '@/api/config';
import { queryProcessDefinitionList } from '@/api/system';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'FormTypeForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { modelValue: visible } = toRefs(props);
const focus = ref<any>();
const values = ref<any>({});
const processList = ref<any[]>([]);
const modelList = ref<any[]>([]);
const listTemplates = ref<any[]>([]);
const itemTemplates = ref<any[]>([]);

const fetchProcessList = async () => {
  processList.value = await queryProcessDefinitionList({ category: 'sys_form', latestVersion: true });
};

const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'form' });
};

const fetchListTemplates = async () => {
  listTemplates.value = await queryFormListTemplates();
};
const fetchItemTemplates = async () => {
  itemTemplates.value = await queryFormItemTemplates();
};

watch(visible, () => {
  if (visible.value) {
    fetchModelList();
    fetchProcessList();
    fetchListTemplates();
    fetchItemTemplates();
  }
});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('formType.title')"
    :query-bean="queryFormType"
    :create-bean="createFormType"
    :update-bean="updateFormType"
    :delete-bean="deleteFormType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ mode: 2, single: false, viewable: false, enabled: true })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="formType"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <!-- 删除类型在列表页的顶栏：类型下还有表单时那里禁用并说明原因，此处不再提供。
         插槽内容为空时 Vue 会回落到默认的删除按钮，故放一个 hidden 占位（带 hidden 属性的元素不参与 space-x 间距） -->
    <template #header-action><span hidden></span></template>
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="formType.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="modelId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="formType.model" /></template>
        <el-select v-model="values.modelId" class="w-full">
          <el-option v-for="item in modelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="processKey">
        <template #label><label-tip message="formType.processKey" help /></template>
        <el-select v-model="values.processKey" clearable class="w-full">
          <el-option v-for="item in processList" :key="item.key" :label="item.name" :value="item.key"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="listTemplate">
        <template #label><label-tip message="formType.listTemplate" help /></template>
        <el-select v-model="values.listTemplate" clearable class="w-full">
          <el-option v-for="item in listTemplates" :key="item" :label="item + '.html'" :value="item"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="itemTemplate">
        <template #label><label-tip message="formType.itemTemplate" help /></template>
        <el-select v-model="values.itemTemplate" clearable class="w-full">
          <el-option v-for="item in itemTemplates" :key="item" :label="item + '.html'" :value="item"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="mode" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="formType.mode" help /></template>
        <el-select v-model="values.mode" class="w-full">
          <el-option v-for="n in [0, 1, 2]" :key="n" :label="$t(`formType.mode.${n}`)" :value="n"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="viewable" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="formType.viewable" help /></template>
        <el-switch v-model="values.viewable"></el-switch>
      </el-form-item>
      <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="formType.enabled" /></template>
        <el-switch v-model="values.enabled"></el-switch>
      </el-form-item>
    </template>
  </dialog-form>
</template>

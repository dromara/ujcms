<script setup lang="ts">
import { onMounted, ref, type PropType } from 'vue';
import { queryProcessModel, createProcessModel, updateProcessModel, deleteProcessModel } from '@/api/system';
import { queryDictListByAlias } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'ProcessModelForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });

const focus = ref<any>();
const values = ref<any>({});
const categoryList = ref<any[]>([]);
const fetchCategoryList = async () => {
  categoryList.value = await queryDictListByAlias('sys_process_category');
};
onMounted(() => {
  fetchCategoryList();
});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.processModel')"
    :query-bean="queryProcessModel"
    :create-bean="createProcessModel"
    :update-bean="updateProcessModel"
    :delete-bean="deleteProcessModel"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ category: categoryList[0]?.value })"
    :to-values="(bean) => ({ ...bean })"
    perms="processModel"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ isEdit }">
      <el-form-item
        prop="key"
        :rules="[
          { required: true, message: () => $t('v.required') },
          { pattern: /^[A-Za-z][\w-]*$/, message: () => $t('processModel.error.key') },
        ]"
      >
        <template #label><label-tip message="processModel.key" /></template>
        <el-input ref="focus" v-model="values.key" maxlength="50" :disabled="isEdit" :placeholder="$t('processModel.key.placeholder')"></el-input>
      </el-form-item>
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="processModel.name" /></template>
        <el-input v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="category" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="processModel.category" help /></template>
        <el-select v-model="values.category" :disabled="isEdit" class="w-full">
          <el-option v-for="item in categoryList" :key="item.value" :value="item.value" :label="item.name" />
        </el-select>
      </el-form-item>
    </template>
  </dialog-form>
</template>

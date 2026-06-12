<script setup lang="ts">
import { ref, PropType } from 'vue';
import dayjs from 'dayjs';
import { queryTag, createTag, updateTag, deleteTag } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'TagForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.content.tag')"
    :query-bean="queryTag"
    :create-bean="createTag"
    :update-bean="updateTag"
    :delete-bean="deleteTag"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="(): any => ({})"
    :to-values="(bean) => ({ ...bean })"
    perms="tag"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ isEdit }">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="tag.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="description">
        <template #label><label-tip message="tag.description" /></template>
        <el-input v-model="values.description" maxlength="1000" type="textarea" :rows="5"></el-input>
      </el-form-item>
      <el-form-item v-if="isEdit" prop="created">
        <template #label><label-tip message="tag.created" /></template>
        <el-input :model-value="dayjs(values.created).format('YYYY-MM-DD HH:mm:ss')" disabled></el-input>
      </el-form-item>
      <el-form-item v-if="isEdit" prop="userId">
        <template #label><label-tip message="tag.user" /></template>
        <el-input :model-value="values.user?.username" disabled></el-input>
      </el-form-item>
      <el-form-item v-if="isEdit" prop="refers">
        <template #label><label-tip message="tag.refers" /></template>
        <el-input :model-value="values.refers" disabled></el-input>
      </el-form-item>
    </template>
  </dialog-form>
</template>

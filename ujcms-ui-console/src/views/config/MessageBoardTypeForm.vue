<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryMessageBoardType, createMessageBoardType, updateMessageBoardType, deleteMessageBoardType } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'MessageBoardTypeForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.messageBoardType')"
    :query-bean="queryMessageBoardType"
    :create-bean="createMessageBoardType"
    :update-bean="updateMessageBoardType"
    :delete-bean="deleteMessageBoardType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({})"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="messageBoardType"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="messageBoardType.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="30"></el-input>
      </el-form-item>
      <el-form-item prop="description">
        <template #label><label-tip message="messageBoardType.description" /></template>
        <el-input v-model="values.description" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
    </template>
  </dialog-form>
</template>

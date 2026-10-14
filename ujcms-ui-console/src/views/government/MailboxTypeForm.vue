<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { queryMailboxType, createMailboxType, updateMailboxType, deleteMailboxType } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'MailboxTypeForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('mailboxType.title')"
    :query-bean="queryMailboxType"
    :create-bean="createMailboxType"
    :update-bean="updateMailboxType"
    :delete-bean="deleteMailboxType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({})"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="mailboxType"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="mailboxType.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="30"></el-input>
      </el-form-item>
      <el-form-item prop="description">
        <template #label><label-tip message="mailboxType.description" /></template>
        <el-input v-model="values.description" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
    </template>
  </dialog-form>
</template>

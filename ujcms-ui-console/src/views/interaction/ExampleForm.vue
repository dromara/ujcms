<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryExample, createExample, updateExample, deleteExample } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'ExampleForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.interaction.example')"
    :query-bean="queryExample"
    :create-bean="createExample"
    :update-bean="updateExample"
    :delete-bean="deleteExample"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ enabled: true })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    perms="example"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="example.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="30"></el-input>
      </el-form-item>
      <el-form-item prop="description">
        <template #label><label-tip message="example.description" /></template>
        <el-input v-model="values.description" type="textarea" maxlength="150"></el-input>
      </el-form-item>
      <el-form-item prop="height">
        <template #label><label-tip message="example.height" help /></template>
        <el-input-number v-model="values.height" :min="1" :max="500" />
      </el-form-item>
      <el-form-item prop="birthday">
        <template #label><label-tip message="example.birthday" /></template>
        <el-date-picker v-model="values.birthday" type="datetime" />
      </el-form-item>
      <el-form-item prop="enabled">
        <template #label><label-tip message="example.enabled" /></template>
        <el-switch v-model="values.enabled" />
      </el-form-item>
    </template>
  </dialog-form>
</template>

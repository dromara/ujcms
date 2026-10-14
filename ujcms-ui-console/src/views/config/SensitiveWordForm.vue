<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { querySensitiveWord, createSensitiveWord, updateSensitiveWord, deleteSensitiveWord } from '@/api/system';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'SensitiveWordForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  // 词库为全局数据：没有全局数据权限时只读
  readonly: { type: Boolean, default: false },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.sensitiveWord')"
    :query-bean="querySensitiveWord"
    :create-bean="createSensitiveWord"
    :update-bean="updateSensitiveWord"
    :delete-bean="deleteSensitiveWord"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ enabled: true })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    :disable-edit="() => readonly"
    :disable-delete="() => readonly"
    :addable="!readonly"
    perms="sensitiveWord"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, whitespace: true, message: () => $t('v.required') }">
        <template #label><label-tip message="sensitiveWord.name" help /></template>
        <el-input ref="focus" v-model="values.name" maxlength="30"></el-input>
      </el-form-item>
      <el-form-item prop="enabled" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="sensitiveWord.enabled" /></template>
        <el-switch v-model="values.enabled" />
      </el-form-item>
    </template>
  </dialog-form>
</template>

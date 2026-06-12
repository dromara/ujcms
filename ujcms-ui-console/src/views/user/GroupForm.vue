<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryGroup, createGroup, updateGroup, deleteGroup } from '@/api/user';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'GroupForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.user.group')"
    :query-bean="queryGroup"
    :create-bean="createGroup"
    :update-bean="updateGroup"
    :delete-bean="deleteGroup"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 2, allAccessPermission: true })"
    :to-values="(bean) => ({ ...bean })"
    :disable-delete="(bean) => bean.id <= 10"
    perms="group"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :label="$t('group.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="description" :label="$t('group.description')">
        <el-input v-model="values.description" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item prop="allAccessPermission">
        <template #label><label-tip message="group.allAccessPermission" /></template>
        <el-switch v-model="values.allAccessPermission"></el-switch>
      </el-form-item>
      <el-form-item prop="type" :label="$t('group.type')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-select v-model="values.type" :disabled="values.type === 1">
          <el-option v-for="n in [1, 2]" :key="n" :disabled="n === 1" :label="$t(`group.type.${n}`)" :value="n"></el-option>
        </el-select>
      </el-form-item>
    </template>
  </dialog-form>
</template>

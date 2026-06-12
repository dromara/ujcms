<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryDict, createDict, updateDict, deleteDict } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';

defineOptions({
  name: 'DictForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  type: { type: Object, default: null },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
const deletable = (bean: any) => bean.id >= 500;
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.content.dict')"
    :query-bean="queryDict"
    :create-bean="createDict"
    :update-bean="updateDict"
    :delete-bean="deleteDict"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="(): any => ({ typeId: type?.id, enabled: true })"
    :to-values="(bean) => ({ ...bean })"
    :disable-delete="(bean: any) => !deletable(bean)"
    perms="dict"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ isEdit }">
      <el-form-item prop="typeId" :label="$t('dict.type')">
        <el-select v-model="values.typeId" disabled>
          <el-option :value="type?.id" :label="type?.name"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item prop="name" :label="$t('dict.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="value" :label="$t('dict.value')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input-number v-if="type?.dataType === 1" v-model="values.value" :disabled="isEdit && !deletable(values)" :min="-2147483648" :max="2147483647"></el-input-number>
        <el-input v-else v-model="values.value" :disabled="isEdit && !deletable(values)" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="remark" :label="$t('dict.remark')">
        <el-input v-model="values.remark" type="textarea" :rows="2" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item prop="enabled" :label="$t('dict.enabled')">
        <el-switch v-model="values.enabled" :disabled="isEdit && !deletable(values)"></el-switch>
      </el-form-item>
      <el-form-item prop="sys" :label="$t('dict.sys')">
        <el-switch v-model="values.sys" disabled></el-switch>
      </el-form-item>
    </template>
  </dialog-form>
</template>

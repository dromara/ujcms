<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryModel, createModel, updateModel, deleteModel } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';

defineOptions({
  name: 'ModelForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  modelType: { type: String, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
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
    :init-values="(): any => ({ type: modelType, scope: 0 })"
    :to-values="(bean) => ({ ...bean })"
    :disable-delete="(bean) => bean.id <= 10"
    perms="model"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :label="$t('model.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="scope" :label="$t('model.scope')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-radio-group v-model="values.scope" :disabled="values.id < 10">
          <el-radio v-for="n in [0, 2]" :key="n" :value="n">{{ $t(`model.scope.${n}`) }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item prop="type" :label="$t('model.type')">
        <el-select v-model="values.type" disabled>
          <el-option :value="modelType" :label="$t(`model.type.${modelType}`)"></el-option>
        </el-select>
      </el-form-item>
    </template>
  </dialog-form>
</template>

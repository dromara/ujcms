<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryDictType, createDictType, updateDictType, deleteDictType, dictTypeAliasExist } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'DictTypeForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
const deletable = (bean: any) => bean.id >= 100;
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.config.dictType')"
    :query-bean="queryDictType"
    :create-bean="createDictType"
    :update-bean="updateDictType"
    :delete-bean="deleteDictType"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ scope: 0, dataType: 0 })"
    :to-values="(bean) => ({ ...bean })"
    :disable-delete="(bean: any) => !deletable(bean)"
    perms="dictType"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{ bean, isEdit }">
      <el-form-item prop="name" :label="$t('dictType.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item
        prop="alias"
        :label="$t('dictType.alias')"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if (value !== bean.alias && (await dictTypeAliasExist(value, values.scope))) {
                callback($t('dictType.error.aliasExist'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <el-input v-model="values.alias" :disabled="isEdit && !deletable(values)" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="remark" :label="$t('dictType.remark')">
        <el-input v-model="values.remark" type="textarea" :rows="2" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item prop="dataType" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="dictType.dataType" help /></template>
        <el-radio-group v-model="values.dataType" :disabled="isEdit">
          <el-radio v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`dictType.dataType.${n}`) }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item prop="scope" :label="$t('dictType.scope')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-radio-group v-model="values.scope" :disabled="isEdit && !deletable(values)">
          <el-radio v-for="n in [0, 2]" :key="n" :value="n">{{ $t(`dictType.scope.${n}`) }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item prop="sys" :label="$t('dictType.sys')">
        <el-switch v-model="values.sys" disabled></el-switch>
      </el-form-item>
    </template>
  </dialog-form>
</template>

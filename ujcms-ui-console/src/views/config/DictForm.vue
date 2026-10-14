<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { queryDict, createDict, updateDict, deleteDict, dictValueExist } from '@/api/config';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'DictForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  type: { type: Object, default: null },
  // 当前类型已有的键值：新增时据此建议下一个键值
  existingValues: { type: Array as PropType<string[]>, default: () => [] },
  // 只读：全局类型的条目属全局数据，无全局数据权限时只可查看（服务端同规）
  readonly: { type: Boolean, default: false },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
// 系统条目（sys）由安装骨架写入：键值/启用状态锁定、不可删除（服务端同规）
const locked = (bean: any) => !!bean?.sys;
// 键值建议：已有键值全为整数时取最大值 + 1（整型类型尚无条目时从 1 起）。连续新增时 DialogForm 先于 finished
// 调用 initValues，列表尚未刷新，故一并计入刚保存的键值（prev，仅限同一类型——它也可能是上次编辑留下的旧值）
const suggestValue = (prev?: any) => {
  const list = props.existingValues.map(String);
  if (prev?.value != null && prev.value !== '' && String(prev.typeId) === String(props.type?.id)) {
    list.push(String(prev.value));
  }
  const integer = props.type?.dataType === 1;
  if (list.length === 0) {
    return integer ? 1 : undefined;
  }
  if (!list.every((item) => /^-?\d+$/.test(item))) {
    return undefined;
  }
  const next = Math.max(...list.map(Number)) + 1;
  return integer ? next : String(next);
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('dict.label')"
    :query-bean="queryDict"
    :create-bean="createDict"
    :update-bean="updateDict"
    :delete-bean="deleteDict"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="(prev?: any): any => ({ typeId: type?.id, enabled: true, value: suggestValue(prev) })"
    :to-values="(bean) => ({ ...bean })"
    :disable-edit="() => readonly"
    :disable-delete="(bean: any) => readonly || locked(bean)"
    :addable="!readonly"
    perms="dict"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #header-status>
      <el-tag v-if="type" type="info" disable-transitions>{{ type.name }}</el-tag>
    </template>
    <template #default="{ bean, isEdit }">
      <el-form-item prop="name" :label="$t('dict.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item
        prop="value"
        :label="$t('dict.value')"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule, value, callback) => {
              if (value != null && String(value) !== String(bean.value ?? '') && (await dictValueExist(values.typeId, String(value)))) {
                callback($t('dict.error.valueExist'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <el-input-number v-if="type?.dataType === 1" v-model="values.value" :disabled="isEdit && locked(values)" :min="-2147483648" :max="2147483647"></el-input-number>
        <el-input v-else v-model="values.value" :disabled="isEdit && locked(values)" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="remark" :label="$t('dict.remark')">
        <el-input v-model="values.remark" type="textarea" :rows="2" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item prop="enabled" :label="$t('dict.enabled')">
        <el-switch v-model="values.enabled" :disabled="isEdit && locked(values)"></el-switch>
      </el-form-item>
      <el-form-item v-if="isEdit" prop="sys">
        <template #label><label-tip message="dict.sys" help /></template>
        <el-switch v-model="values.sys" disabled></el-switch>
      </el-form-item>
    </template>
  </dialog-form>
</template>

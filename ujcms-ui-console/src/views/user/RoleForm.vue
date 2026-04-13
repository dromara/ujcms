<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryRole, createRole, updateRole, deleteRole, roleScopeNotAllowed } from '@/api/user';
import { currentUser } from '@/stores/useCurrentUser';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'RoleForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });

const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.user.role')"
    :query-bean="queryRole"
    :create-bean="createRole"
    :update-bean="updateRole"
    :delete-bean="deleteRole"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 4, rank: currentUser.rank + 1, scope: 0 })"
    :to-values="(bean) => ({ ...bean })"
    :disable-delete="(bean) => bean.id <= 1"
    :disable-edit="(bean) => (bean.global && !currentUser.globalPermission) || currentUser.rank > bean.rank"
    perms="role"
    :model-value="modelValue"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="(event) => $emit('finished')"
  >
    <template #default="{ bean, disabled }">
      <el-form-item prop="name" :label="$t('role.name')" :rules="{ required: true, message: () => $t('v.required') }">
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="description" :label="$t('role.description')">
        <el-input v-model="values.description" maxlength="300"></el-input>
      </el-form-item>
      <el-form-item prop="rank" :rules="[{ required: true, message: () => $t('v.required') }]">
        <template #label><label-tip message="role.rank" help /></template>
        <el-input-number v-model.number="values.rank" :min="disabled ? 0 : currentUser.rank" :max="32767" />
      </el-form-item>
      <el-form-item v-if="currentUser.epRank >= 3" prop="type" :rules="[{ required: true, message: () => $t('v.required') }]">
        <template #label><label-tip message="role.type" help /></template>
        <el-select v-model="values.type">
          <el-option v-for="item in [1, 2, 3, 4]" :key="item" :label="$t(`role.type.${item}`)" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item
        prop="scope"
        :label="$t('block.scope')"
        :rules="[
          { required: true, message: () => $t('v.required') },
          {
            asyncValidator: async (rule: any, value: any, callback: any) => {
              if (value !== bean.scope && (await roleScopeNotAllowed(values.scope, values.id))) {
                callback($t('role.error.scopeNotAllowd'));
                return;
              }
              callback();
            },
          },
        ]"
      >
        <el-radio-group v-model="values.scope">
          <el-radio v-for="n in [0, 2]" :key="n" :value="n" :disabled="!currentUser.globalPermission">{{ $t(`role.scope.${n}`) }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </template>
  </dialog-form>
</template>

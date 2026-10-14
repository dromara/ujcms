<script setup lang="ts">
import { ref } from 'vue';
import { queryLoginLog, createLoginLog, updateLoginLog, deleteLoginLog } from '@/api/log';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'LoginLogForm',
});
const visible = defineModel<boolean>({ default: false });
defineProps<{ beanId?: string; beanIds: string[] }>();
defineEmits({ finished: null });

const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model="visible"
    v-model:values="values"
    :name="$t('menu.log.loginLog')"
    :query-bean="queryLoginLog"
    :create-bean="createLoginLog"
    :update-bean="updateLoginLog"
    :delete-bean="deleteLoginLog"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :init-values="(): any => ({})"
    :to-values="(bean) => ({ ...bean })"
    perms="loginLog"
    :disable-edit="() => true"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="ip">
        <template #label><label-tip message="loginLog.ip" /></template>
        <el-input :model-value="values.ip"></el-input>
      </el-form-item>
      <el-form-item prop="created" :label="$t('loginLog.created')">
        <el-date-picker :model-value="values.created" type="datetime"></el-date-picker>
      </el-form-item>
      <el-form-item prop="user">
        <template #label><label-tip message="loginLog.user" /></template>
        <el-input :model-value="values.user?.username"></el-input>
      </el-form-item>
      <el-form-item prop="loginName">
        <template #label><label-tip message="loginLog.loginName" /></template>
        <el-input :model-value="values.loginName"></el-input>
      </el-form-item>
      <el-form-item prop="type">
        <template #label><label-tip message="loginLog.type" /></template>
        <el-tag v-if="[1, 2].includes(values.type)">{{ $t(`loginLog.type.${values.type}`) }}</el-tag>
        <el-tag v-else-if="values.type != null" type="info">{{ $t(`loginLog.type.${values.type}`) }}</el-tag>
      </el-form-item>
      <el-form-item prop="status">
        <template #label><label-tip message="loginLog.status" /></template>
        <el-tag v-if="values.status === 0" type="success">{{ $t(`loginLog.status.${values.status}`) }}</el-tag>
        <el-tag v-else-if="[1, 2].includes(values.status)" type="danger">{{ $t(`loginLog.status.${values.status}`) }}</el-tag>
        <el-tag v-else-if="[3, 4, 5].includes(values.status)" type="warning">{{ $t(`loginLog.status.${values.status}`) }}</el-tag>
        <el-tag v-else-if="values.status != null" type="info">{{ $t(`loginLog.status.${values.status}`) }}</el-tag>
      </el-form-item>
    </template>
  </dialog-form>
</template>

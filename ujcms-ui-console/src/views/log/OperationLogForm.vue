<script setup lang="ts">
import { ref, type PropType } from 'vue';
import dayjs from 'dayjs';
import { queryOperationLog, createOperationLog, updateOperationLog, deleteOperationLog } from '@/api/log';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';

defineOptions({
  name: 'OperationLogForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.log.operationLog')"
    :query-bean="queryOperationLog"
    :create-bean="createOperationLog"
    :update-bean="updateOperationLog"
    :delete-bean="deleteOperationLog"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="(): any => ({})"
    :to-values="(bean) => ({ ...bean })"
    perms="operationLog"
    :model-value="modelValue"
    :addable="false"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-row>
        <el-col :span="12">
          <el-form-item prop="name">
            <template #label><label-tip message="operationLog.name" /></template>
            <el-input :model-value="values.name && $t(`log.${values.name}`)" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="module">
            <template #label><label-tip message="operationLog.module" /></template>
            <el-input :model-value="values.module && $t(`log.${values.module}`)" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="user">
            <template #label><label-tip message="operationLog.user" /></template>
            <el-input :model-value="values.user?.username" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="created">
            <template #label><label-tip message="operationLog.created" /></template>
            <el-input :model-value="dayjs(values.created).format('YYYY-MM-DD HH:mm:ss')" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="ip">
            <template #label><label-tip message="operationLog.ip" /></template>
            <el-input :model-value="values.ip" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="requestMethod">
            <template #label><label-tip message="operationLog.requestMethod" /></template>
            <el-input :model-value="values.requestMethod" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="type">
            <template #label><label-tip message="operationLog.type" /></template>
            <el-tag size="small">{{ values.type != null && $t(`operationLog.type.${values.type}`) }}</el-tag>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="status">
            <template #label><label-tip message="operationLog.status" /></template>
            <el-tag v-if="values.status === 1" type="success" size="small">{{ values.status && $t(`operationLog.status.${values.status}`) }}</el-tag>
            <el-tag v-else type="danger" size="small">{{ values.status != null && $t(`operationLog.status.${values.status}`) }}</el-tag>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="requestUrl">
            <template #label><label-tip message="operationLog.requestUrl" /></template>
            <el-input :model-value="values.requestUrl" type="textarea" :rows="2" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="requestBody">
            <template #label><label-tip message="operationLog.requestBody" /></template>
            <el-input :model-value="values.requestBody" type="textarea" :autosize="{ minRows: 3, maxRows: 18 }" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="responseEntity">
            <template #label><label-tip message="operationLog.responseEntity" /></template>
            <el-input :model-value="values.responseEntity" type="textarea" :autosize="{ minRows: 3, maxRows: 24 }" readonly></el-input>
          </el-form-item>
        </el-col>
        <el-col v-if="values.exceptionStack != null" :span="24">
          <el-form-item prop="exceptionStack">
            <template #label><label-tip message="operationLog.exceptionStack" /></template>
            <el-input :model-value="values.exceptionStack" type="textarea" :autosize="{ minRows: 3, maxRows: 18 }" readonly></el-input>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>

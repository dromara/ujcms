<script setup lang="ts">
import { ref, PropType } from 'vue';
import { queryTask, createTask, updateTask, deleteTask } from '@/api/system';
import DialogForm from '@/components/DialogForm.vue';

defineOptions({
  name: 'TaskForm',
});
defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
});
defineEmits({ 'update:modelValue': null, finished: null });
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.system.task')"
    :query-bean="queryTask"
    :create-bean="createTask"
    :update-bean="updateTask"
    :delete-bean="deleteTask"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :init-values="(): any => ({ user: {} })"
    :to-values="(bean) => ({ ...bean })"
    :disable-edit="() => true"
    :addable="false"
    perms="task"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-row>
        <el-col :span="12">
          <el-form-item prop="name" :label="$t('task.name')">
            <el-input v-model="values.name"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="user.username" :label="$t('task.user')">
            <el-input v-model="values.user.username"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="beginDate" :label="$t('task.beginDate')">
            <el-date-picker v-model="values.beginDate" type="datetime"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="endDate" :label="$t('task.endDate')">
            <el-date-picker v-model="values.endDate" type="datetime"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="current" :label="$t('task.current')">
            <el-input v-model="values.current"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="processedIn" :label="$t('task.processedIn')">
            <el-input v-model="values.processedIn"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="type" :label="$t('task.type')">
            <el-select v-if="values.type" v-model="values.type">
              <el-option :value="values.type" :label="$t(`task.type.${values.type}`)"></el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="$t('task.status')">
            <el-tag v-if="values.status === 0" type="info">{{ $t(`task.status.${values.status}`) }}</el-tag>
            <el-tag v-if="values.status === 1">{{ $t(`task.status.${values.status}`) }}</el-tag>
            <el-tag v-if="values.status === 2" type="danger">{{ $t(`task.status.${values.status}`) }}</el-tag>
            <el-tag v-if="values.status === 3" type="warning">{{ $t(`task.status.${values.status}`) }}</el-tag>
            <el-tag v-if="values.status === 4" type="success">{{ $t(`task.status.${values.status}`) }}</el-tag>
          </el-form-item>
        </el-col>
        <el-col v-if="values.errorInfo" :span="24">
          <el-form-item :label="$t('task.errorInfo')">
            <pre><code class="whitespace-pre-wrap">{{ values.errorInfo }}</code></pre>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>

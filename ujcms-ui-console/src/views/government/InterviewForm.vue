<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { queryInterview, createInterview, updateInterview, deleteInterview } from '@/api/interaction';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';
import Tinymce from '@/components/Tinymce';
import InterviewGuestsInput from './InterviewGuestsInput.vue';

defineOptions({
  name: 'InterviewForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.government.interview')"
    :query-bean="queryInterview"
    :create-bean="createInterview"
    :update-bean="updateInterview"
    :delete-bean="deleteInterview"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ questionEnabled: true })"
    :to-values="(bean) => ({ ...bean })"
    perms="interview"
    :model-value="modelValue"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #header-status="{ isEdit }">
      <template v-if="isEdit">
        <span v-if="values.status != null">
          <el-tag v-if="values.status === 0" type="info" size="small" disable-transitions>{{ $t(`interview.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 1" type="danger" size="small" disable-transitions>{{ $t(`interview.status.${values.status}`) }}</el-tag>
          <el-tag v-else-if="values.status === 2" type="success" size="small" disable-transitions>{{ $t(`interview.status.${values.status}`) }}</el-tag>
          <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
          <el-tag v-if="values.published" type="success" size="small" disable-transitions class="ml-1">{{ $t('interview.op.publish') }}</el-tag>
        </span>
      </template>
    </template>
    <template #default="{ isEdit }">
      <el-row>
        <el-col :span="24">
          <el-form-item prop="title" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="interview.title" /></template>
            <el-input ref="focus" v-model="values.title" maxlength="150"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="org">
            <template #label><label-tip message="interview.org" /></template>
            <el-input v-model="values.org" maxlength="100"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="host">
            <template #label><label-tip message="interview.host" /></template>
            <el-input v-model="values.host" maxlength="50"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="guests" :rules="{ max: 300, message: () => $t('v.maxLength', { maxLength: 300 }) }">
            <template #label><label-tip message="interview.guests" help /></template>
            <interview-guests-input v-model="values.guests"></interview-guests-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="beginDate" :rules="{ required: true, message: () => $t('v.required') }">
            <template #label><label-tip message="interview.beginDate" /></template>
            <el-date-picker v-model="values.beginDate" type="datetime" class="w-full"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="endDate">
            <template #label><label-tip message="interview.endDate" /></template>
            <el-date-picker v-model="values.endDate" type="datetime" class="w-full"></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="image">
            <template #label><label-tip message="interview.image" /></template>
            <image-upload v-model="values.image"></image-upload>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="questionEnabled">
            <template #label><label-tip message="interview.questionEnabled" help /></template>
            <el-switch v-model="values.questionEnabled" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="description">
            <template #label><label-tip message="interview.description" /></template>
            <el-input v-model="values.description" type="textarea" :rows="4" maxlength="1000"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item prop="text">
            <template #label><label-tip message="interview.text" /></template>
            <tinymce v-model="values.text" class="w-full" />
          </el-form-item>
        </el-col>
        <template v-if="isEdit">
          <el-col :span="12">
            <el-form-item prop="user">
              <template #label><label-tip message="interview.user" /></template>
              <el-input :model-value="values.user?.username" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="created">
              <template #label><label-tip message="interview.created" /></template>
              <el-date-picker :model-value="values.created" type="datetime" class="w-full" disabled></el-date-picker>
            </el-form-item>
          </el-col>
        </template>
      </el-row>
    </template>
  </dialog-form>
</template>

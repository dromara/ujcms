<script setup lang="ts">
import { computed, ref, type PropType } from 'vue';
import { queryLink, createLink, updateLink, deleteLink } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import { ImageUpload } from '@/components/Upload';
import ChannelRefInput from '@/views/design/fields/ChannelRefInput.vue';
import ArticleRefInput from '@/views/design/fields/ArticleRefInput.vue';

defineOptions({
  name: 'LinkForm',
});
// 链接组列表与当前链接组由列表页传入：链接组就在同一屏增删，挂载时自取一次会过期；新增的链接默认投入当前链接组
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  groups: { type: Array as PropType<any[]>, required: true },
  groupId: { type: String, default: undefined },
});
defineEmits({ 'update:modelValue': null, finished: null });
const focus = ref<any>();
const values = ref<any>({});
const currentGroup = computed(() => props.groups.find((item) => item.id === values.value.groupId));
// 三选一（refType 仅前端态）：切换即清空另两个目标字段，避免旧值随提交残留
const handleRefTypeChange = (refType: string) => {
  if (refType !== 'url') values.value.linkUrl = undefined;
  if (refType !== 'channel') values.value.channelId = undefined;
  if (refType !== 'article') values.value.articleId = undefined;
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('link.label')"
    :query-bean="queryLink"
    :create-bean="createLink"
    :update-bean="updateLink"
    :delete-bean="deleteLink"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ groupId, refType: 'url', targetBlank: true, enabled: true })"
    :to-values="(bean) => ({ ...bean, refType: bean.channelId != null ? 'channel' : bean.articleId != null ? 'article' : 'url' })"
    :model-value="modelValue"
    perms="link"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="link.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
      </el-form-item>
      <el-form-item prop="refType">
        <template #label><label-tip message="link.refType" /></template>
        <el-radio-group v-model="values.refType" @change="(val) => handleRefTypeChange(String(val))">
          <el-radio value="url">{{ $t('link.refType.url') }}</el-radio>
          <el-radio value="channel">{{ $t('link.refType.channel') }}</el-radio>
          <el-radio value="article">{{ $t('link.refType.article') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="values.refType === 'url'"
        prop="linkUrl"
        :rules="[
          { required: true, message: () => $t('v.required') },
          { pattern: /^(http|\/).*$/, message: () => $t('link.error.url') },
        ]"
      >
        <template #label><label-tip message="link.url" help /></template>
        <el-input v-model="values.linkUrl" maxlength="255"></el-input>
      </el-form-item>
      <el-form-item v-if="values.refType === 'channel'" prop="channelId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="link.channel" /></template>
        <channel-ref-input v-model="values.channelId"></channel-ref-input>
      </el-form-item>
      <el-form-item v-if="values.refType === 'article'" prop="articleId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="link.article" /></template>
        <article-ref-input v-model="values.articleId"></article-ref-input>
      </el-form-item>
      <el-form-item prop="groupId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="link.group" /></template>
        <el-select v-model="values.groupId" class="w-full">
          <el-option v-for="item in groups" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="currentGroup?.type === 2" prop="logo" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="link.logo" /></template>
        <image-upload v-model="values.logo" :width="currentGroup.imageWidth" :height="currentGroup.imageHeight" mode="manual"></image-upload>
      </el-form-item>
      <el-form-item prop="description">
        <template #label><label-tip message="link.description" /></template>
        <el-input v-model="values.description" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
      <el-form-item prop="targetBlank">
        <template #label><label-tip message="link.targetBlank" /></template>
        <el-switch v-model="values.targetBlank" />
      </el-form-item>
      <el-form-item prop="enabled">
        <template #label><label-tip message="link.enabled" /></template>
        <el-switch v-model="values.enabled" />
      </el-form-item>
    </template>
  </dialog-form>
</template>

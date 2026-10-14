<script setup lang="ts">
import { ref, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { CopyDocument } from '@element-plus/icons-vue';
import { queryWechatAccount, createWechatAccount, updateWechatAccount, deleteWechatAccount, previewWechatAccountStyle } from '@/api/wechat';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import WechatPreviewDialog from './WechatPreviewDialog.vue';

defineOptions({
  name: 'WechatAccountForm',
});
defineProps({ modelValue: { type: Boolean, required: true }, beanId: { type: String, default: null }, beanIds: { type: Array as PropType<string[]>, required: true } });
defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();
const focus = ref<any>();
const values = ref<any>({});

// 排版预设代码，与后端 WechatStyler.PRESETS 一致
const stylePresets = ['none', 'simple', 'elegant'];
const styleProblems = ref<string[]>([]);
const previewVisible = ref<boolean>(false);
const previewContent = ref<string>('');
const previewLoading = ref<boolean>(false);
// 以示例正文预览当前预设与自定义规则的效果，同时列出无法解析的规则（保存时后端会拒绝）
const handleStylePreview = async () => {
  previewLoading.value = true;
  try {
    const { content, problems } = await previewWechatAccountStyle({ stylePreset: values.value.stylePreset, styleRules: values.value.styleRules });
    styleProblems.value = problems ?? [];
    previewContent.value = content;
    previewVisible.value = true;
  } finally {
    previewLoading.value = false;
  }
};

const callbackUrl = (id: string): string => new URL(`${import.meta.env.VITE_BASE_API}/wechat/callback/${id}`, window.location.href).href;
const handleCopy = async (text: string) => {
  await navigator.clipboard.writeText(text);
  ElMessage.success(t('success'));
};
</script>

<template>
  <dialog-form
    v-model:values="values"
    :name="$t('menu.wechat.wechatAccount')"
    :query-bean="queryWechatAccount"
    :create-bean="createWechatAccount"
    :update-bean="updateWechatAccount"
    :delete-bean="deleteWechatAccount"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="() => ({ type: 0, logRetentionDays: 0, stylePreset: 'simple' })"
    :to-values="(bean) => ({ ...bean })"
    :model-value="modelValue"
    label-width="260px"
    perms="wechatAccount"
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="() => $emit('finished')"
  >
    <template #default="{}">
      <el-form-item prop="name" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatAccount.name" /></template>
        <el-input ref="focus" v-model="values.name" maxlength="30"></el-input>
      </el-form-item>
      <el-form-item prop="appId" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatAccount.appId" /></template>
        <el-input v-model="values.appId" maxlength="21"></el-input>
      </el-form-item>
      <el-form-item prop="appSecret" :rules="{ required: true, message: () => $t('v.required') }">
        <template #label><label-tip message="wechatAccount.appSecret" /></template>
        <el-input v-model="values.appSecret" maxlength="42" show-password></el-input>
      </el-form-item>
      <el-form-item prop="type">
        <template #label><label-tip message="wechatAccount.type" /></template>
        <el-radio-group v-model="values.type">
          <el-radio-button v-for="n in [0, 1]" :key="n" :value="n">{{ $t(`wechatAccount.type.${n}`) }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item prop="token">
        <template #label><label-tip message="wechatAccount.token" help /></template>
        <el-input v-model="values.token" maxlength="32"></el-input>
      </el-form-item>
      <el-form-item prop="aesKey">
        <template #label><label-tip message="wechatAccount.aesKey" help /></template>
        <el-input v-model="values.aesKey" maxlength="43"></el-input>
      </el-form-item>
      <el-form-item v-if="values.id">
        <template #label><label-tip message="wechatAccount.callbackUrl" help /></template>
        <el-input :model-value="callbackUrl(values.id)" readonly>
          <template #append>
            <el-button :icon="CopyDocument" @click="() => handleCopy(callbackUrl(values.id))">{{ $t('copy') }}</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item prop="logRetentionDays">
        <template #label><label-tip message="wechatAccount.logRetentionDays" help /></template>
        <el-input-number v-model="values.logRetentionDays" :min="0" :max="3650" :step="1" step-strictly></el-input-number>
        <span class="ml-2 text-xs text-gray-secondary">{{ $t('wechatAccount.logRetentionDays.unit') }}</span>
      </el-form-item>
      <el-form-item prop="stylePreset">
        <template #label><label-tip message="wechatAccount.stylePreset" help /></template>
        <el-select v-model="values.stylePreset" class="w-48">
          <el-option v-for="code in stylePresets" :key="code" :value="code" :label="$t(`wechatAccount.stylePreset.${code}`)"></el-option>
        </el-select>
        <el-button class="ml-2" :loading="previewLoading" @click="() => handleStylePreview()">{{ $t('wechatAccount.style.preview') }}</el-button>
      </el-form-item>
      <el-form-item prop="styleRules">
        <template #label><label-tip message="wechatAccount.styleRules" help /></template>
        <el-input v-model="values.styleRules" type="textarea" :rows="6" maxlength="20000" :placeholder="$t('wechatAccount.styleRules.placeholder')" class="font-mono"></el-input>
        <el-alert v-if="styleProblems.length > 0" type="error" :closable="false" class="mt-2">
          <div>{{ $t('wechatAccount.style.problems') }}</div>
          <ul class="pl-4 list-disc">
            <li v-for="(problem, index) in styleProblems" :key="index">{{ problem }}</li>
          </ul>
        </el-alert>
      </el-form-item>
      <el-form-item prop="remark">
        <template #label><label-tip message="wechatAccount.remark" /></template>
        <el-input v-model="values.remark" type="textarea" :rows="3" maxlength="300"></el-input>
      </el-form-item>
      <wechat-preview-dialog v-model="previewVisible" :title="$t('wechatAccount.style.sampleTitle')" :account-name="values.name" :content="previewContent" />
    </template>
  </dialog-form>
</template>

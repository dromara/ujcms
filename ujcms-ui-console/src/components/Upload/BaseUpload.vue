<script setup lang="ts">
import { ref, toRefs, computed, PropType } from 'vue';
import { UploadFile, UploadFiles } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { handleError } from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { imageUploadUrl, videoUploadUrl, audioUploadUrl, docUploadUrl, fileUploadUrl } from '@/api/config';

const props = defineProps({
  type: {
    type: String as PropType<'image' | 'video' | 'audio' | 'library' | 'doc' | 'file' | 'any'>,
    default: 'file',
    validator: (value: string) => ['image', 'video', 'audio', 'library', 'doc', 'file', 'any'].includes(value),
  },
  button: { type: String, default: null },
  data: { type: Object as PropType<Record<string, any>>, default: null },
  uploadAction: { type: String, default: null },
  fileAccept: { type: String, default: null },
  fileMaxSize: { type: Number, default: null },
  multiple: { type: Boolean },
  disabled: { type: Boolean, default: false },
  onSuccess: { type: Function as PropType<((response: any, uploadFile: UploadFile, uploadFiles: UploadFiles) => void) | undefined>, default: null },
});

const { type, uploadAction, fileAccept, fileMaxSize } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const progressFile = ref<any>({});
const action = computed(() => {
  if (uploadAction?.value != null) {
    return uploadAction.value;
  }
  switch (type.value) {
    case 'image':
      return imageUploadUrl;
    case 'video':
      return videoUploadUrl;
    case 'audio':
      return audioUploadUrl;
    case 'library':
      return docUploadUrl;
    case 'doc':
      return docUploadUrl;
    case 'file':
      return fileUploadUrl;
    default:
      throw new Error(`Type not support: ${type.value}`);
  }
});
const accept = computed(() => {
  if (fileAccept?.value != null) {
    return fileAccept.value;
  }
  switch (type.value) {
    case 'image':
      return sysConfig.upload.imageInputAccept;
    case 'video':
      return sysConfig.upload.videoInputAccept;
    case 'audio':
      return sysConfig.upload.audioInputAccept;
    case 'library':
      return sysConfig.upload.libraryInputAccept;
    case 'doc':
      return sysConfig.upload.docInputAccept;
    case 'file':
      return sysConfig.upload.fileInputAccept;
    case 'any':
      return undefined;
    default:
      throw new Error(`Type not support: ${type.value}`);
  }
});
const maxSize = computed(() => {
  if (fileMaxSize?.value != null) {
    return fileMaxSize.value;
  }
  switch (type.value) {
    case 'image':
      return sysConfig.upload.imageLimitByte;
    case 'video':
      return sysConfig.upload.videoLimitByte;
    case 'audio':
      return sysConfig.upload.audioLimitByte;
    case 'library':
      return sysConfig.upload.libraryLimitByte;
    case 'doc':
      return sysConfig.upload.docLimitByte;
    case 'file':
      return sysConfig.upload.fileLimitByte;
    default:
      return 0;
  }
});
const beforeUpload = (file: any) => {
  if (maxSize.value > 0 && file.size > maxSize.value) {
    ElMessage.error(t('error.fileMaxSize', { size: `${maxSize.value / 1024 / 1024} MB` }));
    return false;
  }
  return true;
};
const onError = (error: Error) => {
  handleError(JSON.parse(error.message));
};
</script>

<template>
  <div>
    <el-upload
      :action="action"
      :headers="{ ...getAuthHeaders(), ...getSiteHeaders() }"
      :data="data"
      :accept="accept"
      :before-upload="beforeUpload"
      :on-success="onSuccess"
      :on-progress="(event, file) => (progressFile = file)"
      :on-error="onError"
      :show-file-list="false"
      :disabled="disabled"
      :multiple="multiple"
      drag
    >
      <!--
      // 用于测试上传进度条
      action="https://jsonplaceholder.typicode.com/posts/"
      -->
      <slot>
        <span>{{ button ?? $t('clickOrDragToUpload') }}</span>
        <!-- <el-button type="primary" :disabled="disabled">{{ button ?? $t('clickOrDragToUpload') }}</el-button> -->
      </slot>
    </el-upload>
    <el-progress v-if="progressFile.status === 'uploading'" :percentage="parseInt(progressFile.percentage, 10)"></el-progress>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-upload-dragger) {
  padding: 0 20px;
  @apply bg-primary-lighter text-primary;
}
</style>

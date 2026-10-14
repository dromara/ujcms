<script setup lang="ts">
import { ref, toRefs, computed, type PropType } from 'vue';
import { type UploadFile, type UploadFiles } from 'element-plus';
import { UploadFilled, FolderOpened } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { handleError } from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { isAcceptedExtension, acceptToText } from '@/utils/upload';
import { extractMediaMeta } from '@/utils/media';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { imageUploadUrl, videoUploadUrl, audioUploadUrl, docUploadUrl, fileUploadUrl } from '@/api/config';
import FilePicker from '@/components/FilePicker/FilePicker.vue';

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
  // 是否显示“从资源库选择”入口
  pickable: { type: Boolean, default: false },
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
// 视频、音频上传时，由浏览器提取时长和视频截图，随上传表单一起提交。提取失败不影响上传
const uploadData = computed(() => {
  if (type.value !== 'video' && type.value !== 'audio') {
    return props.data;
  }
  const mediaType = type.value;
  return async (rawFile: File) => ({ ...props.data, ...(await extractMediaMeta(rawFile, mediaType)) });
});
const beforeUpload = (file: any) => {
  // accept 属性不限制拖拽，需在此手动校验扩展名，拦截拖入的不支持文件
  if (!isAcceptedExtension(file.name, accept.value)) {
    ElMessage.error(t('error.fileType', { types: acceptToText(accept.value) }));
    return false;
  }
  if (maxSize.value > 0 && file.size > maxSize.value) {
    ElMessage.error(t('error.fileMaxSize', { size: `${maxSize.value / 1024 / 1024} MB` }));
    return false;
  }
  return true;
};
const onError = (error: Error) => {
  handleError(JSON.parse(error.message));
};

const pickerVisible = ref(false);
const pickAccept = computed<'image' | 'video' | 'audio' | 'doc' | 'file' | 'any'>(() => {
  switch (type.value) {
    case 'image':
      return 'image';
    case 'video':
      return 'video';
    case 'audio':
      return 'audio';
    case 'doc':
      return 'doc';
    case 'file':
      return 'file';
    default:
      return 'any';
  }
});
const handlePick = (items: any) => {
  const list = Array.isArray(items) ? items : [items];
  list.forEach((item) => {
    props.onSuccess?.(item, { name: item.name, url: item.url } as any, [] as any);
  });
};
</script>

<template>
  <div :class="{ 'w-full': pickable }">
    <el-upload
      :action="action"
      :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
      :data="uploadData"
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
      <slot>
        <!-- 拖拽区内放两个明确按钮：上传（点击冒泡触发文件选择）、从资源库选择（.stop 阻止冒泡，改为打开选择器）；整框仍可拖拽 -->
        <div class="dropzone">
          <el-button type="primary" :icon="UploadFilled" :disabled="disabled">{{ button ?? $t('upload') }}</el-button>
          <el-button v-if="pickable" :icon="FolderOpened" :disabled="disabled" @click.stop="pickerVisible = true">
            {{ $t('resource.library') }}
          </el-button>
        </div>
      </slot>
    </el-upload>
    <el-progress v-if="progressFile.status === 'uploading'" :percentage="parseInt(progressFile.percentage, 10)"></el-progress>
    <file-picker v-if="pickable" v-model="pickerVisible" :accept="pickAccept" :multiple="multiple" @pick="handlePick" />
  </div>
</template>

<style lang="scss" scoped>
/* 非 pickable（如文件管理“上传/ZIP上传”按钮）：紧凑的内联按钮样式 */
:deep(.el-upload-dragger) {
  padding: 0 20px;
  @apply bg-primary-lighter text-primary;
}
/* pickable：拖拽上传区作为主操作铺满整行，并加高内边距形成投放区 */
.w-full :deep(.el-upload),
.w-full :deep(.el-upload-dragger) {
  width: 100%;
}
.w-full :deep(.el-upload-dragger) {
  padding: 12px 20px;
}
.dropzone {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 12px;
}
/* 由 flex gap 统一控制间距，去掉 EP 相邻按钮自带的 margin 以免双倍间隔 */
.dropzone :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>

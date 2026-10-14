<script setup lang="ts">
import { computed, ref, toRefs } from 'vue';
import { useFormItem } from 'element-plus';
import { UploadFilled, View, Delete, FolderOpened } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { handleError } from '@/utils/request';
import { isAcceptedExtension, acceptToText } from '@/utils/upload';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { videoUploadUrl } from '@/api/config';
import FilePicker from '@/components/FilePicker/FilePicker.vue';

/**
 * 视频上传卡片（对照 ImageUpload 的媒体卡片交互，去掉裁剪/压缩等图片专属能力）。
 * 上传走 video-upload 接口（登记附件，返回 duration 与自动抽帧封面 image）；
 * uploadSuccess 事件带出完整响应，供调用方按需预填封面等兄弟字段。
 */
const props = defineProps({
  modelValue: { type: String, default: null },
  fileAccept: { type: String, default: null },
  fileMaxSize: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
});

const emit = defineEmits({ 'update:modelValue': null, uploadSuccess: null });

const { modelValue, fileAccept, fileMaxSize } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const progressFile = ref<any>({});
const previewVisible = ref<boolean>(false);
const { formItem } = useFormItem();
const src = computed({
  get: (): string | undefined => modelValue.value,
  set: (val?: string) => {
    emit('update:modelValue', val);
    formItem?.validate?.('change').catch((err: any) => {
      if (import.meta.env.MODE !== 'production') {
        console.warn(err);
      }
    });
  },
});
const accept = computed(() => fileAccept.value ?? sysConfig.upload.videoInputAccept);
const maxSize = computed(() => fileMaxSize.value ?? sysConfig.upload.videoLimitByte);
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
const onSuccess = (res: any) => {
  src.value = res.url;
  emit('uploadSuccess', res);
};
const onError = (error: Error) => {
  handleError(JSON.parse(error.message));
};
const pickerVisible = ref<boolean>(false);
const handlePick = (items: any) => {
  const item = Array.isArray(items) ? items[0] : items;
  if (item?.url) {
    src.value = item.url;
  }
};
</script>

<template>
  <el-upload
    :action="videoUploadUrl"
    :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
    :accept="accept"
    :before-upload="beforeUpload"
    :show-file-list="false"
    :on-success="onSuccess"
    :on-error="onError"
    :on-progress="(event: any, file: any) => (progressFile = file)"
    :disabled="disabled"
    :drag="!src"
  >
    <div v-if="src" class="relative full-flex-center rounded-border hover:border-opacity-0">
      <!-- preload=metadata 加 muted：仅取首帧作缩略展示，不预载视频数据 -->
      <video :src="src" preload="metadata" muted class="block max-w-full max-h-full"></video>
      <!-- 两行操作：上传/资源库（更换视频来源）在上，预览/删除在下。上传图标点击冒泡触发原生上传，其余 .stop 阻止冒泡 -->
      <div class="absolute bg-black bg-opacity-60 rounded-md opacity-0 cursor-pointer full-flex-center flex-col gap-2 hover:opacity-100">
        <div v-if="!disabled" class="flex space-x-4">
          <el-icon class="video-action" :title="$t('upload')"><UploadFilled /></el-icon>
          <el-icon class="video-action" :title="$t('resource.library')" @click.stop="() => (pickerVisible = true)"><FolderOpened /></el-icon>
        </div>
        <div class="flex space-x-4">
          <el-icon class="video-action" :title="$t('previewVideo')" @click.stop="() => (previewVisible = true)"><View /></el-icon>
          <el-icon class="video-action" :title="$t('deleteVideo')" @click.stop="() => (src = undefined)"><Delete /></el-icon>
        </div>
      </div>
    </div>
    <el-progress v-else-if="progressFile.status === 'uploading'" type="circle" :percentage="parseInt(progressFile.percentage, 10)" />
    <!-- 空态卡片内放两个图标操作：加号（点击/拖拽上传，冒泡触发）、资源库（.stop 阻止冒泡，改为打开选择器） -->
    <div v-else class="el-upload--picture-card add-card">
      <el-icon class="add-card__action" :title="$t('upload')"><UploadFilled /></el-icon>
      <el-icon v-if="!disabled" class="add-card__action" :title="$t('resource.library')" @click.stop="pickerVisible = true"><FolderOpened /></el-icon>
    </div>
  </el-upload>
  <div>
    <el-dialog v-model="previewVisible" top="5vh" :width="768" append-to-body destroy-on-close>
      <el-input v-model="src">
        <template #prepend>URL</template>
      </el-input>
      <video :src="src" controls class="mt-1 w-full bg-black"></video>
    </el-dialog>
  </div>
  <file-picker v-model="pickerVisible" accept="video" @pick="handlePick" />
</template>

<style lang="scss" scoped>
:deep(.el-dialog__headerbtn) {
  top: 4px;
}
:deep(.el-upload) {
  width: 148px;
  height: 148px;
}
.full-flex-center {
  @apply w-full h-full flex justify-center items-center;
}
.rounded-border {
  border: 1px solid #c0ccda;
  @apply rounded-md bg-gray-50;
}
.video-action {
  @apply cursor-pointer text-xl text-white;
}
:deep(.el-upload-dragger) {
  padding: 0;
}
:deep(.el-upload--picture-card) {
  border: 0;
}
.add-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
}
.add-card__action {
  font-size: 24px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: color 0.2s;
}
.add-card__action:hover {
  color: var(--el-color-primary);
}
</style>

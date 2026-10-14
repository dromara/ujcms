<script setup lang="ts">
import { computed, ref, toRefs, type PropType } from 'vue';
import { useFormItem } from 'element-plus';
import { UploadFilled, Crop, View, Delete, FolderOpened } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { handleError } from '@/utils/request';
import { isAcceptedExtension, acceptToText } from '@/utils/upload';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { imageUploadUrl, avatarUploadUrl } from '@/api/config';
import ImageCropper from './ImageCropper.vue';
import FilePicker from '@/components/FilePicker/FilePicker.vue';

// 'image/jpg,image/jpeg,image/png,image/gif'

const props = defineProps({
  modelValue: { type: String, default: null },
  fileAccept: { type: String, default: null },
  fileMaxSize: { type: Number, default: null },
  width: { type: Number, default: null },
  height: { type: Number, default: null },
  /**
   * 仅锁定裁剪宽高比（宽/高），不限制输出尺寸——区别于 width/height（同时决定裁剪输出的最大尺寸）
   */
  aspectRatio: { type: Number, default: null },
  /**
   * none: 原图上传, cut: 自动裁剪, resize: 自动压缩, manual: 手动裁剪
   */
  mode: { type: String as PropType<'none' | 'cut' | 'resize' | 'manual'>, default: 'none' },
  /**
   * image: 图片上传, avatar: 头像上传
   */
  type: { type: String as PropType<'image' | 'avatar'>, default: 'image' },
  disabled: { type: Boolean, default: false },
});

const emit = defineEmits({ 'update:modelValue': null, cropSuccess: null });

const { modelValue, type, width, height, mode, fileAccept, fileMaxSize } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const progressFile = ref<any>({});
const previewVisible = ref<boolean>(false);
const cropperVisible = ref<boolean>(false);
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
const resizable = computed(() => ['cut', 'resize'].includes(mode.value));
const data = computed(() => {
  const params: any = { resizeMode: mode.value === 'cut' ? 'cut' : 'normal' };
  if (width.value != null) {
    // 为0不限制，为空则依然受全局图片宽高限制
    params.maxWidth = resizable.value ? width.value : 0;
  }
  if (height.value != null) {
    // 为0不限制，为空则依然受全局图片宽高限制
    params.maxHeight = resizable.value ? height.value : 0;
  }
  return params;
});
const accept = computed(() => fileAccept.value ?? sysConfig.upload.imageInputAccept);
const maxSize = computed(() => fileMaxSize.value ?? sysConfig.upload.imageLimitByte);
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
const onCropSuccess = (url: string) => {
  src.value = url;
  emit('cropSuccess', url);
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
    :action="type === 'avatar' ? avatarUploadUrl : imageUploadUrl"
    :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
    :accept="accept"
    :before-upload="beforeUpload"
    :data="data"
    :show-file-list="false"
    :on-success="(res: any) => ((src = res.url), (cropperVisible = mode === 'manual'))"
    :on-error="onError"
    :on-progress="(event: any, file: any) => (progressFile = file)"
    :disabled="disabled"
    :drag="!src"
  >
    <!--
    // 用于测试上传进度条
    action="https://jsonplaceholder.typicode.com/posts/"
     -->
    <div v-if="src" class="relative full-flex-center rounded-border hover:border-opacity-0">
      <img :src="src" class="block max-w-full max-h-full" />
      <!-- 两行操作：上传/资源库（更换图片来源）在上，裁剪/预览/删除在下。上传图标点击冒泡触发原生上传，其余 .stop 阻止冒泡 -->
      <div class="absolute bg-black bg-opacity-60 rounded-md opacity-0 cursor-pointer full-flex-center flex-col gap-2 hover:opacity-100">
        <div v-if="!disabled" class="flex space-x-4">
          <el-icon class="image-action" :title="$t('upload')"><UploadFilled /></el-icon>
          <el-icon class="image-action" :title="$t('resource.library')" @click.stop="() => (pickerVisible = true)"><FolderOpened /></el-icon>
        </div>
        <div class="flex space-x-4">
          <el-icon class="image-action" :title="$t('cropImage')" @click.stop="() => (cropperVisible = true)"><Crop /></el-icon>
          <el-icon class="image-action" :title="$t('previewImage')" @click.stop="() => (previewVisible = true)"><View /></el-icon>
          <el-icon class="image-action" :title="$t('deleteImage')" @click.stop="() => (src = undefined)"><Delete /></el-icon>
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
      <img :src="src" alt="" class="mt-1 border border-gray-300" />
    </el-dialog>
  </div>
  <image-cropper v-model="cropperVisible" :type="type" :src="src" :width="width" :height="height" :aspect-ratio="aspectRatio" @success="onCropSuccess" />
  <file-picker v-model="pickerVisible" accept="image" @pick="handlePick" />
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
.image-action {
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

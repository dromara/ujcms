<script setup lang="ts">
import { ref, toRefs, computed, watch, type PropType } from 'vue';
import { useFormItem } from 'element-plus';
import { UploadFilled, Crop, View, Delete, FolderOpened } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { handleError } from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { isAcceptedExtension, acceptToText } from '@/utils/upload';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { imageUploadUrl } from '@/api/config';
import ImageCropper from './ImageCropper.vue';
import FilePicker from '@/components/FilePicker/FilePicker.vue';

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  fileAccept: { type: String, default: null },
  fileMaxSize: { type: Number, default: null },
  maxWidth: { type: Number, default: null },
  maxHeight: { type: Number, default: null },
  listType: { type: String as PropType<'pictureCard' | 'picture'>, default: 'pictureCard' },
  disabled: { type: Boolean, default: false },
});

const emit = defineEmits({ 'update:modelValue': null });

const { modelValue, maxWidth, maxHeight, fileAccept, fileMaxSize } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const dragging = ref<boolean>(false);
const progressFile = ref<any>({});
const currentFile = ref<any>({});
const previewVisible = ref<boolean>(false);
const cropperVisible = ref<boolean>(false);
const previewFile = ref<any>({ src: 'data:;base64,=' });
const fileList = computed({
  get: (): any[] => modelValue.value,
  set: (val: any) => emit('update:modelValue', val),
});
const { formItem } = useFormItem();
watch(
  fileList,
  () => {
    formItem?.validate?.('change').catch((err: any) => {
      if (import.meta.env.MODE !== 'production') {
        console.warn(err);
      }
    });
  },
  { deep: true },
);
const handlePreview = (file: any) => {
  previewFile.value = file;
  previewVisible.value = true;
};
const thumbnailWidth = 300;
const thumbnailHeight = 300;
const getData = () => {
  const data: any = { isWatermark: true, thumbnailWidth, thumbnailHeight };
  if (maxWidth?.value != null) {
    data.maxWidth = maxWidth.value;
  }
  if (maxHeight?.value != null) {
    data.maxHeight = maxHeight.value;
  }
  return data;
};
const accept = computed(() => fileAccept?.value ?? sysConfig.upload.imageInputAccept);
const maxSize = computed(() => fileMaxSize?.value ?? sysConfig.upload.imageLimitByte);
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
const pickerVisible = ref<boolean>(false);
// 资源库选择目标：指向某个图集项时表示“替换该项”，为 null 时表示“追加新图”
const replaceTarget = ref<any>(null);
const handlePick = (items: any) => {
  const list = Array.isArray(items) ? items : [items];
  if (replaceTarget.value != null) {
    const picked = list.find((it: any) => it?.url);
    if (picked) {
      replaceTarget.value.url = picked.url;
      replaceTarget.value.name = picked.name;
    }
    replaceTarget.value = null;
    return;
  }
  list.forEach((item) => {
    if (item?.url) {
      fileList.value.push({ name: item.name, url: item.url });
    }
  });
};
// 通过一个隐藏的 el-upload 实现“替换某张图集图片”，避免在图集项内嵌 el-upload 影响图标排版
const replaceUploadRef = ref<HTMLElement>();
const triggerReplaceUpload = (file: any) => {
  replaceTarget.value = file;
  replaceUploadRef.value?.querySelector<HTMLInputElement>('input[type="file"]')?.click();
};
const onReplaceUploadSuccess = (res: any) => {
  if (replaceTarget.value != null) {
    replaceTarget.value.url = res.url;
    replaceTarget.value.name = res.name;
    replaceTarget.value = null;
  }
};
</script>

<template>
  <div>
    <!-- <transition-group tag="ul" :class="['el-upload-list', 'el-upload-list--picture-card', { 'is-disabled': disabled }]" name="el-list"> -->
    <draggable
      :list="fileList"
      tag="ul"
      item-key="url"
      :animation="250"
      class="el-upload-list"
      :class="[listType === 'picture' ? 'el-upload-list--picture' : 'el-upload-list--picture-card', { 'is-disabled': disabled }]"
      @start="() => (dragging = true)"
      @end="() => (dragging = false)"
    >
      <template #item="{ element: file }">
        <li class="el-upload-list__item is-success">
          <div :class="listType === 'picture' ? ['w-32', 'h-32'] : ['w-full', 'h-full']" class="relative flex items-center justify-center bg-gray-50">
            <img class="block max-w-full max-h-full" :src="file.url" alt="" />
            <!-- 两行操作：上传/资源库（替换当前项的图片来源）在上，裁剪/预览/删除在下 -->
            <div
              class="absolute bg-black bg-opacity-60 rounded-md opacity-0 cursor-move full-flex-center flex-col gap-2"
              :class="dragging ? undefined : 'hover:opacity-100'"
              @click.stop
            >
              <div v-if="!disabled" class="flex items-center space-x-4">
                <el-icon class="image-action" :title="$t('upload')" @click="() => triggerReplaceUpload(file)"><UploadFilled /></el-icon>
                <el-icon class="image-action" :title="$t('resource.library')" @click="() => ((replaceTarget = file), (pickerVisible = true))"><FolderOpened /></el-icon>
              </div>
              <div class="flex items-center space-x-4">
                <el-icon class="image-action" :title="$t('cropImage')" @click="() => ((cropperVisible = true), (currentFile = file))"><Crop /></el-icon>
                <el-icon class="image-action" :title="$t('previewImage')" @click="() => handlePreview(file)"><View /></el-icon>
                <el-icon class="image-action" :title="$t('deleteImage')" @click="() => fileList.splice(fileList.indexOf(file), 1)"><Delete /></el-icon>
              </div>
            </div>
          </div>
          <div v-if="listType === 'picture'" class="ml-2">
            <el-input v-model="file.url" placeholder="URL" maxlength="255">
              <template #prepend>URL</template>
            </el-input>
            <el-input v-model="file.name" :placeholder="$t('article.imageList.name')" class="mt-1">
              <template #prepend>{{ $t('article.imageList.name') }}</template>
            </el-input>
            <el-input v-model="file.description" type="textarea" :rows="2" :placeholder="$t('article.imageList.description')" class="mt-1"></el-input>
          </div>
        </li>
      </template>
      <template #footer>
        <el-upload
          :action="imageUploadUrl"
          :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
          :data="getData()"
          :accept="accept"
          :before-upload="beforeUpload"
          :on-success="(res: any, file: any) => fileList.push({ name: res.name, url: res.url })"
          :on-progress="(event: any, file: any) => (progressFile = file)"
          :on-error="onError"
          :show-file-list="false"
          :disabled="disabled"
          multiple
          drag
        >
          <el-progress v-if="progressFile.status === 'uploading'" type="circle" :percentage="parseInt(progressFile.percentage, 10)" />
          <!-- 添加卡片内放两个图标操作：加号（点击/拖拽上传，冒泡触发）、资源库（.stop 阻止冒泡，改为打开选择器） -->
          <div v-else class="el-upload--picture-card add-card">
            <el-icon class="add-card__action" :title="$t('upload')"><UploadFilled /></el-icon>
            <el-icon v-if="!disabled" class="add-card__action" :title="$t('resource.library')" @click.stop="() => ((replaceTarget = null), (pickerVisible = true))"><FolderOpened /></el-icon>
          </div>
        </el-upload>
      </template>
    </draggable>
    <!-- </transition-group> -->
    <file-picker v-model="pickerVisible" accept="image" multiple @pick="handlePick" />
    <!-- 隐藏上传器：点击图集项的“上传”图标时触发其文件选择，上传成功后替换该项图片 -->
    <div ref="replaceUploadRef" class="hidden">
      <el-upload
        :action="imageUploadUrl"
        :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
        :data="getData()"
        :accept="accept"
        :before-upload="beforeUpload"
        :on-success="onReplaceUploadSuccess"
        :on-error="onError"
        :show-file-list="false"
      >
        <span></span>
      </el-upload>
    </div>
    <div>
      <el-dialog v-model="previewVisible" top="5vh" :width="768">
        <el-input v-model="previewFile.url" maxlength="255">
          <template #prepend>URL</template>
        </el-input>
        <el-input v-if="listType !== 'picture'" v-model="previewFile.name" :placeholder="$t('article.imageList.name')" class="mt-1">
          <template #prepend>{{ $t('article.imageList.name') }}</template>
        </el-input>
        <el-input
          v-if="listType !== 'picture'"
          v-model="previewFile.description"
          type="textarea"
          :rows="2"
          :placeholder="$t('article.imageList.description')"
          class="mt-1"
        ></el-input>
        <img :src="previewFile.url" alt="" class="mt-1 border border-gray-300" />
      </el-dialog>
    </div>
    <image-cropper
      v-model="cropperVisible"
      :src="currentFile.url"
      :thumbnail-width="thumbnailWidth"
      :thumbnail-height="thumbnailHeight"
      @success="(url) => (currentFile.url = url)"
    ></image-cropper>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-dialog__headerbtn) {
  top: 4px;
}
.full-flex-center {
  @apply w-full h-full flex justify-center items-center;
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
// 修复进度圈位置不正确
:deep(.el-upload-list--picture-card .el-progress) {
  left: 0;
  transform: none;
}
// 修复拖动图集无动画效果
:deep(.el-upload-list__item) {
  transition: none;
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

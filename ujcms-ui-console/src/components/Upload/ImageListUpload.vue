<script setup lang="ts">
import { ref, toRefs, computed, watch, PropType } from 'vue';
import { useFormItem } from 'element-plus';
import { Plus, Crop, View, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { handleError } from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { imageUploadUrl } from '@/api/config';
import ImageCropper from './ImageCropper.vue';

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
            <div
              class="absolute space-x-4 bg-black bg-opacity-50 rounded-md opacity-0 cursor-move full-flex-center"
              :class="dragging ? undefined : 'hover:opacity-100'"
              @click.stop
            >
              <el-icon class="image-action" :title="$t('cropImage')" @click="() => ((cropperVisible = true), (currentFile = file))"><Crop /></el-icon>
              <el-icon class="image-action" :title="$t('previewImage')" @click="() => handlePreview(file)"><View /></el-icon>
              <el-icon class="image-action" :title="$t('deleteImage')" @click="() => fileList.splice(fileList.indexOf(file), 1)"><Delete /></el-icon>
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
          :headers="{ ...getAuthHeaders(), ...getSiteHeaders() }"
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
          <div v-else class="el-upload--picture-card">
            <el-icon><Plus /></el-icon>
          </div>
        </el-upload>
      </template>
    </draggable>
    <!-- </transition-group> -->
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
</style>

<script setup lang="ts">
import { ref, toRefs, computed, watch } from 'vue';
import { useFormItem } from 'element-plus';
import { Close, Document, CircleCheck, UploadFilled, FolderOpened } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { handleError } from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { isAcceptedExtension, acceptToText } from '@/utils/upload';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { fileUploadUrl } from '@/api/config';
import FilePicker from '@/components/FilePicker/FilePicker.vue';

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  fileAccept: { type: String, default: null },
  fileMaxSize: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
});
const emit = defineEmits({ 'update:modelValue': null });

const { fileAccept, fileMaxSize, modelValue } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const progressFile = ref<any>({});
const fileList = computed({
  get: (): any[] => modelValue.value,
  set: (val) => emit('update:modelValue', val),
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
const previewVisible = ref<boolean>(false);
const previewFile = ref<any>({});
const form = ref<any>();

const handlePreview = (file: any) => {
  previewFile.value = file;
  previewVisible.value = true;
};
const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid) return;
    previewVisible.value = false;
  });
};
const accept = computed(() => fileAccept?.value ?? sysConfig.upload.fileInputAccept);
const maxSize = computed(() => fileMaxSize?.value ?? sysConfig.upload.fileLimitByte);
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
const handlePick = (items: any) => {
  const list = Array.isArray(items) ? items : [items];
  list.forEach((item) => {
    if (!fileList.value.some((f: any) => f.url === item.url)) {
      fileList.value.push({ name: item.name, url: item.url, length: item.size });
    }
  });
};
</script>

<template>
  <div class="w-full">
    <el-upload
      :action="fileUploadUrl"
      :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
      :accept="accept"
      :before-upload="beforeUpload"
      :on-success="(res: any) => fileList.push({ name: res.name, url: res.url, length: res.size })"
      :on-progress="(event: any, file: any) => (progressFile = file)"
      :on-error="onError"
      :show-file-list="false"
      :disabled="disabled"
      multiple
      drag
    >
      <!-- 拖拽区内放两个明确按钮：上传（点击冒泡触发文件选择）、从资源库选择（.stop 阻止冒泡，改为打开选择器）；整框仍可拖拽 -->
      <div class="dropzone">
        <el-button type="primary" :icon="UploadFilled" :disabled="disabled">{{ $t('upload') }}</el-button>
        <el-button v-if="!disabled" :icon="FolderOpened" @click.stop="pickerVisible = true">{{ $t('resource.library') }}</el-button>
      </div>
    </el-upload>
    <el-progress v-if="progressFile.status === 'uploading'" :percentage="parseInt(progressFile.percentage, 10)"></el-progress>
    <file-picker v-model="pickerVisible" accept="file" multiple @pick="handlePick" />
    <transition-group tag="ul" :class="['el-upload-list', 'el-upload-list--text', { 'is-disabled': disabled }]" name="el-list">
      <li v-for="file in fileList" :key="file.url" class="el-upload-list__item is-success">
        <a class="el-upload-list__item-name" @click="() => handlePreview(file)">
          <el-icon class="el-icon--document"><Document /></el-icon>{{ file.name }}
        </a>
        <label class="el-upload-list__item-status-label">
          <el-icon class="el-icon--upload-success el-icon--circle-check"><CircleCheck /></el-icon>
        </label>
        <el-icon v-if="!disabled" class="el-icon--close" @click="() => fileList.splice(fileList.indexOf(file), 1)"><Close /></el-icon>
      </li>
    </transition-group>
    <el-dialog v-model="previewVisible" :title="$t('article.fileList.attribute')" top="5vh" :width="768" append-to-body>
      <el-form ref="form" :model="previewFile" label-width="150px">
        <el-form-item prop="name" :label="$t('name')" :rules="{ required: true, message: () => $t('v.required') }">
          <el-input v-model="previewFile.name" maxlength="100"></el-input>
        </el-form-item>
        <el-form-item prop="length" :label="$t('size')" :rules="{ required: true, message: () => $t('v.required') }">
          <el-input v-model="previewFile.length" maxlength="19">
            <template #append>Byte</template>
          </el-input>
        </el-form-item>
        <el-form-item prop="url" label="URL" :rules="{ required: true, message: () => $t('v.required') }">
          <el-input v-model="previewFile.url" maxlength="255"></el-input>
        </el-form-item>
        <el-button type="primary" native-type="submit" @click.prevent="() => handleSubmit()">{{ $t('save') }}</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-upload-dragger) {
  padding: 12px 20px;
  @apply bg-primary-lighter;
}
/* 拖拽上传区作为主操作铺满整行 */
.w-full :deep(.el-upload),
.w-full :deep(.el-upload-dragger) {
  width: 100%;
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

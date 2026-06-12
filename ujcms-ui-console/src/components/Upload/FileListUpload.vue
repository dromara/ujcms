<script setup lang="ts">
import { ref, toRefs, computed, watch } from 'vue';
import { useFormItem } from 'element-plus';
import { Close, Document, CircleCheck } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { handleError } from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { fileUploadUrl } from '@/api/config';

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
  <div class="w-full">
    <el-upload
      :action="fileUploadUrl"
      :headers="{ ...getAuthHeaders(), ...getSiteHeaders() }"
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
      <!--
      // 用于测试上传进度条
      action="https://jsonplaceholder.typicode.com/posts/"
      -->
      {{ $t('clickOrDragToUpload') }}
      <!-- <el-button type="primary">{{ $t('clickToUpload') }}</el-button> -->
    </el-upload>
    <el-progress v-if="progressFile.status === 'uploading'" :percentage="parseInt(progressFile.percentage, 10)"></el-progress>
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
  padding: 0 20px;
  @apply bg-primary-lighter text-primary;
}
</style>

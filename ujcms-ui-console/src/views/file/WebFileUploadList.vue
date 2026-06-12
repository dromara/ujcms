<script setup lang="ts">
import {
  uploadWebFileUploadUrl,
  uploadZipWebFileUploadUrl,
  queryWebFileUpload,
  downloadZipWebFileUpload,
  createWebFileUpload,
  mkdirWebFileUpload,
  updateWebFileUpload,
  renameWebFileUpload,
  copyWebFileUpload,
  moveWebFileUpload,
  deleteWebFileUpload,
  queryWebFileUploadList,
} from '@/api/file';
import WebFileList from './WebFileList.vue';

defineOptions({
  name: 'WebFileUploadList',
});
</script>

<template>
  <web-file-list
    type="Upload"
    :upload-web-file-url="uploadWebFileUploadUrl"
    :upload-zip-web-file-url="uploadZipWebFileUploadUrl"
    :query-web-file="queryWebFileUpload"
    :download-zip-web-file="downloadZipWebFileUpload"
    :create-web-file="createWebFileUpload"
    :mkdir-web-file="mkdirWebFileUpload"
    :update-web-file="updateWebFileUpload"
    :rename-web-file="renameWebFileUpload"
    :copy-web-file="copyWebFileUpload"
    :move-web-file="moveWebFileUpload"
    :delete-web-file="deleteWebFileUpload"
    :query-web-file-list="queryWebFileUploadList"
  />
</template>

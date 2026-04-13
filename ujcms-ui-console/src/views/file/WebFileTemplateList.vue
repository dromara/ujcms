<script setup lang="ts">
import {
  uploadWebFileTemplateUrl,
  uploadZipWebFileTemplateUrl,
  queryWebFileTemplate,
  downloadZipWebFileTemplate,
  createWebFileTemplate,
  mkdirWebFileTemplate,
  updateWebFileTemplate,
  renameWebFileTemplate,
  copyWebFileTemplate,
  moveWebFileTemplate,
  deleteWebFileTemplate,
  queryWebFileTemplateList,
} from '@/api/file';
import WebFileList from './WebFileList.vue';

defineOptions({
  name: 'WebFileTemplateList',
});
</script>

<template>
  <web-file-list
    type="Template"
    :upload-web-file-url="uploadWebFileTemplateUrl"
    :upload-zip-web-file-url="uploadZipWebFileTemplateUrl"
    :query-web-file="queryWebFileTemplate"
    :download-zip-web-file="downloadZipWebFileTemplate"
    :create-web-file="createWebFileTemplate"
    :mkdir-web-file="mkdirWebFileTemplate"
    :update-web-file="updateWebFileTemplate"
    :rename-web-file="renameWebFileTemplate"
    :move-web-file="moveWebFileTemplate"
    :copy-web-file="copyWebFileTemplate"
    :delete-web-file="deleteWebFileTemplate"
    :query-web-file-list="queryWebFileTemplateList"
  />
</template>

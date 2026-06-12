<script setup lang="ts">
import {
  uploadWebFileHtmlUrl,
  uploadZipWebFileHtmlUrl,
  queryWebFileHtml,
  downloadZipWebFileHtml,
  createWebFileHtml,
  mkdirWebFileHtml,
  updateWebFileHtml,
  renameWebFileHtml,
  copyWebFileHtml,
  moveWebFileHtml,
  deleteWebFileHtml,
  queryWebFileHtmlList,
} from '@/api/file';
import WebFileList from './WebFileList.vue';

defineOptions({
  name: 'WebFileHtmlList',
});
</script>

<template>
  <web-file-list
    type="Html"
    :upload-web-file-url="uploadWebFileHtmlUrl"
    :upload-zip-web-file-url="uploadZipWebFileHtmlUrl"
    :query-web-file="queryWebFileHtml"
    :download-zip-web-file="downloadZipWebFileHtml"
    :create-web-file="createWebFileHtml"
    :mkdir-web-file="mkdirWebFileHtml"
    :update-web-file="updateWebFileHtml"
    :rename-web-file="renameWebFileHtml"
    :copy-web-file="copyWebFileHtml"
    :move-web-file="moveWebFileHtml"
    :delete-web-file="deleteWebFileHtml"
    :query-web-file-list="queryWebFileHtmlList"
  />
</template>

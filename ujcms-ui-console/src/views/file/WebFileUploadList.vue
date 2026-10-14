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
import { currentUser } from '@/stores/useCurrentUser';
import { useRouteState, stringParam, flagParam } from '@/composables/useRouteState';
import WebFileList from './WebFileList.vue';

defineOptions({
  name: 'WebFileUploadList',
});
// 导航上下文（当前目录、全局数据）存于路由 query：刷新、页签往返后恢复。目录的合法性由 WebFileList 挂载时校验
const { dir, global: showGlobal } = useRouteState({
  dir: stringParam(),
  global: flagParam(() => currentUser.globalPermission),
});
</script>

<template>
  <web-file-list
    v-model:dir="dir"
    v-model:global="showGlobal"
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

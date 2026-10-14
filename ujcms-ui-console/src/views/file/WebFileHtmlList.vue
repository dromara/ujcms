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
import { currentUser } from '@/stores/useCurrentUser';
import { useRouteState, stringParam, flagParam } from '@/composables/useRouteState';
import WebFileList from './WebFileList.vue';

defineOptions({
  name: 'WebFileHtmlList',
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

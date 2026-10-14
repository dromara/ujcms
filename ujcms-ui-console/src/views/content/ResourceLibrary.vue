<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Upload, FolderOpened } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { managementUploadUrl } from '@/api/config';
import { importWebFileAttachment, existingAttachmentUrls } from '@/api/system';
import { useRouteState, stringParam, enumParam } from '@/composables/useRouteState';
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
import BaseUpload from '@/components/Upload/BaseUpload.vue';
import WebFileList from '../file/WebFileList.vue';
import AttachmentList from './AttachmentList.vue';
import ResourceCategoryTree from './ResourceCategoryTree.vue';

defineOptions({ name: 'ResourceLibrary' });

const { t } = useI18n();

// 导航上下文（页签、两个资源库各自选中的分类）存于路由 query：刷新、页签往返后恢复。
// 分类 ID 由 ResourceCategoryTree 在分类树加载后校验，已不存在则回落“全部”
const {
  tab: activeTab,
  categoryId: selectedCategoryId,
  // 我的资源库选中的个人分类；'0' 表示“未分类”筛选（上传时不带分类）
  myCategoryId,
} = useRouteState({
  tab: enumParam('all', ['all', 'shared', 'my']),
  categoryId: stringParam(),
  myCategoryId: stringParam(),
});

const allRef = ref<any>();
const sharedRef = ref<any>();
const myRef = ref<any>();

const uploadData = computed<Record<string, any>>(() => {
  if (activeTab.value === 'shared') {
    const data: Record<string, any> = { scope: 'shared' };
    if (selectedCategoryId.value) {
      data.categoryId = selectedCategoryId.value;
    }
    return data;
  }
  if (activeTab.value === 'my') {
    const data: Record<string, any> = { scope: 'my' };
    // 选中具体个人分类时上传归入该分类；'全部'(null) / '未分类'('0') 则上传为未分类收藏
    if (myCategoryId.value && myCategoryId.value !== '0') {
      data.categoryId = myCategoryId.value;
    }
    return data;
  }
  return { scope: 'all' };
});

const refreshActive = () => {
  if (activeTab.value === 'all') {
    allRef.value?.refresh();
  } else if (activeTab.value === 'shared') {
    sharedRef.value?.refresh();
  } else {
    myRef.value?.refresh();
  }
};

// 标签页之间内容会相互影响（收藏 / 加入共享），切换时刷新目标标签，避免显示旧数据
watch(activeTab, () => refreshActive());

const handleUploadSuccess = () => {
  refreshActive();
  ElMessage.success(t('success'));
};

// 从“上传管理”服务器文件登记为附件；按当前标签页 scope 决定是否同时加入共享/我的（复用上传的 uploadData）
const canImportServerFile = computed(() => !perm('webFileUpload:list') && !perm('resource:import'));
const serverFileVisible = ref<boolean>(false);
const serverFileListRef = ref<any>();
const importedAny = ref<boolean>(false);
// 过滤掉已登记为附件的服务器文件（返回需排除的 url 列表）
const filterImportedFiles = async (rows: any[]): Promise<string[]> => {
  const urls = rows.filter((r) => r.fileType !== 'DIRECTORY' && r.url).map((r) => r.url);
  if (urls.length === 0) {
    return [];
  }
  try {
    return await existingAttachmentUrls(urls);
  } catch {
    // 查询失败时不排除任何文件（后端导入仍按 url 去重，不会产生重复）
    return [];
  }
};
const handleServerFilePick = async (row: any) => {
  await importWebFileAttachment({ id: row.id, ...uploadData.value });
  importedAny.value = true;
  ElMessage.success(t('success'));
  // 乐观隐藏刚登记的文件，立即从列表移除（无需重新加载）
  serverFileListRef.value?.excludeUrls([row.url]);
};
const onServerFileClosed = () => {
  if (importedAny.value) {
    refreshActive();
    importedAny.value = false;
  }
};
</script>

<template>
  <div>
    <div class="flex items-center mb-3">
      <el-tabs v-model="activeTab" class="resource-tabs">
        <el-tab-pane :label="$t('resource.scope.all')" name="all" />
        <el-tab-pane :label="$t('resource.scope.shared')" name="shared" />
        <el-tab-pane :label="$t('resource.scope.my')" name="my" />
      </el-tabs>
      <div class="ml-auto flex items-center gap-2">
        <base-upload
          type="any"
          :upload-action="managementUploadUrl"
          :data="uploadData"
          :on-success="handleUploadSuccess"
          :disabled="perm('attachment:create')"
          multiple
        >
          <el-icon class="text-sm align-middle"><Upload /></el-icon> <span class="leading-7">{{ $t('resource.upload') }}</span>
        </base-upload>
        <el-button v-if="canImportServerFile" :icon="FolderOpened" @click="serverFileVisible = true">{{ $t('resource.serverFile') }}</el-button>
      </div>
    </div>

    <div v-show="activeTab === 'all'">
      <attachment-list ref="allRef" scope="all" />
    </div>
    <div v-show="activeTab === 'shared'" class="shared-layout">
      <resource-category-tree :model-value="selectedCategoryId ?? null" @update:model-value="(id) => (selectedCategoryId = id ?? undefined)" />
      <div class="shared-main">
        <attachment-list ref="sharedRef" scope="shared" :category-id="selectedCategoryId" />
      </div>
    </div>
    <div v-show="activeTab === 'my'" class="shared-layout">
      <resource-category-tree :model-value="myCategoryId ?? null" scope="my" @update:model-value="(id) => (myCategoryId = id ?? undefined)" />
      <div class="shared-main">
        <attachment-list ref="myRef" scope="my" :category-id="myCategoryId" />
      </div>
    </div>

    <el-dialog
      v-model="serverFileVisible"
      :title="$t('resource.serverFile')"
      width="72%"
      top="6vh"
      append-to-body
      destroy-on-close
      @closed="onServerFileClosed"
    >
      <div class="mb-2 text-xs text-gray-400">{{ $t('resource.serverFileImportHint') }}</div>
      <web-file-list
        ref="serverFileListRef"
        type="Upload"
        pick
        :pick-filter="filterImportedFiles"
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
        @pick="handleServerFilePick"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.resource-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.shared-layout {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.shared-main {
  flex: 1;
  min-width: 0;
}
</style>

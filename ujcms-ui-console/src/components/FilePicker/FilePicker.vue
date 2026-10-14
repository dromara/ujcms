<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { isAcceptedExtension, acceptToText } from '@/utils/upload';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { managementUploadUrl } from '@/api/config';
import { importWebFileAttachment } from '@/api/system';
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
import AttachmentList from '@/views/content/AttachmentList.vue';
import ResourceCategoryTree from '@/views/content/ResourceCategoryTree.vue';
import WebFileList from '@/views/file/WebFileList.vue';

defineOptions({ name: 'FilePicker' });

// 'media' = 视频+音频合并过滤组（供编辑器媒体对话框使用），后端 resolveExtensions 识别
type Accept = 'image' | 'video' | 'audio' | 'media' | 'doc' | 'file' | 'any';
export interface PickItem {
  id?: string;
  url: string;
  name?: string;
  size?: number | string;
}

// zIndex：可选层级。富文本编辑器（TinyMCE/Cherry）的对话框层级固定为 1300，高于本应用
// 设置的 Element Plus 基准层级（el-config-provider z-index=500），从编辑器内唤起时必须显式抬高，
// 否则选择器会被编辑器自身的弹窗盖住。普通表单场景不传，沿用自适应层级以免盖住其内部的提示/确认框。
const props = withDefaults(defineProps<{ modelValue: boolean; accept?: Accept; multiple?: boolean; zIndex?: number }>(), {
  accept: 'any',
  multiple: false,
});
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; pick: [items: PickItem | PickItem[]] }>();
const { t } = useI18n();
const sysConfig = useSysConfigStore();

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

type Tab = 'library' | 'upload' | 'webfile';
type Source = 'my' | 'shared' | 'all';
// 服务器文件标签页：需要列出上传目录（webFileUpload:list）且能登记为附件（resource:import）
const canPickWebFile = computed(() => !perm('webFileUpload:list') && !perm('resource:import'));
const canUpload = computed(() => !perm('attachment:create'));

// 记住上次使用的标签页 / 范围 / “上传后加入”勾选（仅本浏览器），减少每次插入文件时的重复操作
const PREF_KEY = 'ujcms.filePicker';
const readPrefs = (): Record<string, any> => {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY) ?? '{}') ?? {};
  } catch {
    return {};
  }
};
const prefs = readPrefs();
const initialTab = (): Tab => {
  if (prefs.tab === 'upload' && canUpload.value) return 'upload';
  if (prefs.tab === 'webfile' && canPickWebFile.value) return 'webfile';
  return 'library';
};
const tab = ref<Tab>(initialTab());
const source = ref<Source>(['my', 'shared', 'all'].includes(prefs.source) ? prefs.source : 'my');
const selection = ref<any[]>([]);
// 共享/我的范围下的分类导航；切换范围时回到“全部”
const categoryId = ref<string | null>(null);
watch(source, () => (categoryId.value = null));

const sourceOptions = computed(() => [
  { label: t('resource.scope.my'), value: 'my' },
  { label: t('resource.scope.shared'), value: 'shared' },
  { label: t('resource.scope.all'), value: 'all' },
]);

const gridAccept = computed(() => (props.accept === 'any' ? '' : props.accept));

// 上传标签页：限制可上传的文件类型与调用方一致（accept='any' 不限制）。
// 'media' = 视频+音频两类 inputAccept 合并；后端 management-upload 仍按扩展名再校验。
const uploadAccept = computed<string | undefined>(() => {
  switch (props.accept) {
    case 'image':
      return sysConfig.upload.imageInputAccept;
    case 'video':
      return sysConfig.upload.videoInputAccept;
    case 'audio':
      return sysConfig.upload.audioInputAccept;
    case 'media':
      return [sysConfig.upload.videoInputAccept, sysConfig.upload.audioInputAccept].filter(Boolean).join(',');
    case 'doc':
      return sysConfig.upload.docInputAccept;
    case 'file':
      return sysConfig.upload.fileInputAccept;
    default:
      return undefined;
  }
});
// accept 属性不拦截拖拽，需在 before-upload 手动校验扩展名
const beforeUpload = (file: File): boolean => {
  const acc = uploadAccept.value;
  if (acc && !isAcceptedExtension(file.name, acc)) {
    ElMessage.error(t('error.fileType', { types: acceptToText(acc) }));
    return false;
  }
  return true;
};

// 上传后加入哪些库（可同时加入我的与共享；都不勾选则仅登记为附件）。
// 默认都不勾选：资源库是“精选子集”，从编辑器随手上传的文件不应自动进入我的资源库（否则收藏夹会被上传记录淹没）；
// 勾选状态会被记住，需要时勾一次即可。
const addToMy = ref<boolean>(prefs.addToMy === true);
const addToShared = ref<boolean>(prefs.addToShared === true);
watch([tab, source, addToMy, addToShared], () => {
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify({ tab: tab.value, source: source.value, addToMy: addToMy.value, addToShared: addToShared.value }));
  } catch {
    /* 隐私模式等无法写入时忽略 */
  }
});
const uploadData = computed<Record<string, any>>(() => {
  const data: Record<string, any> = {};
  if (addToMy.value) data.addToMy = true;
  if (addToShared.value) data.addToShared = true;
  return data;
});

const toPick = (row: any): PickItem => ({ id: row.id, url: row.url, name: row.name, size: row.length ?? row.size });

const close = () => emit('update:modelValue', false);

const handleGridPick = (row: any) => {
  emit('pick', toPick(row));
  close();
};
const handleSelectionChange = (rows: any[]) => {
  selection.value = rows;
};
const confirmMultiple = () => {
  emit('pick', selection.value.map(toPick));
  close();
};
const handleUploadSuccess = (response: any) => {
  emit('pick', { url: response.url, name: response.name, size: response.size });
  if (!props.multiple) {
    close();
  }
};
// 服务器文件：选中后经 import-web-file 桥接登记为附件（无字节拷贝，已存在则去重），再返回其 url
const handleWebFilePick = async (row: any) => {
  const attachment = await importWebFileAttachment({ id: row.id });
  emit('pick', { id: attachment.id, url: attachment.url, name: attachment.name, size: attachment.length });
  close();
};
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="$t('resource.selectFromLibrary')"
    width="72%"
    top="6vh"
    append-to-body
    destroy-on-close
    :z-index="zIndex"
    class="file-picker-dialog"
  >
    <el-tabs v-model="tab">
      <el-tab-pane :label="$t('menu.content.resourceLibrary')" name="library">
        <el-segmented v-model="source" :options="sourceOptions" size="default" class="mb-3" />
        <div class="picker-library">
          <!-- 共享/我的：带分类树导航（只读），与资源库管理页一致；全部附件无分类 -->
          <resource-category-tree v-if="source !== 'all'" :key="source" v-model="categoryId" :scope="source" readonly class="picker-tree" />
          <div class="picker-main">
            <attachment-list
              :scope="source"
              :category-id="source === 'all' ? null : categoryId"
              :accept="gridAccept"
              :z-index="zIndex"
              pick
              :multiple="multiple"
              @pick="handleGridPick"
              @selection-change="handleSelectionChange"
            />
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane v-if="canUpload" :label="$t('resource.upload')" name="upload">
        <div class="mb-3 space-x-4">
          <el-checkbox v-model="addToMy">{{ $t('resource.uploadToMy') }}</el-checkbox>
          <el-checkbox v-if="!perm('resource:share')" v-model="addToShared">{{ $t('resource.uploadToShared') }}</el-checkbox>
        </div>
        <el-upload
          :action="managementUploadUrl"
          :headers="{ ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() }"
          :data="uploadData"
          :accept="uploadAccept"
          :before-upload="beforeUpload"
          :on-success="handleUploadSuccess"
          :show-file-list="false"
          :multiple="multiple"
          drag
        >
          <div class="upload-hint">{{ $t('clickOrDragToUpload') }}</div>
        </el-upload>
      </el-tab-pane>
      <el-tab-pane v-if="canPickWebFile" :label="$t('resource.serverFile')" name="webfile">
        <web-file-list
          type="Upload"
          pick
          :accept="uploadAccept"
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
          @pick="handleWebFilePick"
        />
      </el-tab-pane>
    </el-tabs>
    <template v-if="multiple" #footer>
      <el-button @click="close">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :disabled="selection.length === 0" @click="confirmMultiple">
        {{ $t('ok') }}{{ selection.length ? ` (${selection.length})` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.file-picker-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
.picker-library {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
/* 分类树本身是无边框白卡片（内容区灰底上）；对话框是白底，须加边框才看得出面板 */
.picker-tree {
  width: 200px;
  border: 1px solid var(--el-border-color-lighter);
}
.picker-main {
  flex: 1;
  min-width: 0;
}
.upload-hint {
  padding: 16px;
  color: var(--el-text-color-secondary);
}
</style>

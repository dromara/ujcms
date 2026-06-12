<script setup lang="ts">
import { computed, onMounted, ref, toRefs, PropType } from 'vue';
import { Delete, Folder, FolderAdd, Document, DocumentAdd, DocumentCopy, Picture, Collection, Files, Switch, Download } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { saveAs } from 'file-saver';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { queryCurrentSite } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import BaseUpload from '@/components/Upload/BaseUpload.vue';
import WebFileForm from './WebFileForm.vue';
import WebFileBatch from './WebFileBatch.vue';

defineOptions({
  name: 'WebFileList',
});
const props = defineProps({
  type: { type: String, required: true },
  uploadWebFileUrl: { type: String, reuqired: true, default: null },
  uploadZipWebFileUrl: { type: String, reuqired: true, default: null },
  queryWebFile: { type: Function as PropType<(id: string) => Promise<any>>, required: true },
  downloadZipWebFile: { type: Function as PropType<(dir: string, names: string[]) => Promise<any>>, required: true },
  createWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  mkdirWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  updateWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  renameWebFile: { type: Function as PropType<(data: Record<string, any>) => Promise<any>>, required: true },
  moveWebFile: { type: Function as PropType<(dir: string, names: string[], destDir: string) => Promise<any>>, required: true },
  copyWebFile: { type: Function as PropType<(dir: string, names: string[], destDir: string) => Promise<any>>, required: true },
  deleteWebFile: { type: Function as PropType<(data: string[]) => Promise<any>>, required: true },
  queryWebFileList: { type: Function as PropType<(params?: Record<string, any>) => Promise<any>>, required: true },
});
const { type } = toRefs(props);

const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const downloadLoading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const filtered = ref<boolean>(false);
const baseId = ref<string>('');
const parentId = ref<string>('/');
const parents = computed(() => parentId.value.split('/').filter((it: string) => it !== ''));
const showGlobalData = ref<boolean>(false);
const site = ref<any>({});

const batchFormVisible = ref<boolean>(false);
const batchType = ref<'copy' | 'move'>('copy');
const batchIds = ref<string[]>([]);
const batchNames = ref<string[]>([]);

const fetchData = async (showLoading?: boolean) => {
  if (showLoading == null || showLoading) {
    loading.value = true;
  }
  try {
    data.value = await props.queryWebFileList({ ...toParams(params.value), parentId: parentId.value, sort: sort.value });
    filtered.value = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0 || sort.value !== undefined;
  } finally {
    if (showLoading == null || showLoading) {
      loading.value = false;
    }
  }
};

const handleShowGlobalChange = () => {
  if (!showGlobalData.value) {
    baseId.value = type.value === 'Html' ? site.value.staticBase : `/${site.value.id}`;
    if (baseId.value === '') {
      baseId.value = '/';
    }
  } else {
    baseId.value = '/';
  }
  parentId.value = baseId.value;
  fetchData();
};
onMounted(async () => {
  site.value = await queryCurrentSite();
  handleShowGlobalChange();
});

const handleSort = ({ column, prop, order }: { column: any; prop: string; order: string }) => {
  if (prop && order) {
    sort.value = (column.sortBy ?? prop) + (order === 'descending' ? '_desc' : '');
  } else {
    sort.value = undefined;
  }
  fetchData();
};
const handleSearch = () => fetchData();
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  fetchData();
};
const changeParentId = (id: string) => {
  parentId.value = id;
  params.value = {};
  fetchData();
};
const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string, fileType: string) => {
  if (fileType === 'DIRECTORY') {
    changeParentId(id);
    return;
  }
  beanId.value = id;
  formVisible.value = true;
};
const handleDownloadZip = async (names: string[]) => {
  downloadLoading.value = true;
  try {
    const blob = await props.downloadZipWebFile(parentId.value, names);
    saveAs(blob, 'download.zip');
  } finally {
    downloadLoading.value = false;
  }
};
// const handleView = (id: string, fileType: string, url: string) => {
//   window.open(url, '_blank');
// };
const handleBatchAction = (ids: string[], names: string[], type: 'copy' | 'move') => {
  batchIds.value = ids;
  batchNames.value = names;
  batchType.value = type;
  batchFormVisible.value = true;
};
const handleRename = async (id: string, name: string) => {
  try {
    const { value } = await ElMessageBox.prompt(t('webFile.newName'), t('webFile.op.rename'), {
      confirmButtonText: t('submit'),
      cancelButtonText: t('cancel'),
      inputValue: name,
      inputPattern: /\S+/,
      inputErrorMessage: t('v.required'),
    });
    await props.renameWebFile({ id, name: value });
    fetchData();
    ElMessage.success(t('success'));
  } catch {
    /* empty */
  }
};
const handleMkdir = async () => {
  try {
    const { value } = await ElMessageBox.prompt(t('webFile.name'), t('webFile.op.mkdir'), {
      confirmButtonText: t('submit'),
      cancelButtonText: t('cancel'),
      inputPattern: /\S+/,
      inputErrorMessage: t('v.required'),
    });
    await props.mkdirWebFile({ parentId: parentId.value, name: value });
    fetchData();
    ElMessage.success(t('success'));
  } catch {
    /* empty */
  }
};
const handleDelete = async (ids: string[]) => {
  await props.deleteWebFile(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const uploadSuccess = async () => {
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('webFile.name')" name="name"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button-group>
        <el-button type="primary" :disabled="perm(`webFile${type}:create`)" :icon="DocumentAdd" @click="() => handleAdd()">{{ $t('webFile.op.mkfile') }}</el-button>
        <el-button :disabled="perm(`webFile${type}:mkdir`)" :icon="FolderAdd" @click="() => handleMkdir()">{{ $t('webFile.op.mkdir') }}</el-button>
      </el-button-group>
      <el-button-group>
        <el-button
          :disabled="perm(`webFile${type}:downloadZip`) || selection.length <= 0"
          :icon="Download"
          :loading="downloadLoading"
          @click="() => handleDownloadZip(selection.map((row) => row.origName))"
        >
          {{ $t('webFile.op.downloadZip') }}
        </el-button>
        <el-button
          :disabled="perm(`webFile${type}:copy`) || selection.length <= 0"
          :icon="DocumentCopy"
          @click="
            () =>
              handleBatchAction(
                selection.map((row) => row.id),
                selection.map((row) => row.origName),
                'copy',
              )
          "
        >
          {{ $t('webFile.op.copy') }}
        </el-button>
        <el-button
          :disabled="perm(`webFile${type}:move`) || selection.length <= 0"
          :icon="Switch"
          @click="
            () =>
              handleBatchAction(
                selection.map((row) => row.id),
                selection.map((row) => row.origName),
                'move',
              )
          "
        >
          {{ $t('webFile.op.move') }}
        </el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm(`webFile${type}:delete`)" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
      </el-button-group>
      <el-button-group class="mt-1.5">
        <base-upload
          :disabled="perm(`webFile${type}:upload`)"
          type="any"
          :data="{ parentId }"
          :upload-action="uploadWebFileUrl"
          :on-success="uploadSuccess"
          multiple
          class="inline-block"
        >
          <el-icon class="text-sm align-middle"><Files /></el-icon> <span class="leading-7">{{ $t('webFile.op.upload') }}</span>
        </base-upload>
        <base-upload
          :disabled="perm(`webFile${type}:uploadZip`)"
          type="file"
          :data="{ parentId }"
          file-accept=".zip,.7z"
          :upload-action="uploadZipWebFileUrl"
          :on-success="uploadSuccess"
          class="inline-block ml-1"
        >
          <el-icon class="text-sm align-middle"><Collection /></el-icon> <span class="leading-7">{{ $t('webFile.op.uploadZip') }}</span>
        </base-upload>
      </el-button-group>
      <column-setting :name="`webFile${type}`" />
    </div>
    <div class="mt-2">
      <el-checkbox
        v-if="currentUser.globalPermission"
        v-model="showGlobalData"
        class="mr-3 align-middle"
        :label="$t('globalData')"
        :border="true"
        @change="() => handleShowGlobalChange()"
      />
      <el-button type="primary" size="small" plain :disabled="!showGlobalData && baseId !== '/'" @click="() => changeParentId('/')">/</el-button>
      <el-button
        v-for="(item, index) in parents"
        :key="item"
        type="primary"
        size="small"
        plain
        :disabled="index < (baseId.match(/\//g)?.length ?? 0) - 1"
        @click="() => changeParentId(`/${parents.slice(0, index + 1).join('/')}`)"
        >{{ item }}</el-button
      >
    </div>
    <div class="mt-3 app-block">
      <el-table
        ref="table"
        v-loading="loading"
        :data="data"
        @selection-change="(rows) => (selection = rows)"
        @row-dblclick="(row) => handleEdit(row.id, row.fileType)"
        @sort-change="handleSort"
      >
        <column-list :name="`webFile${type}`">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="name" :label="$t('webFile.name')" sortable="custom" show-overflow-tooltip min-width="360">
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="() => handleEdit(row.id, row.fileType)">
                <el-icon v-if="row.fileType === 'DIRECTORY'" class="mr-1"><Folder class="text-warning" /></el-icon>
                <el-icon v-else-if="row.fileType === 'ZIP'" class="mr-1"><Collection /></el-icon>
                <el-icon v-else-if="row.fileType === 'TEXT'" class="mr-1"><Document /></el-icon>
                <el-icon v-else-if="row.fileType === 'IMAGE'" class="mr-1"><Picture /></el-icon>
                <span>{{ row.name }}</span>
              </el-link>
            </template>
          </el-table-column>
          <el-table-column
            property="lastModified"
            :label="$t('webFile.lastModified')"
            sortable="custom"
            show-overflow-tooltip
            :formatter="(row) => (row.lastModified != null ? dayjs(row.lastModified).format('YYYY-MM-DD HH:mm') : '')"
            min-width="150"
          ></el-table-column>
          <el-table-column property="fileType" :label="$t('webFile.fileType')" sortable="custom" :formatter="(row) => $t(`webFile.fileType.${row.fileType}`)" min-width="100" />
          <el-table-column property="length" :label="$t('webFile.size')" sortable="custom" :formatter="(row) => (row.directory ? '' : row.size)" min-width="100" />
          <el-table-column :label="$t('table.action')" width="200">
            <template #default="{ row }">
              <!--
              <el-button type="primary" :disabled="row.directory" size="small" link @click="() => handleView(row.id, row.fileType, row.url)">
                {{ $t('webFile.op.view') }}
              </el-button>
               -->
              <el-button type="primary" :disabled="perm(`webFile${type}:update`)" size="small" link @click="() => handleRename(row.id, row.name)">
                {{ $t('webFile.op.rename') }}
              </el-button>
              <el-button type="primary" :disabled="perm(`webFile${type}:copy`)" size="small" link @click="() => handleBatchAction([row.id], [row.origName], 'copy')">
                {{ $t('webFile.op.copy') }}
              </el-button>
              <el-button type="primary" :disabled="perm(`webFile${type}:move`)" size="small" link @click="() => handleBatchAction([row.id], [row.origName], 'move')">
                {{ $t('webFile.op.move') }}
              </el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm(`webFile${type}:delete`)" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <web-file-form
      v-model="formVisible"
      :bean-id="beanId"
      :bean-ids="beanIds"
      :parent-id="parentId"
      :type="type"
      :query-web-file="queryWebFile"
      :create-web-file="createWebFile"
      :update-web-file="updateWebFile"
      :delete-web-file="deleteWebFile"
      @finished="() => fetchData(!formVisible)"
    />
    <web-file-batch
      v-model="batchFormVisible"
      :type="batchType"
      :batch-ids="batchIds"
      :batch-names="batchNames"
      :current-parent-id="parentId"
      :base-id="baseId"
      :move-web-file="moveWebFile"
      :copy-web-file="copyWebFile"
      :query-web-file-list="queryWebFileList"
      @finished="() => fetchData(!batchFormVisible)"
    />
  </div>
</template>

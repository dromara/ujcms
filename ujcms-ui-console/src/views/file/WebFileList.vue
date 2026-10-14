<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, toRefs, watch, type PropType } from 'vue';
import { Delete, Folder, FolderAdd, Document, DocumentAdd, DocumentCopy, Picture, Collection, Files, Switch, Download, Clock } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { saveAs } from 'file-saver';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { isAcceptedExtension } from '@/utils/upload';
import { queryCurrentSite } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import BaseUpload from '@/components/Upload/BaseUpload.vue';
import WebFileBatch from './WebFileBatch.vue';

// 文件编辑对话框与历史抽屉依赖 CodeMirror 6（约 600 kB）；本组件被 FilePicker 静态引入，进而进入所有带上传控件的表单 chunk。
// 这里异步加载，使 CodeMirror 只随文件管理/模板管理路由下载。对话框本身仍常驻挂载，行为不变。
const WebFileForm = defineAsyncComponent(() => import('./WebFileForm.vue'));
const WebFileHistoryDrawer = defineAsyncComponent(() => import('./WebFileHistoryDrawer.vue'));

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
  // 选择模式：隐藏管理操作，点击文件触发 pick（用于 FilePicker 的“服务器文件”标签页）
  pick: { type: Boolean, default: false },
  // 选择模式下按扩展名（inputAccept 串，如 ".jpg,.png"）过滤可选文件；为空不限制。目录始终显示以便导航。
  accept: { type: String, default: '' },
  // 选择模式下排除部分文件（返回需排除的 url 列表，如已登记为附件的文件）。目录始终显示。
  pickFilter: { type: Function as PropType<(rows: any[]) => Promise<string[]>>, default: null },
  // 是否提供模板编辑历史（仅模板文件管理器传 true——历史只对模板存储捕获）
  history: { type: Boolean, default: false },
  // 编辑对话框标题名称（透传 WebFileForm；模板管理器迁入设计模块后菜单词条不再是 menu.file.webFile{type}）
  formName: { type: String, default: null },
  // 浏览根（受控模式，template-manager P2）：外壳传入时以此为根（如 设计›模板 的 /{siteId}/{theme}），
  // 隐藏内部「全局数据」姿态与根之上的路径段；缺省 null 时按 站点+类型 内部计算（上传/HTML 文件不变）
  baseId: { type: String, default: null },
  // 受控模式下面包屑根按钮的显示名（如主题名）；缺省显示 "/"
  breadcrumbRoot: { type: String, default: null },
  // 文件打开拦截（template-manager P3）：外壳接管文件打开（如 设计›模板 的编辑工作台）。
  // 返回 true 表示已处理；返回 false 或缺省 null 走内置编辑对话框（上传/HTML 文件不变）
  openFile: { type: Function as PropType<(row: any) => boolean>, default: null },
  // 导航上下文的初始值（仅非受控模式、挂载时读取一次）：路由外壳（上传文件/HTML 文件）把当前目录与「全局数据」
  // 存于路由 query（useRouteState），经 v-model:dir / v-model:global 传入并接收变化；本组件不直接接触路由
  // （它同时用于对话框与受控外壳）。dir 为完整路径（如 /1/image/2026），缺省 null 即浏览根
  dir: { type: String, default: null },
  global: { type: Boolean, default: false },
});
const emit = defineEmits<{ pick: [row: any]; 'update:dir': [value: string | undefined]; 'update:global': [value: boolean] }>();
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
// 选择模式下排除的文件 url（如已登记为附件的文件），随每次加载刷新
const excludedUrls = ref<Set<string>>(new Set());
// 选择模式下按 accept 过滤展示：目录始终保留（用于进入子目录），文件按扩展名匹配并排除 excludedUrls
const displayData = computed(() => {
  let rows = data.value;
  if (props.accept) {
    rows = rows.filter((row) => row.fileType === 'DIRECTORY' || isAcceptedExtension(row.name, props.accept));
  }
  if (excludedUrls.value.size > 0) {
    rows = rows.filter((row) => row.fileType === 'DIRECTORY' || !excludedUrls.value.has(row.url));
  }
  return rows;
});
const filtered = ref<boolean>(false);
const internalBaseId = ref<string>('');
// 受控模式：外壳传入 baseId 即接管浏览根；否则用内部按 站点+类型 计算的根
const controlled = computed<boolean>(() => props.baseId != null);
const effectiveBaseId = computed<string>(() => props.baseId ?? internalBaseId.value);
const parentId = ref<string>('/');
const parents = computed(() => parentId.value.split('/').filter((it: string) => it !== ''));
// 受控模式下面包屑隐藏根以内的路径段（操作员不接触站点 ID 等根前缀）
const rootDepth = computed<number>(() => (controlled.value && effectiveBaseId.value !== '/' ? effectiveBaseId.value.split('/').filter((it) => it !== '').length : 0));
const showGlobalData = ref<boolean>(false);
const site = ref<any>({});

const batchFormVisible = ref<boolean>(false);
const batchType = ref<'copy' | 'move'>('copy');
const batchIds = ref<string[]>([]);
const batchNames = ref<string[]>([]);

// 目录历史抽屉（template-history）：当前文件夹子树的最近历史——已删除文件的发现面，恢复即重建
const historyVisible = ref<boolean>(false);

const fetchData = async (showLoading?: boolean) => {
  if (showLoading == null || showLoading) {
    loading.value = true;
  }
  try {
    const rows = await props.queryWebFileList({ ...toParams(params.value), parentId: parentId.value, sort: sort.value });
    // 先算出需排除的文件，再赋值 data，避免已登记文件“先显示后消失”的闪烁
    excludedUrls.value = props.pick && props.pickFilter ? new Set(await props.pickFilter(rows)) : new Set();
    data.value = rows;
    filtered.value = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0 || sort.value !== undefined;
  } finally {
    if (showLoading == null || showLoading) {
      loading.value = false;
    }
  }
};
defineExpose({
  refresh: () => fetchData(false),
  // 选择后即时隐藏对应文件（乐观更新），无需重新加载
  excludeUrls: (urls: string[]) => {
    excludedUrls.value = new Set([...excludedUrls.value, ...urls]);
  },
});

const computeBase = () => {
  if (!showGlobalData.value) {
    internalBaseId.value = type.value === 'Html' ? site.value.staticBase : `/${site.value.id}`;
    if (internalBaseId.value === '') {
      internalBaseId.value = '/';
    }
  } else {
    internalBaseId.value = '/';
  }
};
const handleShowGlobalChange = () => {
  computeBase();
  parentId.value = internalBaseId.value;
  fetchData();
};
// 初始目录须位于浏览根之内（与后端的路径校验同口径）：地址可能来自其它站点（切换站点后整页重载而地址不变）或被手改
const isWithin = (dir: string, base: string): boolean => !/\.\.|\/\/|\\|~/.test(dir) && (base === '/' ? dir.startsWith('/') : dir === base || dir.startsWith(`${base}/`));
onMounted(async () => {
  if (props.baseId != null) {
    parentId.value = props.baseId;
    fetchData();
    return;
  }
  site.value = await queryCurrentSite();
  showGlobalData.value = props.global && currentUser.globalPermission && !props.pick;
  computeBase();
  parentId.value = props.dir != null && isWithin(props.dir, internalBaseId.value) ? props.dir : internalBaseId.value;
  // 初始值经校验后回报一次：解析结果与 parentId 的初值相同时，下面的侦听不会触发
  emit('update:dir', parentId.value === internalBaseId.value ? undefined : parentId.value);
  emit('update:global', showGlobalData.value);
  fetchData();
});
// 非受控模式下向外壳回报导航上下文；位于浏览根时回报 undefined（外壳据此省略地址中的 dir）
watch(parentId, (value) => {
  if (!controlled.value) {
    emit('update:dir', value === effectiveBaseId.value ? undefined : value);
  }
});
watch(showGlobalData, (value) => {
  if (!controlled.value) {
    emit('update:global', value);
  }
});
// 受控模式下外壳切换浏览根（如换主题）：重置查询条件并回到新根
watch(
  () => props.baseId,
  (value) => {
    if (value != null) {
      resetParams(params.value);
      sort.value = undefined;
      parentId.value = value;
      fetchData();
    }
  },
);

const handleSort = ({ column, prop, order }: { column: any; prop: string | null; order: string | null }) => {
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
const handleOpen = (row: any) => {
  if (row.fileType === 'DIRECTORY') {
    changeParentId(row.id);
    return;
  }
  if (props.pick) {
    emit('pick', row);
    return;
  }
  if (props.openFile != null && props.openFile(row)) {
    return;
  }
  beanId.value = row.id;
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
    <div v-if="!pick" class="space-x-2">
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
      <el-button v-if="history" :disabled="perm(`webFile${type}:show`)" :icon="Clock" @click="() => (historyVisible = true)">
        {{ $t('webFile.op.history') }}
      </el-button>
      <column-setting :name="`webFile${type}`" />
    </div>
    <div class="mt-2">
      <!-- 选择模式（导入附件）下隐藏“全局数据”：导入受站点隔离限制，只能登记当前站点的文件；
           受控模式（外壳传 baseId）下浏览姿态由外壳负责（设计›模板 的 原始目录 开关） -->
      <el-checkbox
        v-if="currentUser.globalPermission && !pick && !controlled"
        v-model="showGlobalData"
        class="mr-3 align-middle"
        :label="$t('globalData')"
        :border="true"
        @change="() => handleShowGlobalChange()"
      />
      <template v-if="controlled">
        <!-- 受控模式：根按钮即浏览根（根以内路径段不展示） -->
        <el-button type="primary" size="small" plain @click="() => changeParentId(effectiveBaseId)">{{ breadcrumbRoot ?? '/' }}</el-button>
        <el-button
          v-for="(item, index) in parents.slice(rootDepth)"
          :key="item"
          type="primary"
          size="small"
          plain
          @click="() => changeParentId(`/${parents.slice(0, rootDepth + index + 1).join('/')}`)"
          >{{ item }}</el-button
        >
      </template>
      <template v-else>
        <el-button type="primary" size="small" plain :disabled="!showGlobalData && effectiveBaseId !== '/'" @click="() => changeParentId('/')">/</el-button>
        <el-button
          v-for="(item, index) in parents"
          :key="item"
          type="primary"
          size="small"
          plain
          :disabled="index < (effectiveBaseId.match(/\//g)?.length ?? 0) - 1"
          @click="() => changeParentId(`/${parents.slice(0, index + 1).join('/')}`)"
          >{{ item }}</el-button
        >
      </template>
    </div>
    <div class="mt-3 app-block">
      <el-table
        ref="table"
        v-loading="loading"
        :data="displayData"
        @selection-change="(rows) => (selection = rows)"
        @row-dblclick="(row) => handleOpen(row)"
        @sort-change="handleSort"
      >
        <column-list :name="`webFile${type}`">
          <el-table-column v-if="!pick" type="selection" width="38"></el-table-column>
          <el-table-column property="name" :label="$t('webFile.name')" sortable="custom" show-overflow-tooltip min-width="360">
            <template #default="{ row }">
              <el-link type="primary" underline="never" @click="() => handleOpen(row)">
                <el-icon v-if="row.fileType === 'DIRECTORY'" class="mr-1"><Folder class="text-warning" /></el-icon>
                <el-icon v-else-if="row.fileType === 'ZIP'" class="mr-1"><Collection /></el-icon>
                <el-icon v-else-if="row.fileType === 'TEXT'" class="mr-1"><Document /></el-icon>
                <el-icon v-else-if="row.fileType === 'IMAGE'" class="mr-1"><Picture /></el-icon>
                <span>{{ row.name }}</span>
              </el-link>
              <!-- 名称后缀插槽：外壳按行补充标注（如 设计›模板 结构化目录的角色标签） -->
              <slot name="row-tag" :row="row"></slot>
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
          <el-table-column :label="$t('table.action')" width="240">
            <template #default="{ row }">
              <template v-if="pick">
                <el-button v-if="row.fileType !== 'DIRECTORY'" type="primary" size="small" link @click="() => handleOpen(row)">
                  {{ $t('select') }}
                </el-button>
              </template>
              <template v-else>
              <!--
              <el-button type="primary" :disabled="row.directory" size="small" link @click="() => handleView(row.id, row.fileType, row.url)">
                {{ $t('webFile.op.view') }}
              </el-button>
               -->
              <!-- 行级操作插槽：外壳注入类型专属动作（如 设计›模板 的 预览） -->
              <slot name="row-actions" :row="row"></slot>
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
      :form-name="formName"
      :query-web-file="queryWebFile"
      :create-web-file="createWebFile"
      :update-web-file="updateWebFile"
      :delete-web-file="deleteWebFile"
      :history="history"
      @finished="() => fetchData(!formVisible)"
    />
    <web-file-history-drawer v-if="history" v-model="historyVisible" :dir="parentId" @restored="() => fetchData(false)" />
    <web-file-batch
      v-model="batchFormVisible"
      :type="batchType"
      :batch-ids="batchIds"
      :batch-names="batchNames"
      :current-parent-id="parentId"
      :base-id="effectiveBaseId"
      :move-web-file="moveWebFile"
      :copy-web-file="copyWebFile"
      :query-web-file-list="queryWebFileList"
      @finished="() => fetchData(!batchFormVisible)"
    />
  </div>
</template>

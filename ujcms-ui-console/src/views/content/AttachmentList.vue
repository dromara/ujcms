<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Delete, Folder, FolderAdd, Grid, List, Search, Star, StarFilled, View } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { useViewModeStore, type ViewMode } from '@/stores/viewModeStore';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import {
  deleteAttachment,
  deleteUnusedAttachment,
  queryAttachment,
  queryAttachmentPage,
  unfavoriteAttachment,
  categorizeFavoriteAttachment,
  shareAttachment,
  unshareAttachment,
  queryResourceCategoryList,
} from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import AttachmentThumb from './AttachmentThumb.vue';
import AttachmentDetail from './AttachmentDetail.vue';

defineOptions({
  name: 'AttachmentList',
});

type Scope = 'all' | 'shared' | 'my';

const props = withDefaults(
  defineProps<{
    scope?: Scope;
    categoryId?: string | null;
    pick?: boolean;
    multiple?: boolean;
    accept?: string;
    // 从编辑器内的选择器打开时传入（详情/预览对话框需高于选择器层级）
    zIndex?: number;
  }>(),
  { scope: 'all', categoryId: null, pick: false, multiple: false, accept: '', zIndex: undefined },
);

const emit = defineEmits<{
  pick: [row: any];
  'selection-change': [rows: any[]];
}>();

const { t } = useI18n();
const VIEW_KEY = 'attachment';
const viewModeStore = useViewModeStore();
const viewMode = computed<ViewMode>({
  get: () => viewModeStore.get(VIEW_KEY, 'grid'),
  set: (mode) => viewModeStore.set(VIEW_KEY, mode),
});

const params = ref<any>({});
// 共享/我的范围的文件名关键字（全部附件范围沿用标准 QueryForm 的 Q_Contains_name）
const keyword = ref<string>('');
const typeFilter = ref<string>('all');
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const detailVisible = ref<boolean>(false);
const detailRow = ref<any>(null);
const gridSelection = ref<Set<string>>(new Set());

// 选择器（pick）无具体 accept 限制时，三种范围都提供类型筛选；独立管理页保持仅“全部附件”页有筛选。
const showTypeFilter = computed(() => !props.accept && (props.scope === 'all' || props.pick));
const showQuery = computed(() => props.scope === 'all');
// 选中项是否包含使用中（被引用/已加入资源库）的附件；包含则禁止删除（pin 状态不可删）
const selectionHasUsed = computed(() => selection.value.some((r: any) => r.used));
// 选中项中已收藏的附件 id：批量“取消收藏”只提交这些，未收藏的选中项不受影响
const selectionFavoritedIds = computed(() => selection.value.filter((r: any) => r.favorited).map((r: any) => r.id));

const fetchData = async () => {
  loading.value = true;
  try {
    const apiParams: Record<string, any> = {
      ...toParams(params.value),
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
      scope: props.scope,
    };
    // shared 按共享分类过滤；my 按个人分类过滤（'0' 表示未分类）
    if ((props.scope === 'shared' || props.scope === 'my') && props.categoryId) {
      apiParams.categoryId = props.categoryId;
    }
    if (props.scope !== 'all' && keyword.value.trim()) {
      apiParams.keyword = keyword.value.trim();
    }
    if (props.accept) {
      apiParams.type = props.accept;
    } else if (showTypeFilter.value && typeFilter.value && typeFilter.value !== 'all') {
      apiParams.type = typeFilter.value;
    }
    const {
      content,
      page: { totalElements },
    } = await queryAttachmentPage(apiParams);
    data.value = content;
    total.value = Number(totalElements);
    syncGridSelection();
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);
watch(
  () => [props.scope, props.categoryId, props.accept],
  () => {
    currentPage.value = 1;
    fetchData();
  },
);
const handleKeyword = () => {
  currentPage.value = 1;
  fetchData();
};

defineExpose({ refresh: fetchData });

const handleTypeChange = () => {
  currentPage.value = 1;
  fetchData();
};

const typeOptions = computed(() => [
  { label: t('all'), value: 'all' },
  { label: t('attachment.type.image'), value: 'image' },
  { label: t('attachment.type.video'), value: 'video' },
  { label: t('attachment.type.audio'), value: 'audio' },
  { label: t('attachment.type.doc'), value: 'doc' },
  { label: t('attachment.type.file'), value: 'file' },
]);

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
  table.value?.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  fetchData();
};

const openDetail = (row: any) => {
  detailRow.value = row;
  detailVisible.value = true;
};
const handleShowDetail = (row: any) => {
  if (props.pick) {
    handlePick(row);
    return;
  }
  openDetail(row);
};
const handleDelete = async (ids: string[]) => {
  await deleteAttachment(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleCleanUnused = async () => {
  try {
    await ElMessageBox.confirm(t('resource.cleanUnusedConfirm'), { type: 'warning' });
    const count = await deleteUnusedAttachment();
    fetchData();
    ElMessage.success(t('resource.cleanUnusedDone', { count }));
  } catch {
    /* cancelled */
  }
};

const handlePick = (row: any) => {
  if (props.multiple) {
    toggleGridSelect(row);
  } else {
    emit('pick', row);
  }
};

const handleFavorite = async (row: any) => {
  if (row.favorited) {
    await unfavoriteAttachment([row.id]);
    row.favorited = false;
    if (props.scope === 'my') {
      // 我的资源库：取消收藏后该项移出列表
      fetchData();
      return;
    }
    // used 可能因其它引用/共享/他人收藏仍为 true，取后端最新值刷新“是否使用”标记
    const fresh = await queryAttachment(row.id);
    if (fresh) {
      row.used = fresh.used;
    }
  } else {
    // 加入收藏：有个人分类时弹出分类选择，否则一键收藏（归入未分类）
    openMoveFavoriteDialog([row.id], true);
  }
};

const handleUnshare = async (ids: string[]) => {
  await unshareAttachment(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleUnfavorite = async (ids: string[]) => {
  await unfavoriteAttachment(ids);
  fetchData();
  ElMessage.success(t('success'));
};

// 加入共享 / 移动分类（共享） 与 我的资源库 移动分类，共用同一个分类选择对话框
const categoryDialogVisible = ref<boolean>(false);
// 'share' 共享资源库分类，'favorite' 我的资源库个人分类
const categoryDialogMode = ref<'share' | 'favorite'>('share');
const categoryTree = ref<any[]>([]);
// 空字符串表示“未分类”（提交时转为 null）
const pickedCategoryId = ref<string>('');
const shareTargetIds = ref<string[]>([]);
// 与后端 ResourceCategory.UNCATEGORIZED 一致：共享库懒创建的默认分类，仅有它时分类选择没有意义
const UNCATEGORIZED_NAME = '未分类';
const isTrivialShareTree = (tree: any[]) => tree.every((c) => c.name === UNCATEGORIZED_NAME && !c.children?.length);
// 本地同步已加入资源库的行（used 随之为 true），免去整页刷新
const markRows = (ids: string[], patch: Record<string, any>) => {
  const set = new Set(ids.map(String));
  data.value.filter((r) => set.has(String(r.id))).forEach((r) => Object.assign(r, patch));
};
// 加入共享：站点尚无分类（或仅有“未分类”）时直接加入，不弹分类选择
const openShareDialog = async (ids: string[]) => {
  shareTargetIds.value = ids;
  pickedCategoryId.value = '';
  categoryDialogMode.value = 'share';
  categoryTree.value = await queryResourceCategoryList();
  if (props.scope === 'all' && isTrivialShareTree(categoryTree.value)) {
    for (const id of ids) {
      await shareAttachment({ id, categoryId: null });
    }
    markRows(ids, { used: true });
    ElMessage.success(t('resource.shareSuccess'));
    return;
  }
  categoryDialogVisible.value = true;
};
// 加入我的 / 移动分类。quick=true（加入收藏）且尚无个人分类时直接收藏到“未分类”，不弹分类选择
const openMoveFavoriteDialog = async (ids: string[], quick = false) => {
  shareTargetIds.value = ids;
  pickedCategoryId.value = '';
  categoryDialogMode.value = 'favorite';
  categoryTree.value = await queryResourceCategoryList({ scope: 'my' });
  if (quick && categoryTree.value.length === 0) {
    await categorizeFavoriteAttachment({ ids, categoryId: null });
    markRows(ids, { favorited: true, used: true });
    ElMessage.success(t('resource.favoriteSuccess'));
    return;
  }
  categoryDialogVisible.value = true;
};
const confirmShare = async () => {
  if (categoryDialogMode.value === 'favorite') {
    await categorizeFavoriteAttachment({ ids: shareTargetIds.value, categoryId: pickedCategoryId.value || null });
  } else {
    for (const id of shareTargetIds.value) {
      await shareAttachment({ id, categoryId: pickedCategoryId.value || null });
    }
  }
  categoryDialogVisible.value = false;
  fetchData();
  ElMessage.success(categoryDialogMode.value === 'favorite' ? t('success') : t('resource.shareSuccess'));
};

const toggleGridSelect = (row: any, event?: Event) => {
  if (event) {
    event.stopPropagation();
    event.preventDefault();
  }
  const id = String(row.id);
  if (gridSelection.value.has(id)) {
    gridSelection.value.delete(id);
  } else {
    gridSelection.value.add(id);
  }
  selection.value = data.value.filter((r) => gridSelection.value.has(String(r.id)));
  emit('selection-change', selection.value);
};
const syncGridSelection = () => {
  const validIds = new Set(data.value.map((r) => String(r.id)));
  // Set 迭代期间删除当前元素是规范定义的安全操作（只删不增），无需先复制
  for (const id of gridSelection.value) {
    if (!validIds.has(id)) gridSelection.value.delete(id);
  }
  selection.value = data.value.filter((r) => gridSelection.value.has(String(r.id)));
  emit('selection-change', selection.value);
};
</script>

<template>
  <div>
    <div v-if="showTypeFilter" class="mb-3">
      <el-segmented v-model="typeFilter" :options="typeOptions" size="default" @change="handleTypeChange" />
    </div>
    <div v-if="showQuery" class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('attachment.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div v-else class="mb-3 flex items-center gap-2">
      <el-input v-model="keyword" :placeholder="$t('resource.keywordPlaceholder')" clearable class="keyword-input" @keyup.enter="handleKeyword" @clear="handleKeyword" />
      <el-button :icon="Search" @click="handleKeyword">{{ $t('search') }}</el-button>
    </div>
    <div v-if="!pick" class="flex items-center">
      <el-popconfirm v-if="scope === 'all'" :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button
            :disabled="selection.length <= 0 || selectionHasUsed || perm('attachment:delete')"
            :title="selectionHasUsed ? $t('resource.deleteUsedDisabled') : ''"
            :icon="Delete"
          >
            {{ $t('delete') }}
          </el-button>
        </template>
      </el-popconfirm>
      <el-button
        v-if="scope === 'all'"
        :disabled="selection.length <= 0 || perm('resource:favorite')"
        :icon="Star"
        @click="openMoveFavoriteDialog(selection.map((row) => row.id), true)"
      >
        {{ $t('resource.favorite') }}
      </el-button>
      <el-popconfirm v-if="scope === 'all'" :title="$t('resource.unfavorite') + '?'" @confirm="() => handleUnfavorite(selectionFavoritedIds)">
        <template #reference>
          <el-button :disabled="selectionFavoritedIds.length <= 0 || perm('resource:favorite')" :icon="StarFilled">
            {{ $t('resource.unfavorite') }}
          </el-button>
        </template>
      </el-popconfirm>
      <el-button
        v-if="scope === 'all'"
        :disabled="selection.length <= 0 || perm('resource:share')"
        :icon="FolderAdd"
        @click="openShareDialog(selection.map((row) => row.id))"
      >
        {{ $t('resource.addToShared') }}
      </el-button>
      <el-button v-if="scope === 'all'" :disabled="perm('attachment:delete')" :icon="Delete" @click="handleCleanUnused">
        {{ $t('resource.cleanUnused') }}
      </el-button>
      <el-button
        v-if="scope === 'shared'"
        :disabled="selection.length <= 0 || perm('resource:share')"
        @click="openShareDialog(selection.map((row) => row.id))"
      >
        {{ $t('resource.moveCategory') }}
      </el-button>
      <el-popconfirm v-if="scope === 'shared'" :title="$t('resource.removeFromShared') + '?'" @confirm="() => handleUnshare(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('resource:share')">{{ $t('resource.removeFromShared') }}</el-button>
        </template>
      </el-popconfirm>
      <el-button
        v-if="scope === 'my'"
        :disabled="selection.length <= 0 || perm('resource:favorite')"
        @click="openMoveFavoriteDialog(selection.map((row) => row.id))"
      >
        {{ $t('resource.moveCategory') }}
      </el-button>
      <el-popconfirm v-if="scope === 'my'" :title="$t('resource.unfavorite') + '?'" @confirm="() => handleUnfavorite(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('resource:favorite')">{{ $t('resource.unfavorite') }}</el-button>
        </template>
      </el-popconfirm>
      <div class="ml-auto flex items-center">
        <el-radio-group v-model="viewMode" size="small" class="mr-2">
          <el-radio-button value="list">
            <el-tooltip :content="$t('view.list')" placement="top">
              <el-icon><List /></el-icon>
            </el-tooltip>
          </el-radio-button>
          <el-radio-button value="grid">
            <el-tooltip :content="$t('view.grid')" placement="top">
              <el-icon><Grid /></el-icon>
            </el-tooltip>
          </el-radio-button>
        </el-radio-group>
        <column-setting v-if="viewMode === 'list'" name="attachment" />
      </div>
    </div>
    <div class="mt-3 app-block">
      <el-table
        v-if="viewMode === 'list'"
        ref="table"
        v-loading="loading"
        :data="data"
        @selection-change="(rows) => { if (!pick) selection = rows; }"
        @row-dblclick="(row) => handleShowDetail(row)"
        @sort-change="handleSort"
      >
        <column-list name="attachment">
          <el-table-column v-if="!pick" type="selection" width="45"></el-table-column>
          <el-table-column property="name" :label="$t('attachment.name')" sortable="custom" min-width="130" show-overflow-tooltip></el-table-column>
          <el-table-column property="path" :label="$t('attachment.path')" sortable="custom" min-width="200" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="url" :label="$t('attachment.url')" sortable="custom" min-width="350" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="length" :label="$t('attachment.length')" sortable="custom" align="right" show-overflow-tooltip>
            <template #default="{ row }">{{ row.size }}</template>
          </el-table-column>
          <el-table-column property="created" :label="$t('attachment.created')" sortable="custom" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('attachment.user')" sort-by="user-username" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column v-if="scope === 'shared'" property="sharedCategoryName" :label="$t('resource.belongCategory')" min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="used" :label="$t('attachment.used')" :sortable="scope === 'all' ? 'custom' : false" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.used ? 'success' : 'info'" size="small">{{ $t(row.used ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="handleShowDetail(row)">{{ pick ? $t('select') : $t('detail') }}</el-button>
              <el-button v-if="pick" type="primary" size="small" link @click="openDetail(row)">{{ $t('detail') }}</el-button>
              <el-button
                v-if="!pick"
                type="primary"
                size="small"
                link
                :disabled="perm('resource:favorite')"
                @click="handleFavorite(row)"
              >
                {{ row.favorited ? $t('resource.unfavorite') : $t('resource.favorite') }}
              </el-button>
              <el-button
                v-if="!pick && scope === 'all'"
                type="primary"
                size="small"
                link
                :disabled="perm('resource:share')"
                @click="openShareDialog([row.id])"
              >
                {{ $t('resource.addToShared') }}
              </el-button>
              <el-button
                v-if="!pick && scope === 'my'"
                type="primary"
                size="small"
                link
                :disabled="perm('resource:favorite')"
                @click="openMoveFavoriteDialog([row.id])"
              >
                {{ $t('resource.moveCategory') }}
              </el-button>
            </template>
          </el-table-column>
        </column-list>
      </el-table>

      <div v-else v-loading="loading" class="attachment-grid">
        <div
          v-for="row in data"
          :key="row.id"
          class="attachment-card"
          :class="{ selected: gridSelection.has(String(row.id)) }"
          @click="() => handleShowDetail(row)"
        >
          <!-- 选择模式：多选时显示复选框；始终提供“详情”预览入口（点卡片本身即选中，不再打开详情） -->
          <div class="attachment-actions" @click.stop>
            <el-checkbox v-if="!pick || multiple" :model-value="gridSelection.has(String(row.id))" @change="() => toggleGridSelect(row)" />
            <template v-if="pick">
              <span class="actions-spacer"></span>
              <el-tooltip :content="$t('detail')" placement="top">
                <button class="card-act" @click="openDetail(row)">
                  <el-icon :size="16"><View /></el-icon>
                </button>
              </el-tooltip>
            </template>
            <template v-if="!pick">
              <span class="actions-spacer"></span>
              <el-tooltip :content="row.favorited ? $t('resource.unfavorite') : $t('resource.favorite')" placement="top">
                <button class="card-act" :class="{ 'is-fav': row.favorited }" :disabled="perm('resource:favorite')" @click="handleFavorite(row)">
                  <el-icon :size="16"><StarFilled v-if="row.favorited" /><Star v-else /></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip v-if="scope === 'all'" :content="$t('resource.addToShared')" placement="top">
                <button class="card-act" :disabled="perm('resource:share')" @click="openShareDialog([row.id])">
                  <el-icon :size="16"><FolderAdd /></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip v-if="scope === 'my'" :content="$t('resource.moveCategory')" placement="top">
                <button class="card-act" :disabled="perm('resource:favorite')" @click="openMoveFavoriteDialog([row.id])">
                  <el-icon :size="16"><Folder /></el-icon>
                </button>
              </el-tooltip>
            </template>
          </div>
          <attachment-thumb :name="row.name" :url="row.url" />
          <div class="attachment-meta">
            <div class="attachment-name" :title="row.name">{{ row.name }}</div>
            <div class="attachment-info">
              <span class="info-size">{{ row.size }}</span>
              <el-tag v-if="!pick" :type="row.used ? 'success' : 'info'" size="small" effect="light">
                {{ $t(row.used ? 'attachment.inUse' : 'attachment.unused') }}
              </el-tag>
            </div>
            <div class="attachment-foot">
              <span class="info-user" :title="row.user?.username">{{ row.user?.username }}</span>
              <span class="info-date">{{ row.created ? dayjs(row.created).format('YYYY-MM-DD') : '' }}</span>
            </div>
            <div v-if="scope === 'shared' && row.sharedCategoryName" class="attachment-cat" :title="row.sharedCategoryName">
              <el-icon><Folder /></el-icon>{{ row.sharedCategoryName }}
            </div>
          </div>
        </div>
        <div v-if="!loading && data.length === 0" class="attachment-empty">{{ $t('error.notFound') }}</div>
      </div>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="pageSizes"
        :layout="pageLayout"
        size="small"
        background
        class="justify-end px-3 py-2"
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <attachment-detail v-model="detailVisible" :row="detailRow" :z-index="zIndex" />

    <el-dialog v-model="categoryDialogVisible" :title="$t('resource.selectCategory')" width="420px" append-to-body>
      <el-tree
        :data="categoryTree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        highlight-current
        default-expand-all
        :current-node-key="pickedCategoryId || undefined"
        @node-click="(node) => (pickedCategoryId = String(node.id))"
      />
      <div class="mt-2 text-xs text-gray-400">{{ $t('resource.uncategorizedHint') }}</div>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">{{ $t('cancel') }}</el-button>
        <el-button type="primary" @click="confirmShare">{{ $t('ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.keyword-input {
  width: 260px;
}
.attachment-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  min-height: 200px;
}
.attachment-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  background: var(--el-bg-color);
}
.attachment-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.attachment-card.selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}
.attachment-meta {
  padding: 8px 9px 6px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.attachment-name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attachment-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.info-size {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.attachment-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-width: 0;
}
.info-user {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info-date {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}
.attachment-cat {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--el-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attachment-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}
.actions-spacer {
  flex: 1;
}
.card-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  color: var(--el-text-color-secondary);
  transition: background 0.15s, color 0.15s;
}
.card-act:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}
.card-act:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}
.card-act.is-fav {
  color: var(--el-color-warning);
}
.attachment-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>

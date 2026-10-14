<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue';
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { NodeDropType } from 'element-plus/es/components/tree/src/tree.type';
import { Plus, Delete, Edit, Grid, FirstAidKit, Switch, CopyDocument, DocumentAdd, Download, ArrowDown, SetUp } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { saveAs } from 'file-saver';
import { perm, currentUser, hasPermission, isShowTier } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { lockedTip } from '@/utils/commercial';
import { toTree, flatTree, findTreeItem, filterTreeNodeByName } from '@/utils/tree';
import { toTextBlob } from '@/utils/textFile';
import { deleteChannel, queryChannelList, queryChannelPermissions, updateChannelNav, updateChannelReal, moveChannel, tidyTreeChannel } from '@/api/content';
import { queryProcessDefinitionList, querySiteList } from '@/api/system';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { useRouteState, stringParam } from '@/composables/useRouteState';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CollapsibleAside from '@/components/CollapsibleAside.vue';
import ChannelForm from './ChannelForm.vue';
import ChannelBatchForm from './ChannelBatchForm.vue';
import ChannelBatchUpdateForm from './ChannelBatchUpdateForm.vue';
import ChannelMoveForm from './ChannelMoveForm.vue';
import ChannelMergeForm from './ChannelMergeForm.vue';
import ChannelTranslateBatch from './components/ChannelTranslateBatch.vue';
import { serializeChannelOutline } from './channelOutline';

defineOptions({
  name: 'ChannelList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const batchFormVisible = ref<boolean>(false);
const batchUpdateVisible = ref<boolean>(false);
const parent = ref<any>();
const beanId = ref<string>();
const beanIds = computed(() => flatTree(data.value).map((row: any) => row.id));
const channelPermissions = ref<string[]>([]);
const processList = ref<any[]>([]);
const isSorted = ref<boolean>(false);
// 列表带查询条件：结果是来自不同上级的平铺行，不可拖拽排序
const isQueried = ref<boolean>(false);
const tidying = ref<boolean>(false);

const isEdit = ref<boolean>(false);
const channelTree = ref<any>();
const channelTreeData = ref<any[]>([]);
const channelTreeLoading = ref<boolean>(false);
const channelTreeFilter = ref<string>();
const asideCollapsed = ref<boolean>(false);
// 导航上下文（列表所属的上级栏目）存于路由 query：刷新、页签往返后恢复
const { channelId } = useRouteState({ channelId: stringParam() });
// 以 ID 为准、节点由栏目树派生：树每次重载后节点对象即更新；树加载前为 undefined
const channel = computed<any>(() => (channelId.value != null ? findTreeItem(channelTreeData.value, (item: any) => item.id === channelId.value) : undefined));

const moveFormVisible = ref<any>(false);
const mergeFormVisible = ref<any>(false);
const translateBatchVisible = ref<boolean>(false);
const currentSiteStore = useCurrentSiteStore();
// 工具栏“批量新增译文”入口仅在当前站点有语言组同伴时显示（与文章管理同款）。判定需 site:list（弹窗本就依赖站点列表）；
// 无该权限时不判定、保持显示，由弹窗内提示
const inLangGroup = ref<boolean>(true);
const fetchLangGroup = async () => {
  const current = currentSiteStore.currentSite;
  if (perm('site:list') || current == null) {
    inLangGroup.value = true;
    return;
  }
  const masterId = current.langParentId ?? current.id;
  const sites = await querySiteList();
  inLangGroup.value = sites.some((site: any) => (site.langParentId ?? site.id) === masterId && site.id !== current.id);
};
watch(() => currentSiteStore.currentSiteId, fetchLangGroup);

// 栏目权限（角色、组织的数据范围）：服务端据同一份清单拒绝，这里据它置灰
const deletable = (bean: any) => currentUser.allChannelPermission || channelPermissions.value.includes(bean.id);

watch(channelTreeFilter, (val) => {
  channelTree.value.filter(val);
});
const fetchChannelTreeData = async () => {
  channelTreeLoading.value = true;
  try {
    channelTreeData.value = toTree(await queryChannelList());
    // 选中的栏目可能已删除，或恢复自其它站点的地址：回落到根栏目并重新加载列表
    if (channelId.value != null && channel.value == null) {
      channelId.value = undefined;
      fetchListData();
    }
    nextTick(() => {
      if (channelTreeFilter.value != null) {
        channelTree.value?.filter(channelTreeFilter.value);
      }
      // setCurrentKey 会展开选中节点的各级父节点，无需另存展开状态
      channelTree.value?.setCurrentKey(channelId.value ?? null);
    });
  } finally {
    channelTreeLoading.value = false;
  }
};
// 列表请求的序号：连续点击导航时，只采用最后一次请求的结果
let listSeq = 0;
const fetchListData = async () => {
  listSeq += 1;
  const seq = listSeq;
  loading.value = true;
  try {
    const sorted = sort.value !== undefined;
    const queried = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0;
    // 查询、排序时结果是平铺的行，不再把所选栏目自身混在它的子栏目里
    const flat = queried || sorted;
    const list = await queryChannelList({ ...toParams(params.value), parentId: channelId.value, isIncludeSelf: !flat, isIncludeChildren: queried, Q_OrderBy: sort.value });
    if (seq !== listSeq) {
      return;
    }
    isSorted.value = sorted;
    isQueried.value = queried;
    data.value = flat ? list : toTree(list);
  } finally {
    if (seq === listSeq) {
      loading.value = false;
    }
  }
};
const fetchData = () => {
  fetchChannelTreeData();
  fetchListData();
};
const fetchProcessList = async () => {
  if (currentUser.commercialTier > 0) {
    processList.value = await queryProcessDefinitionList({ category: 'sys_article', latestVersion: true });
  }
};
const fetchChannelPermissions = async () => {
  channelPermissions.value = await queryChannelPermissions();
};
let sortable;
const initDragTable = () => {
  const tbody = table.value?.$el.querySelector('.el-table__body-wrapper tbody');
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    draggable: '.drag-draggable',
    animation: 200,
    chosenClass: 'sortable-chosen',
    onEnd: async function (event: any) {
      const { oldIndex, newIndex } = event;
      if (oldIndex !== newIndex) {
        let from = oldIndex;
        let to = newIndex;
        let list = data.value;
        const children = list[0]?.children;
        if (children?.length > 0) {
          list = children;
          from -= 1;
          to -= 1;
        }
        try {
          await moveChannel(list[from].id, list[to].id, from > to ? 'before' : 'after');
        } catch {
          // 请求拦截器已提示错误。Sortable 已挪动了行的 DOM 而数据未变：把这一行放回原处
          const row = event.item;
          const body = event.from;
          body.removeChild(row);
          body.insertBefore(row, body.children[oldIndex] ?? null);
          return;
        }
        fetchChannelTreeData();
        list.splice(to, 0, list.splice(from, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};
onMounted(async () => {
  // 拖拽初始化须在任何 await 之前：它查询本视图的 DOM
  initDragTable();
  fetchProcessList();
  fetchChannelPermissions();
  fetchLangGroup();
  if (channelId.value == null) {
    fetchData();
    return;
  }
  // 有恢复出的上级栏目：先加载栏目树完成校验，再查列表，避免带着失效的栏目 ID 发请求
  try {
    await fetchChannelTreeData();
  } catch {
    // 请求拦截器已提示错误；列表照常加载
  }
  // 校验不通过时 fetchChannelTreeData 已回落到根栏目并加载了列表
  if (channelId.value != null) {
    fetchListData();
  }
});
onBeforeUnmount(() => {
  if (sortable !== undefined) {
    sortable.destroy();
  }
});

const handleSort = ({ column, prop, order }: { column: any; prop: string | null; order: string | null }) => {
  if (prop && order) {
    sort.value = (column.sortBy ?? prop) + (order === 'descending' ? '_desc' : '');
  } else {
    sort.value = undefined;
  }
  // 排序只影响列表，栏目树不必重载
  fetchListData();
};
const handleFinished = () => {
  fetchData();
  fetchChannelPermissions();
};
const handleSearch = () => fetchListData();
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  fetchListData();
};

const handleAdd = (bean: any) => {
  beanId.value = undefined;
  parent.value = bean ?? channel.value;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  if (!hasPermission('channel:update')) {
    return;
  }
  beanId.value = id;
  parent.value = null;
  formVisible.value = true;
};
// 开关由 change 事件保存（鼠标与键盘切换都会触发）；保存失败时把开关拨回去，拦截器已提示错误
const handleUpdateNav = async (row: any) => {
  try {
    await updateChannelNav(row.id, row.nav);
    ElMessage.success(t('success'));
  } catch {
    row.nav = !row.nav;
  }
};
const handleUpdateReal = async (row: any) => {
  try {
    await updateChannelReal(row.id, row.real);
    ElMessage.success(t('success'));
  } catch {
    row.real = !row.real;
  }
};
const handleDelete = async (ids: string[]) => {
  await deleteChannel(ids);
  fetchData();
  ElMessage.success(t('success'));
};
// 树节点的上级栏目数据；一级栏目的上级是 el-tree 的虚拟根节点（level 0），返回 undefined
const parentDataOf = (node: Node): any => (node.parent != null && node.parent.level > 0 ? node.parent.data : undefined);
// 拖拽与服务端同一规则：须对被拖动的栏目有权限；换了上级的，还须对新上级有权限（根下不限）
const treeAllowDrag = (node: Node) => deletable(node.data);
const treeAllowDrop = (draggingNode: Node, dropNode: Node, type: 'prev' | 'inner' | 'next') => {
  const newParent = type === 'inner' ? dropNode.data : parentDataOf(dropNode);
  return newParent == null || newParent.id === parentDataOf(draggingNode)?.id || deletable(newParent);
};
const handleDragEnd = async (draggingNode: Node, dropNode: Node | null, dropType: NodeDropType) => {
  if (dropType === 'none' || !dropNode) {
    return;
  }
  try {
    await moveChannel(draggingNode.data.id, dropNode.data.id, dropType);
  } catch {
    // 请求拦截器已提示错误。el-tree 已就地挪动了节点：重新加载，让树回到实际状态
    fetchChannelTreeData();
    return;
  }
  // el-tree 只挪动 children，节点数据上的 parent 回指仍是旧上级（编辑模式据它定位列表）：就地改正，不重载以保留展开状态
  draggingNode.data.parent = dropType === 'inner' ? dropNode.data : parentDataOf(dropNode);
  fetchListData();
  ElMessage.success(t('success'));
};
const handleTidyTree = async () => {
  try {
    await ElMessageBox.confirm(t('channel.tidyTree.confirm'), t('tidyTree'), { type: 'warning' });
  } catch {
    return;
  }
  tidying.value = true;
  try {
    await tidyTreeChannel();
    fetchData();
    ElMessage.success(t('success'));
  } finally {
    tidying.value = false;
  }
};
// 导出所选栏目的整棵子树（未选则全站）为大纲文本，即“批量新增”接受的格式；由浏览器据已加载的栏目树写出，不经服务端
const handleExport = () => {
  const tree = channel.value != null ? [channel.value] : channelTreeData.value;
  const count = flatTree(tree).length;
  if (count <= 0) {
    ElMessage.warning(t('channel.export.empty'));
    return;
  }
  saveAs(toTextBlob(serializeChannelOutline(tree)), 'channels.txt');
  ElMessage.success(t('channel.export.result', { count }));
};
const treeNodeClick = async (node: any) => {
  if (isEdit.value) {
    if (channelId.value !== node.parent?.id) {
      channelId.value = node.parent?.id;
      // 必须先获取列表数据，再打开编辑页面，否则编辑页面的上一条、下一条数据不正确
      await fetchListData();
    }
    handleEdit(node.id);
  } else {
    channelId.value = node.id;
    handleSearch();
  }
};
const treeRootClick = () => {
  channelTree.value.setCurrentKey(null);
  channelId.value = undefined;
  handleSearch();
};
</script>

<template>
  <el-container>
    <collapsible-aside
      v-model:collapsed="asideCollapsed"
      v-model:keyword="channelTreeFilter"
      :search-placeholder="$t('channel.filter')"
      :root-label="$t('channel.root')"
      :root-active="channelId == null"
      @root="treeRootClick"
    >
      <template #root-extra>
        <el-tooltip :content="$t('editMode')" placement="top">
          <el-switch v-model="isEdit" :active-action-icon="Edit" :inactive-action-icon="Edit" />
        </el-tooltip>
      </template>
      <el-tree
        ref="channelTree"
        v-loading="channelTreeLoading"
        :data="channelTreeData"
        :props="{ label: 'name' }"
        :expand-on-click-node="false"
        node-key="id"
        highlight-current
        :draggable="hasPermission('channel:update')"
        :allow-drag="treeAllowDrag"
        :allow-drop="treeAllowDrop"
        :filter-node-method="filterTreeNodeByName"
        @node-click="treeNodeClick"
        @node-drag-end="handleDragEnd"
      ></el-tree>
    </collapsible-aside>
    <el-main class="p-0">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('channel.name')" name="Q_Contains_name"></query-item>
          <query-item :label="$t('channel.slug')" name="Q_Contains_slug"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="perm('channel:create')" :icon="Plus" @click="() => handleAdd(undefined)">{{ $t('add') }}</el-button>
        <el-button :disabled="perm('channel:create')" :icon="DocumentAdd" @click="() => (batchFormVisible = true)">{{ $t('channel.op.batchCreate') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('channel:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-button :disabled="selection.length <= 0 || perm('channel:update')" :icon="SetUp" @click="() => (batchUpdateVisible = true)">{{ $t('channel.op.batchUpdate') }}</el-button>
        <el-button :disabled="perm('channel:update')" :icon="Switch" @click="() => (moveFormVisible = true)">{{ $t('channel.op.batchMove') }}</el-button>
        <!-- 合并会删除原栏目：须同时有编辑与删除权限（服务端同此） -->
        <el-button :disabled="perm('channel:update') || perm('channel:delete')" :icon="CopyDocument" @click="() => (mergeFormVisible = true)">{{ $t('channel.op.batchMerge') }}</el-button>
        <el-dropdown class="ml-3">
          <el-button :loading="tidying">
            {{ $t('channel.op.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="Download" :disabled="channelTreeData.length <= 0" @click="() => handleExport()">{{ $t('channel.op.export') }}</el-dropdown-item>
              <el-dropdown-item :icon="FirstAidKit" :disabled="perm('channel:tidyTree')" @click="() => handleTidyTree()">{{ $t('tidyTree') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button
          v-if="inLangGroup && isShowTier(2)"
          :disabled="perm('channel:translateBatch') || currentUser.commercialTier < 2"
          :title="lockedTip(2)"
          class="ml-3"
          @click="() => (translateBatchVisible = true)"
        >
          {{ $t('channel.op.translateBatch') }}<commercial-badge v-if="currentUser.commercialTier < 2" :tier="2" class="ml-1" />
        </el-button>
        <column-setting name="channel" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          ref="table"
          v-loading="loading"
          row-key="id"
          default-expand-all
          :data="data"
          :row-class-name="({ row }) => (row.children?.length > 0 ? '' : 'drag-draggable')"
          @selection-change="(rows) => (selection = rows)"
          @row-dblclick="(row) => handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="channel">
            <el-table-column type="selection" :selectable="deletable" width="45"></el-table-column>
            <el-table-column property="name" :label="$t('channel.name')" min-width="80" sortable="custom">
              <template #default="{ row }">
                <el-link :href="row.fullUrl" underline="never" target="_blank" type="primary">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column property="slug" :label="$t('channel.slug')" min-width="80" sortable="custom"></el-table-column>
            <el-table-column
              property="channelModel.name"
              :label="$t('channel.channelModel')"
              sort-by="channelModel@model-name"
              display="none"
              sortable="custom"
              min-width="60"
            ></el-table-column>
            <el-table-column property="articleModel.name" :label="$t('channel.articleModel')" sort-by="articleModel@model-name" sortable="custom" min-width="60"></el-table-column>
            <el-table-column property="processKey" :label="$t('channel.processKey')" min-width="60" sortable="custom" show-overflow-tooltip>
              <template #default="{ row }">{{ row.processKey != null ? processList.find((item) => item.key === row.processKey)?.name : undefined }}</template>
            </el-table-column>
            <el-table-column property="nav" :label="$t('channel.nav')" min-width="50">
              <template #default="{ row }">
                <el-switch v-model="row.nav" size="small" :disabled="perm('channel:update') || !deletable(row)" @change="() => handleUpdateNav(row)" />
              </template>
            </el-table-column>
            <el-table-column property="real" :label="$t('channel.real')" min-width="50">
              <template #default="{ row }">
                <el-switch v-model="row.real" size="small" :disabled="perm('channel:update') || !deletable(row)" @change="() => handleUpdateReal(row)" />
              </template>
            </el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column width="42">
              <template #default="{ row }">
                <el-icon
                  class="text-lg align-middle"
                  :class="
                    isSorted || isQueried || perm('channel:update') || !deletable(row) || row.children?.length > 0
                      ? ['cursor-not-allowed', 'text-gray-disabled']
                      : ['cursor-move', 'text-gray-secondary', 'drag-handle']
                  "
                >
                  <Grid />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('channel:create') || !deletable(row)" size="small" link @click="() => handleAdd(row)">{{ $t('channel.addChild') }}</el-button>
                <el-button type="primary" :disabled="perm('channel:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('channel:delete') || !deletable(row)" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
      <channel-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :parent="parent" @finished="handleFinished" />
      <channel-batch-form v-model="batchFormVisible" :parent="channel" :channel-tree="channelTreeData" :channel-permissions="channelPermissions" @finished="handleFinished" />
      <channel-batch-update-form
        v-model="batchUpdateVisible"
        :channels="selection"
        :channel-tree="channelTreeData"
        :channel-permissions="channelPermissions"
        @finished="handleFinished"
      />
      <channel-move-form v-model="moveFormVisible" :channel-permissions="channelPermissions" @finished="handleFinished" />
      <channel-merge-form v-model="mergeFormVisible" :channel-permissions="channelPermissions" @finished="handleFinished" />
      <channel-translate-batch v-model="translateBatchVisible" @finished="fetchData" />
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

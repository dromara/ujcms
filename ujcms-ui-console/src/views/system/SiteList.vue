<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue';
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { NodeDropType } from 'element-plus/es/components/tree/src/tree.type';
import { Plus, Delete, Edit, Grid, FirstAidKit, QuestionFilled } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { currentUser, perm, hasPermission } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { toTree, flatTree, findTreeItem, filterTreeNodeByName } from '@/utils/tree';
import { useRouteState, stringParam, flagParam } from '@/composables/useRouteState';
import { deleteSite, querySiteList, moveSite, openSite, closeSite, tidyTreeSite } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CollapsibleAside from '@/components/CollapsibleAside.vue';
import SiteForm from './SiteForm.vue';

defineOptions({
  name: 'SiteList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => flatTree(data.value).map((row) => row.id));
const parentId = ref<string>();
// 导航上下文（列表所属的上级站点、全局数据）存于路由 query：刷新、页签往返后恢复。
// 键名用 siteId：site 是主题详情页的身份类 query
const { siteId, global: showGlobalData } = useRouteState({
  siteId: stringParam(),
  global: flagParam(() => currentUser.globalPermission),
});
const defaultExpandedKeys = ref<string[]>();
const isSorted = ref<boolean>(false);

const isEdit = ref<boolean>(false);
const siteTree = ref<any>();
const siteTreeData = ref<any[]>([]);
const siteTreeLoading = ref<boolean>(false);
const siteTreeFilter = ref<string>();
const asideCollapsed = ref<boolean>(false);
// 以 ID 为准、节点由站点树派生；树加载前为 undefined
const site = computed<any>(() => (siteId.value != null ? findTreeItem(siteTreeData.value, (item: any) => item.id === siteId.value) : undefined));

watch(siteTreeFilter, (val) => {
  siteTree.value.filter(val);
});
const fetchSiteTreeData = async () => {
  siteTreeLoading.value = true;
  try {
    siteTreeData.value = toTree(await querySiteList({ current: !showGlobalData.value }));
    // 选中的站点可能已删除或不在当前范围（已取消全局数据）：回落到根站点并重新加载列表
    if (siteId.value != null && site.value == null) {
      siteId.value = undefined;
      fetchListData();
    }
    nextTick(() => {
      if (siteTreeFilter.value != null) {
        siteTree.value?.filter(siteTreeFilter.value);
      }
      // setCurrentKey 会展开选中节点的各级父节点
      siteTree.value?.setCurrentKey(siteId.value ?? null);
    });
  } finally {
    siteTreeLoading.value = false;
  }
};
const fetchListData = async () => {
  loading.value = true;
  try {
    isSorted.value = sort.value !== undefined;
    const isQueryed = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0;
    data.value = await querySiteList({
      ...toParams(params.value),
      parentId: siteId.value,
      current: !showGlobalData.value,
      isIncludeSelf: true,
      isIncludeChildren: isQueryed,
      Q_OrderBy: sort.value,
    });
    if (!isQueryed && !isSorted.value) {
      data.value = toTree(data.value);
      defaultExpandedKeys.value = data.value.map((item: any) => item.id);
    } else {
      defaultExpandedKeys.value = undefined;
    }
    parentId.value = showGlobalData.value ? undefined : data.value[0]?.id;
  } finally {
    loading.value = false;
  }
};
const fetchData = () => {
  fetchSiteTreeData();
  fetchListData();
};
let sortable;
const initDragTable = () => {
  const tbody = document.querySelector('#dataTable .el-table__body-wrapper tbody');
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
        await moveSite(list[from].id, list[to].id, from > to ? 'before' : 'after');
        fetchSiteTreeData();
        list.splice(to, 0, list.splice(from, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};
onMounted(async () => {
  // 拖拽初始化须在任何 await 之前：它查询本视图的 DOM
  initDragTable();
  if (siteId.value == null) {
    fetchData();
    return;
  }
  // 有恢复出的上级站点：先加载树完成校验，再查列表，避免带着失效的 ID 发请求
  try {
    await fetchSiteTreeData();
  } catch {
    // 请求拦截器已提示错误；列表照常加载
  }
  // 校验不通过时 fetchSiteTreeData 已回落到根站点并加载了列表
  if (siteId.value != null) {
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
  fetchData();
};
const handleSearch = () => fetchData();
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  fetchData();
};

const variantFrom = ref<any>();
const handleAdd = (pid?: string) => {
  beanId.value = undefined;
  variantFrom.value = undefined;
  if (pid != null) {
    parentId.value = pid;
  }
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  variantFrom.value = undefined;
  formVisible.value = true;
};
// 新增语言版本（multilingual P4）：以该行站点为源，新增其语言变体（预填语言组/域名/名称/复制来源）
const handleAddVariant = (row: any) => {
  beanId.value = undefined;
  variantFrom.value = row;
  formVisible.value = true;
};
// 处于语言组中的站点（变体或仍有变体的主站点）在列表上显示语言徽标
const langMasterIds = computed(() => new Set(flatTree(data.value).map((row) => row.langParentId).filter((id) => id != null)));
const isInLangGroup = (row: any) => row.langParentId != null || langMasterIds.value.has(row.id);
const handleOpen = async (ids: string[]) => {
  await openSite(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleClose = async (ids: string[]) => {
  await closeSite(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteSite(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDragEnd = async (draggingNode: Node, dropNode: Node | null, dropType: NodeDropType) => {
  if (dropType !== 'none' && dropNode) {
    await moveSite(draggingNode.data.id, dropNode.data.id, dropType);
    fetchListData();
  }
};
const handleTidyTree = async () => {
  await tidyTreeSite();
  fetchData();
  ElMessage.success(t('success'));
};
const treeNodeClick = async (node: any) => {
  if (isEdit.value) {
    if (siteId.value !== node.parent?.id) {
      siteId.value = node.parent?.id;
      // 必须先获取列表数据，再打开编辑页面，否则编辑页面的上一条、下一条数据不正确
      await fetchListData();
    }
    handleEdit(node.id);
  } else {
    siteId.value = node.id;
    handleSearch();
  }
};
const treeRootClick = () => {
  siteTree.value.setCurrentKey(null);
  siteId.value = undefined;
  handleSearch();
};
const treeAllowDrag = (node: any) => {
  return showGlobalData.value || siteTreeData.value.findIndex((item) => item.id === node.data.id) === -1;
};
const treeAllowDrop = (draggingNode: any, dropNode: any) => {
  return showGlobalData.value || siteTreeData.value.findIndex((item) => item.id === dropNode.data.id) === -1;
};
</script>

<template>
  <el-container>
    <collapsible-aside
      v-model:collapsed="asideCollapsed"
      v-model:keyword="siteTreeFilter"
      :search-placeholder="$t('site.filter')"
      :root-label="$t('site.root')"
      :root-active="siteId == null"
      @root="treeRootClick"
    >
      <template #root-extra>
        <el-tooltip :content="$t('editMode')" placement="top">
          <el-switch v-model="isEdit" :active-action-icon="Edit" :inactive-action-icon="Edit" />
        </el-tooltip>
      </template>
      <el-tree
        ref="siteTree"
        v-loading="siteTreeLoading"
        :data="siteTreeData"
        :props="{ label: 'name' }"
        :default-expanded-keys="defaultExpandedKeys"
        :expand-on-click-node="false"
        node-key="id"
        highlight-current
        :draggable="hasPermission('site:update')"
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
          <query-item :label="$t('site.name')" name="Q_Contains_name"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="perm('site:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('site:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-button :disabled="perm('site:tidyTree')" :icon="FirstAidKit" @click="() => handleTidyTree()">{{ $t('tidyTree') }}</el-button>
        <el-tooltip placement="top" :content="$t('tidyTree.tooltip')">
          <el-icon class="ml-1 text-base align-text-bottom"><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-checkbox v-if="currentUser.globalPermission" v-model="showGlobalData" class="ml-2 align-middle" :label="$t('globalData')" :border="true" @change="() => fetchData()" />
        <column-setting name="site" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          id="dataTable"
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
          <column-list name="site">
            <el-table-column type="selection" width="38"></el-table-column>
            <el-table-column property="name" :label="$t('site.name')" sortable="custom" min-width="120px" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link :href="row.fullUrl" underline="never" target="_blank" type="primary">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column property="fullUrl" :label="$t('site.url')" min-width="120px" show-overflow-tooltip></el-table-column>
            <el-table-column property="lang" :label="$t('site.lang')" sortable="custom" width="90" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="isInLangGroup(row)" size="small" disable-transitions>{{ row.lang }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column property="domain" :label="$t('site.domain')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="subDir" :label="$t('site.subDir')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="description" :label="$t('site.description')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="theme" :label="$t('site.theme')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="mobileTheme" :label="$t('site.mobileTheme')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="org.name" :label="$t('site.org')" sortable="custom" show-overflow-tooltip></el-table-column>
            <el-table-column property="status" :label="$t('site.status')" sortable="custom" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.status === 0" type="success" size="small">{{ $t(`site.status.${row.status}`) }}</el-tag>
                <el-tag v-if="row.status === 1" type="danger" size="small">{{ $t(`site.status.${row.status}`) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column width="42">
              <template #default="{ row }">
                <el-icon
                  class="text-lg align-middle"
                  :class="
                    isSorted || perm('site:update') || row.children?.length > 0
                      ? ['cursor-not-allowed', 'text-gray-disabled']
                      : ['cursor-move', 'text-gray-secondary', 'drag-handle']
                  "
                >
                  <Grid />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')" width="300">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('site:create')" size="small" link @click="() => handleAdd(row.id)">{{ $t('addChild') }}</el-button>
                <el-button type="primary" :disabled="perm('site:create')" size="small" link @click="() => handleAddVariant(row)">
                  {{ $t('site.op.createLangVariant') }}
                </el-button>
                <el-button type="primary" :disabled="perm('site:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-button v-if="row.status === 1" type="primary" :disabled="perm('site:open') || row.status !== 1" size="small" link @click="() => handleOpen([row.id])">
                  {{ $t('site.op.open') }}
                </el-button>
                <el-button v-else-if="row.status === 0" type="primary" :disabled="perm('site:close') || row.status !== 0" size="small" link @click="() => handleClose([row.id])">
                  {{ $t('site.op.close') }}
                </el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('site:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
      <site-form
        v-model="formVisible"
        :bean-id="beanId"
        :bean-ids="beanIds"
        :parent-id="parentId"
        :show-global-data="showGlobalData"
        :variant-from="variantFrom"
        @finished="fetchData"
      />
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

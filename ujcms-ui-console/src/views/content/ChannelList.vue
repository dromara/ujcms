<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue';
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { NodeDropType } from 'element-plus/es/components/tree/src/tree.type';
import { Plus, Delete, Search, Edit, Grid, FirstAidKit, QuestionFilled, Switch, CopyDocument } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm, currentUser, hasPermission } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { toTree, flatTree } from '@/utils/tree';
import { deleteChannel, queryChannelList, queryChannelPermissions, updateChannelNav, updateChannelReal, moveChannel, tidyTreeChannel } from '@/api/content';
import { queryProcessDefinitionList } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ChannelForm from './ChannelForm.vue';
import ChannelMoveForm from './ChannelMoveForm.vue';
import ChannelMergeForm from './ChannelMergeForm.vue';

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
const parent = ref<any>();
const beanId = ref<string>();
const beanIds = computed(() => flatTree(data.value).map((row: any) => row.id));
const channelPermissions = ref<string[]>([]);
const processList = ref<any[]>([]);
const isSorted = ref<boolean>(false);

const isEdit = ref<boolean>(false);
const channelTree = ref<any>();
const channelTreeData = ref<any[]>([]);
const channelTreeLoading = ref<boolean>(false);
const channelTreeFilter = ref<string>();
const channel = ref<any>();

const moveFormVisible = ref<any>(false);
const mergeFormVisible = ref<any>(false);

const deletable = (bean: any) => currentUser.allChannelPermission || channelPermissions.value.includes(bean.id);

watch(channelTreeFilter, (val) => {
  channelTree.value.filter(val);
});
const fetchChannelTreeData = async () => {
  channelTreeLoading.value = true;
  try {
    channelTreeData.value = toTree(await queryChannelList());
    nextTick(() => {
      if (channelTreeFilter.value != null) {
        channelTree.value.filter(channelTreeFilter.value);
      }
      channelTree.value.setCurrentKey(channel.value?.id);
    });
  } finally {
    channelTreeLoading.value = false;
  }
};
const fetchListData = async () => {
  loading.value = true;
  try {
    isSorted.value = sort.value !== undefined;
    const isQueryed = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0;
    data.value = await queryChannelList({ ...toParams(params.value), parentId: channel.value?.id, isIncludeSelf: true, isIncludeChildren: isQueryed, Q_OrderBy: sort.value });
    if (!isQueryed && !isSorted.value) {
      data.value = toTree(data.value);
    }
  } finally {
    loading.value = false;
  }
};
const fetchData = () => {
  fetchChannelTreeData();
  fetchListData();
};
const fetchProcessList = async () => {
  if (currentUser.epRank > 0) {
    processList.value = await queryProcessDefinitionList({ category: 'sys_article', latestVersion: true });
  }
};
const fetchChannelPermissions = async () => {
  channelPermissions.value = await queryChannelPermissions();
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
        await moveChannel(list[from].id, list[to].id, from > to ? 'before' : 'after');
        fetchChannelTreeData();
        list.splice(to, 0, list.splice(from, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};
onMounted(() => {
  fetchData();
  fetchProcessList();
  fetchChannelPermissions();
  initDragTable();
});
onBeforeUnmount(() => {
  if (sortable !== undefined) {
    sortable.destroy();
  }
});

const handleSort = ({ column, prop, order }: { column: any; prop: string; order: string }) => {
  if (prop && order) {
    sort.value = (column.sortBy ?? prop) + (order === 'descending' ? '_desc' : '');
  } else {
    sort.value = undefined;
  }
  fetchData();
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
const handleUpdateNav = (id: string, nav: boolean) => {
  if (!hasPermission('channel:update')) {
    return;
  }
  updateChannelNav(id, nav);
  ElMessage.success(t('success'));
};
const handleUpdateReal = (id: string, real: boolean) => {
  if (!hasPermission('channel:update')) {
    return;
  }
  updateChannelReal(id, real);
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteChannel(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDragEnd = async (draggingNode: Node, dropNode: Node, dropType: NodeDropType) => {
  if (dropType !== 'none') {
    await moveChannel(draggingNode.data.id, dropNode.data.id, dropType);
    fetchListData();
  }
};
const handleTidyTree = async () => {
  await tidyTreeChannel();
  fetchData();
  ElMessage.success(t('success'));
};
const treeNodeClick = async (node: any) => {
  if (isEdit.value) {
    if (channel.value != node.parent) {
      channel.value = node.parent;
      // 必须先获取列表数据，再打开编辑页面，否则编辑页面的上一条、下一条数据不正确
      await fetchListData();
    }
    handleEdit(node.id);
  } else {
    channel.value = node;
    handleSearch();
  }
};
const treeFilterNode = (value: string, data: any) => {
  if (!value) {
    return true;
  }
  return data.name.includes(value);
};
const treeRootClick = () => {
  channelTree.value.setCurrentKey(null);
  channel.value = undefined;
  handleSearch();
};
</script>

<template>
  <el-container>
    <el-aside width="220px" class="pr-3">
      <el-scrollbar class="p-2 bg-white rounded-sm">
        <el-input v-model="channelTreeFilter" :suffix-icon="Search" size="small" />
        <div class="flex justify-between mx-2 mt-1">
          <el-button :type="channel == null ? 'primary' : undefined" link @click="treeRootClick">
            {{ $t('channel.root') }}
          </el-button>
          <el-tooltip :content="$t('editMode')" placement="top">
            <el-switch v-model="isEdit" :active-action-icon="Edit" :inactive-action-icon="Edit" />
          </el-tooltip>
        </div>
        <el-tree
          ref="channelTree"
          v-loading="channelTreeLoading"
          :data="channelTreeData"
          :props="{ label: 'name' }"
          :expand-on-click-node="false"
          node-key="id"
          highlight-current
          :draggable="hasPermission('channel:update')"
          :filter-node-method="treeFilterNode"
          @node-click="treeNodeClick"
          @node-drag-end="handleDragEnd"
        ></el-tree>
      </el-scrollbar>
    </el-aside>
    <el-main class="p-0">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('channel.name')" name="Q_Contains_name"></query-item>
          <query-item :label="$t('channel.alias')" name="Q_Contains_alias"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="perm('channel:create')" :icon="Plus" @click="() => handleAdd(undefined)">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('channel:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-button :disabled="perm('channel:update')" :icon="Switch" @click="() => (moveFormVisible = true)">{{ $t('channel.op.batchMove') }}</el-button>
        <el-button :disabled="perm('channel:update')" :icon="CopyDocument" @click="() => (mergeFormVisible = true)">{{ $t('channel.op.batchMerge') }}</el-button>
        <el-button :disabled="perm('channel:tidyTree')" :icon="FirstAidKit" @click="() => handleTidyTree()">{{ $t('tidyTree') }}</el-button>
        <el-tooltip placement="top" :content="$t('tidyTree.tooltip')">
          <el-icon class="ml-1 text-base align-text-bottom"><QuestionFilled /></el-icon>
        </el-tooltip>
        <column-setting name="channel" class="ml-2" />
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
          <column-list name="channel">
            <el-table-column type="selection" :selectable="deletable" width="45"></el-table-column>
            <el-table-column property="name" :label="$t('channel.name')" min-width="80" sortable="custom">
              <template #default="{ row }">
                <el-link :href="row.fullUrl" :underline="false" target="_blank" type="primary">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column property="alias" :label="$t('channel.alias')" min-width="80" sortable="custom"></el-table-column>
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
                <el-switch v-model="row.nav" size="small" :disabled="perm('channel:update') || !deletable(row)" @click="() => handleUpdateNav(row.id, row.nav)" />
              </template>
            </el-table-column>
            <el-table-column property="real" :label="$t('channel.real')" min-width="50">
              <template #default="{ row }">
                <el-switch v-model="row.real" size="small" :disabled="perm('channel:update') || !deletable(row)" @click="() => handleUpdateReal(row.id, row.real)" />
              </template>
            </el-table-column>
            <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
            <el-table-column width="42">
              <template #default="{ row }">
                <el-icon
                  class="text-lg align-middle"
                  :class="
                    isSorted || perm('channel:update') || row.children?.length > 0
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
                <el-button type="primary" :disabled="perm('channel:create') || !deletable(row)" size="small" link @click="() => handleAdd(row)">{{ $t('addChild') }}</el-button>
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
      <channel-move-form v-model="moveFormVisible" @finished="handleFinished" />
      <channel-merge-form v-model="mergeFormVisible" @finished="handleFinished" />
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

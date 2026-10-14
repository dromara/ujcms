<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue';
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { NodeDropType } from 'element-plus/es/components/tree/src/tree.type';
import { Plus, Delete, Grid, FirstAidKit, QuestionFilled } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm, hasPermission } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { toTree, flatTree, findTreeItem, filterTreeNodeByName } from '@/utils/tree';
import { useRouteState, stringParam, enumParam } from '@/composables/useRouteState';
import { deleteTopic, queryTopicList, moveTopic, tidyTreeTopic } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CollapsibleAside from '@/components/CollapsibleAside.vue';
import TopicForm from './TopicForm.vue';
import TopicArticlePanel from './components/TopicArticlePanel.vue';

defineOptions({
  name: 'TopicList',
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
const isSorted = ref<boolean>(false);

// 导航上下文（选中专题、子专题/文章页签）存于路由 query：刷新、页签往返后恢复
const { topicId, tab: viewMode } = useRouteState({
  topicId: stringParam(),
  tab: enumParam('subtopic', ['subtopic', 'article']),
});
const topicTree = ref<any>();
const topicTreeData = ref<any[]>([]);
const topicTreeLoading = ref<boolean>(false);
const topicTreeFilter = ref<string>();
const asideCollapsed = ref<boolean>(false);
// 以 ID 为准、节点由专题树派生；树加载前为 undefined
const topic = computed<any>(() => (topicId.value != null ? findTreeItem(topicTreeData.value, (item: any) => item.id === topicId.value) : undefined));

watch(topicTreeFilter, (val) => {
  topicTree.value.filter(val);
});
const fetchTopicTreeData = async () => {
  topicTreeLoading.value = true;
  try {
    topicTreeData.value = toTree(await queryTopicList());
    // 选中的专题可能已删除，或恢复自其它站点的地址：回落到根专题并重新加载列表
    if (topicId.value != null && topic.value == null) {
      topicId.value = undefined;
      fetchListData();
    }
    nextTick(() => {
      if (topicTreeFilter.value != null) {
        topicTree.value?.filter(topicTreeFilter.value);
      }
      // setCurrentKey 会展开选中节点的各级父节点
      topicTree.value?.setCurrentKey(topicId.value ?? null);
    });
  } finally {
    topicTreeLoading.value = false;
  }
};
const fetchListData = async () => {
  loading.value = true;
  try {
    isSorted.value = sort.value !== undefined;
    const isQueryed = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0;
    data.value = await queryTopicList({ ...toParams(params.value), parentId: topicId.value, isIncludeSelf: true, isIncludeChildren: isQueryed, Q_OrderBy: sort.value });
    if (!isQueryed && !isSorted.value) {
      data.value = toTree(data.value);
    }
  } finally {
    loading.value = false;
  }
};
const fetchData = () => {
  fetchTopicTreeData();
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
        await moveTopic(list[from].id, list[to].id, from > to ? 'before' : 'after');
        fetchTopicTreeData();
        list.splice(to, 0, list.splice(from, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};
onMounted(async () => {
  // 拖拽初始化须在任何 await 之前：它查询本视图的 DOM
  initDragTable();
  if (topicId.value == null) {
    fetchData();
    return;
  }
  // 有恢复出的专题：先加载树完成校验，再查列表，避免带着失效的 ID 发请求
  try {
    await fetchTopicTreeData();
  } catch {
    // 请求拦截器已提示错误；列表照常加载
  }
  // 校验不通过时 fetchTopicTreeData 已回落到根专题并加载了列表
  if (topicId.value != null) {
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
const handleFinished = () => {
  fetchData();
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
  parent.value = bean ?? topic.value;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  if (!hasPermission('topic:update')) {
    return;
  }
  beanId.value = id;
  parent.value = null;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteTopic(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDragEnd = async (draggingNode: Node, dropNode: Node | null, dropType: NodeDropType) => {
  if (dropType !== 'none' && dropNode) {
    await moveTopic(draggingNode.data.id, dropNode.data.id, dropType);
    fetchListData();
  }
};
const handleTidyTree = async () => {
  await tidyTreeTopic();
  fetchData();
  ElMessage.success(t('success'));
};
const treeNodeClick = (node: any) => {
  topicId.value = node.id;
  handleSearch();
};
const treeRootClick = () => {
  topicTree.value.setCurrentKey(null);
  topicId.value = undefined;
  handleSearch();
};
</script>

<template>
  <el-container>
    <collapsible-aside
      v-model:collapsed="asideCollapsed"
      v-model:keyword="topicTreeFilter"
      :search-placeholder="$t('topic.filter')"
      :root-label="$t('topic.root')"
      :root-active="topicId == null"
      @root="treeRootClick"
    >
      <el-tree
        ref="topicTree"
        v-loading="topicTreeLoading"
        :data="topicTreeData"
        :props="{ label: 'name' }"
        :expand-on-click-node="false"
        node-key="id"
        highlight-current
        :draggable="hasPermission('topic:update')"
        :filter-node-method="filterTreeNodeByName"
        @node-click="treeNodeClick"
        @node-drag-end="handleDragEnd"
      ></el-tree>
    </collapsible-aside>
    <el-main class="p-0">
      <el-tabs v-model="viewMode">
        <el-tab-pane :label="$t('topic.subtopic')" name="subtopic">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('topic.name')" name="Q_Contains_title"></query-item>
          <query-item :label="$t('topic.slug')" name="Q_Contains_slug"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="perm('topic:create')" :icon="Plus" @click="() => handleAdd(undefined)">{{ $t('topic.add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('topic:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-button :disabled="perm('topic:tidyTree')" :icon="FirstAidKit" @click="() => handleTidyTree()">{{ $t('tidyTree') }}</el-button>
        <el-tooltip placement="top" :content="$t('tidyTree.tooltip')">
          <el-icon class="ml-1 text-base align-text-bottom"><QuestionFilled /></el-icon>
        </el-tooltip>
        <column-setting name="topic" class="ml-2" />
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
          <column-list name="topic">
            <el-table-column type="selection" width="45"></el-table-column>
            <el-table-column property="name" :label="$t('topic.name')" min-width="120" sortable="custom" sort-by="title">
              <template #default="{ row }">
                <el-link :href="row.fullUrl" underline="never" target="_blank" type="primary">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column property="slug" :label="$t('topic.slug')" min-width="80" sortable="custom"></el-table-column>
            <el-table-column
              property="topicModel.name"
              :label="$t('topic.topicModel')"
              sort-by="topicModel@model-name"
              sortable="custom"
              min-width="60"
            ></el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column width="42">
              <template #default="{ row }">
                <el-icon
                  class="text-lg align-middle"
                  :class="
                    isSorted || perm('topic:update') || row.children?.length > 0
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
                <el-button type="primary" :disabled="perm('topic:create')" size="small" link @click="() => handleAdd(row)">{{ $t('topic.addChild') }}</el-button>
                <el-button type="primary" :disabled="perm('topic:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('topic:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
        </el-tab-pane>
        <el-tab-pane :label="$t('topic.articles')" name="article">
          <topic-article-panel v-if="topic?.id" :topic-id="topic.id" />
          <el-empty v-else :description="$t('topic.articles.selectHint')" />
        </el-tab-pane>
      </el-tabs>
      <topic-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :parent="parent" @finished="handleFinished" />
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

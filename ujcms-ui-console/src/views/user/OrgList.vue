<script setup lang="ts">
import { computed, watch, onBeforeUnmount, onMounted, ref, nextTick } from 'vue';
import { currentUser, perm, hasPermission } from '@/stores/useCurrentUser';
import type Node from 'element-plus/es/components/tree/src/model/node';
import type { NodeDropType } from 'element-plus/es/components/tree/src/tree.type';
import { Plus, Delete, Search, Edit, Grid, FirstAidKit, QuestionFilled } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { toParams, resetParams } from '@/utils/common';
import { toTree, flatTree } from '@/utils/tree';
import { deleteOrg, queryOrgList, moveOrg, tidyTreeOrg } from '@/api/user';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import OrgForm from './OrgForm.vue';
import OrgPermissionForm from './OrgPermissionForm.vue';

defineOptions({
  name: 'OrgList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const permissionFormVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => flatTree(data.value).map((row: any) => row.id));
const parentId = ref<string>('1');
const showGlobalData = ref<boolean>(false);
const defaultExpandedKeys = ref<string[]>();
const isSorted = ref<boolean>(false);

const isEdit = ref<boolean>(false);
const orgTree = ref<any>();
const orgTreeData = ref<any[]>([]);
const orgTreeLoading = ref<boolean>(false);
const orgTreeFilter = ref<string>();
const org = ref<any>();

watch(orgTreeFilter, (val) => {
  orgTree.value.filter(val);
});
const fetchOrgTreeData = async () => {
  orgTreeLoading.value = true;
  try {
    orgTreeData.value = toTree(await queryOrgList({ current: !showGlobalData.value }));
    nextTick(() => {
      if (orgTreeFilter.value != null) {
        orgTree.value.filter(orgTreeFilter.value);
      }
      orgTree.value.setCurrentKey(org.value?.id);
    });
  } finally {
    orgTreeLoading.value = false;
  }
};
const fetchListData = async () => {
  loading.value = true;
  try {
    isSorted.value = sort.value !== undefined;
    const isQueryed = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0;
    data.value = await queryOrgList({
      ...toParams(params.value),
      parentId: org.value?.id,
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
    parentId.value = data.value[0]?.id;
  } finally {
    loading.value = false;
  }
};
const fetchData = () => {
  fetchOrgTreeData();
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
        await moveOrg(list[from].id, list[to].id, from > to ? 'before' : 'after');
        fetchOrgTreeData();
        list.splice(to, 0, list.splice(from, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};

onMounted(() => {
  fetchData();
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
const handleSearch = () => fetchListData();
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  fetchData();
};

const handleAdd = (pid?: string) => {
  beanId.value = undefined;
  if (pid != null) {
    parentId.value = pid;
  }
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteOrg(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handlePermissionEdit = (id: string) => {
  beanId.value = id;
  permissionFormVisible.value = true;
};
const deletable = (bean: any) => bean.id > 1;

const handleDragEnd = async (draggingNode: Node, dropNode: Node, dropType: NodeDropType) => {
  if (dropType !== 'none') {
    await moveOrg(draggingNode.data.id, dropNode.data.id, dropType);
    fetchListData();
  }
};
const handleTidyTree = async () => {
  await tidyTreeOrg();
  fetchData();
  ElMessage.success(t('success'));
};
const treeNodeClick = async (node: any) => {
  if (isEdit.value) {
    if (org.value != node.parent) {
      org.value = node.parent;
      // 必须先获取列表数据，再打开编辑页面，否则编辑页面的上一条、下一条数据不正确
      await fetchListData();
    }
    handleEdit(node.id);
  } else {
    org.value = node;
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
  orgTree.value.setCurrentKey(null);
  org.value = undefined;
  handleSearch();
};
const treeAllowDrag = (node: any) => {
  return orgTreeData.value.findIndex((item) => item.id === node.data.id) === -1;
};
const treeAllowDrop = (draggingNode: any, dropNode: any) => {
  return orgTreeData.value.findIndex((item) => item.id === dropNode.data.id) === -1;
};
</script>

<template>
  <el-container>
    <el-aside width="220px" class="pr-3">
      <el-scrollbar class="p-2 bg-white rounded-sm">
        <el-input v-model="orgTreeFilter" :suffix-icon="Search" size="small" />
        <div class="flex justify-between mx-2 mt-1">
          <el-button :type="org == null ? 'primary' : undefined" link @click="treeRootClick">{{ $t('org.root') }}</el-button>
          <el-tooltip :content="$t('editMode')" placement="top">
            <el-switch v-model="isEdit" :active-action-icon="Edit" :inactive-action-icon="Edit" />
          </el-tooltip>
        </div>
        <el-tree
          ref="orgTree"
          v-loading="orgTreeLoading"
          :data="orgTreeData"
          :props="{ label: 'name' }"
          :default-expanded-keys="defaultExpandedKeys"
          :expand-on-click-node="false"
          node-key="id"
          highlight-current
          :draggable="hasPermission('org:update')"
          :allow-drag="treeAllowDrag"
          :allow-drop="treeAllowDrop"
          :filter-node-method="treeFilterNode"
          @node-click="treeNodeClick"
          @node-drag-end="handleDragEnd"
        ></el-tree>
      </el-scrollbar>
    </el-aside>
    <el-main class="p-0">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('org.name')" name="Q_Contains_name"></query-item>
          <query-item :label="$t('org.address')" name="Q_Contains_address"></query-item>
          <query-item :label="$t('org.phone')" name="Q_Contains_phone"></query-item>
          <query-item :label="$t('org.contacts')" name="Q_Contains_contacts"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <el-button :disabled="perm('org:tidyTree')" :icon="FirstAidKit" @click="() => handleTidyTree()">{{ $t('tidyTree') }}</el-button>
        <el-tooltip placement="top" :content="$t('tidyTree.tooltip')">
          <el-icon class="ml-1 text-base align-text-bottom"><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-checkbox v-if="currentUser.globalPermission" v-model="showGlobalData" class="ml-2 align-middle" :label="$t('globalData')" :border="true" @change="() => fetchData()" />
        <column-setting name="org" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          id="dataTable"
          ref="table"
          v-loading="loading"
          row-key="id"
          default-expand-all
          :data="data"
          :row-class-name="({ row }) => (row.children?.length > 0 || row.id === '0' || row.id === '1' ? undefined : 'drag-draggable')"
          @selection-change="(rows) => (selection = rows)"
          @row-dblclick="(row) => handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="org">
            <el-table-column type="selection" :selectable="deletable" width="45"></el-table-column>
            <el-table-column property="name" :label="$t('org.name')" sortable="custom" min-width="120"></el-table-column>
            <el-table-column property="address" :label="$t('org.address')" sortable="custom" display="none" min-width="100"></el-table-column>
            <el-table-column property="phone" :label="$t('org.phone')" sortable="custom" min-width="100"></el-table-column>
            <el-table-column property="contacts" :label="$t('org.contacts')" sortable="custom"></el-table-column>
            <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
            <el-table-column width="42">
              <template #default="{ row }">
                <el-icon
                  class="text-lg align-middle"
                  :class="
                    isSorted || perm('org:update') || row.children?.length > 0 || row.id === '0' || row.id === '1'
                      ? ['cursor-not-allowed', 'text-gray-disabled']
                      : ['cursor-move', 'text-gray-secondary', 'drag-handle']
                  "
                >
                  <Grid />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')" width="240">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('org:create')" size="small" link @click="() => handleAdd(row.id)">{{ $t('addChild') }}</el-button>
                <el-button type="primary" :disabled="perm('org:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-button
                  v-if="currentUser.epRank >= 3 || currentUser.epDisplay"
                  :title="currentUser.epRank < 3 ? $t('error.enterprise.short') : undefined"
                  :disabled="perm('org:updatePermission') || currentUser.epRank < 3"
                  type="primary"
                  size="small"
                  link
                  @click="() => handlePermissionEdit(row.id)"
                >
                  {{ $t('permissionSettings') }}
                </el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="!deletable(row) || perm('org:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
      <org-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :parent-id="parentId" :show-global-data="showGlobalData" @finished="fetchData" />
      <org-permission-form v-model="permissionFormVisible" :bean-id="beanId" :show-global-data="showGlobalData" @finished="fetchData"></org-permission-form>
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

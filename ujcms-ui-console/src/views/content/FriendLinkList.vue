<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { deleteFriendLink, queryFriendLinkList, updateFriendLinkOrder } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import FriendLinkForm from './FriendLinkForm.vue';

defineOptions({
  name: 'FriendLinkList',
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
const beanIds = computed(() => data.value.map((row) => row.id));
const isSorted = ref<boolean>(false);
const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryFriendLinkList({ ...toParams(params.value), Q_OrderBy: sort.value });
    isSorted.value = sort.value !== undefined;
  } finally {
    loading.value = false;
  }
};
let sortable;
const initDragTable = () => {
  const tbody = document.querySelector('#dataTable .el-table__body-wrapper tbody');
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 200,
    chosenClass: 'sortable-chosen',
    onEnd: async function (event: any) {
      const { oldIndex, newIndex } = event;
      if (oldIndex !== newIndex) {
        await updateFriendLinkOrder(data.value[oldIndex].id, data.value[newIndex].id);
        data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
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
const handleSearch = () => fetchData();
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  fetchData();
};

const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteFriendLink(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('friendLink.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('friendLink:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('friendLink:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="friendLink" />
    </div>
    <div class="mt-3 app-block">
      <el-table
        id="dataTable"
        ref="table"
        v-loading="loading"
        row-key="id"
        :data="data"
        @selection-change="(rows) => (selection = rows)"
        @row-dblclick="(row) => handleEdit(row.id)"
        @sort-change="handleSort"
      >
        <column-list name="friendLink">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column width="42">
            <el-icon
              class="text-lg align-middle"
              :class="isSorted || perm('friendLink:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
            >
              <Grid />
            </el-icon>
          </el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="name" :label="$t('friendLink.name')" sortable="custom" :min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="url" :label="$t('friendLink.url')" sortable="custom" :min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column property="type.name" sort-by="typeId" :label="$t('friendLink.type')" sortable="custom" width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="enabled" :label="$t('friendLink.enabled')" sortable="custom" width="120">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('friendLink:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('friendLink:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <friend-link-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="() => fetchData()" />
  </div>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

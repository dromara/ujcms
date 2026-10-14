<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteCollection, updateCollectionOrder, startCollection, stopCollection, clearCollectionRecords, queryCollectionPage } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CollectionForm from './CollectionForm.vue';

defineOptions({
  name: 'CollectionList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
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
    const {
      content,
      page: { totalElements },
    } = await queryCollectionPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
    data.value = content;
    total.value = Number(totalElements);
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
        await updateCollectionOrder(data.value[oldIndex].id, data.value[newIndex].id);
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

const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteCollection(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleStart = async (ids: string[]) => {
  await startCollection(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleStop = async (ids: string[]) => {
  await stopCollection(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleClearRecords = async (ids: string[]) => {
  await clearCollectionRecords(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const errorVisible = ref<boolean>(false);
const errorInfo = ref<string>();
const handleShowError = async (bean: any) => {
  errorVisible.value = true;
  errorInfo.value = bean.errorInfo;
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('collection.name')" name="Q_Contains_name"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :disabled="perm('collection:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('collection:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="collection" class="ml-2" />
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
        <column-list name="collection">
          <el-table-column type="selection" width="45"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column width="42">
            <el-icon
              class="text-lg align-middle"
              :class="isSorted || perm('collection:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
              disalbed
            >
              <Grid />
            </el-icon>
          </el-table-column>
          <el-table-column property="name" :label="$t('collection.name')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="description" :label="$t('collection.description')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="channel.name" :label="$t('collection.channel')" show-overflow-tooltip></el-table-column>
          <el-table-column property="user.username" :label="$t('collection.user')" show-overflow-tooltip></el-table-column>
          <el-table-column property="downloadImage" :label="$t('collection.downloadImage')" sortable="custom" display="none" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.downloadImage ? 'success' : 'info'" size="small">{{ $t(row.downloadImage ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="autoSubmit" :label="$t('collection.autoSubmit')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.autoSubmit ? 'success' : 'info'" size="small">{{ $t(row.autoSubmit ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="allowDuplicate" :label="$t('collection.allowDuplicate')" sortable="custom" display="none" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.allowDuplicate ? 'success' : 'info'" size="small">{{ $t(row.allowDuplicate ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('collection.pages')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }"> {{ row.currentPages + 1 }}/{{ row.totalPages }} </template>
          </el-table-column>
          <el-table-column :label="$t('collection.counts')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }"> {{ row.successCount }}/{{ row.totalCount }} </template>
          </el-table-column>
          <el-table-column property="status" :label="$t('collection.status')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" size="small">{{ $t(`collection.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small">{{ $t(`collection.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $t(`collection.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="330">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('collection:start')" size="small" link @click="() => handleStart([row.id])">{{ $t('collection.op.start') }}</el-button>
              <el-button type="primary" :disabled="perm('collection:stop')" size="small" link @click="() => handleStop([row.id])">{{ $t('collection.op.stop') }}</el-button>
              <el-popconfirm :title="$t('collection.confirmClearRecords')" @confirm="() => handleClearRecords([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('collection:clearRecords')" size="small" link>{{ $t('collection.op.clearRecords') }}</el-button>
                </template>
              </el-popconfirm>
              <el-button type="primary" :disabled="perm('collection:show') || !row.errorInfo" size="small" link @click="() => handleShowError(row)">
                {{ $t('collection.op.showError') }}
              </el-button>
              <el-button type="primary" :disabled="perm('collection:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('collection:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total
        :page-sizes
        :layout="pageLayout"
        class="justify-end px-3 py-2"
        size="small"
        background
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <collection-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
    <el-dialog v-model="errorVisible" :title="$t('collection.op.showError')" width="98%" top="16px">
      <pre><code class="whitespace-pre-wrap">{{ errorInfo }}</code></pre>
    </el-dialog>
  </div>
</template>

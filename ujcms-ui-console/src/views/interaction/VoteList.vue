<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteVote, queryVotePage, updateVoteOrder } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import VoteForm from './VoteForm.vue';

defineOptions({
  name: 'VoteList',
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
    } = await queryVotePage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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
        await updateVoteOrder(data.value[oldIndex].id, data.value[newIndex].id);
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
  await deleteVote(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('vote.title')" name="Q_Contains_title"></query-item>
        <query-item :label="$t('vote.beginDate')" name="Q_GE_beginDate_DateTime,Q_LE_beginDate_DateTime" type="datetime"></query-item>
        <query-item :label="$t('vote.endDate')" name="Q_GE_endDate_DateTime,Q_LE_endDate_DateTime" type="datetime"></query-item>
        <query-item :label="$t('vote.mode')" name="Q_In_mode_Short" :options="[1, 2, 3].map((item) => ({ label: $t(`vote.mode.${item}`), value: item }))"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :disabled="perm('vote:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('vote:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="vote" class="ml-2" />
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
        <column-list name="vote">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column width="42">
            <el-icon
              class="text-lg align-middle"
              :class="isSorted || perm('vote:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
              disalbed
            >
              <Grid />
            </el-icon>
          </el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="title" :label="$t('vote.title')" min-width="280" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="beginDate" :label="$t('vote.beginDate')" min-width="120" sortable="custom" display="none" show-overflow-tooltip>
            <template #default="{ row }">{{ row.beginDate != null ? dayjs(row.beginDate).format('YYYY-MM-DD HH:mm') : '' }}</template>
          </el-table-column>
          <el-table-column property="endDate" :label="$t('vote.endDate')" min-width="120" sortable="custom" display="none" show-overflow-tooltip>
            <template #default="{ row }">{{ row.endDate != null ? dayjs(row.endDate).format('YYYY-MM-DD HH:mm') : '' }}</template>
          </el-table-column>
          <el-table-column property="times" :label="$t('vote.times')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="mode" :label="$t('vote.mode')" sortable="custom" show-overflow-tooltip :formatter="(item) => $t(`vote.mode.${item.mode}`)"></el-table-column>
          <el-table-column property="multiple" :label="$t('vote.multiple')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.multiple ? 'success' : 'info'" size="small">{{ $t(row.multiple ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="enabled" :label="$t('vote.enabled')" min-width="100" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('vote:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('vote:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="pageSizes"
        :layout="pageLayout"
        class="justify-end px-3 py-2"
        size="small"
        background
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <vote-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

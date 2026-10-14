<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { toParams, resetParams } from '@/utils/common';
import { deleteScheduleTask, queryScheduleTaskList, updateScheduleTaskOrder, runScheduleTask } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ScheduleTaskForm from './ScheduleTaskForm.vue';

defineOptions({
  name: 'ScheduleTaskList',
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
    data.value = await queryScheduleTaskList({ ...toParams(params.value), Q_OrderBy: sort.value });
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
        await updateScheduleTaskOrder(data.value[oldIndex].id, data.value[newIndex].id);
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
  await deleteScheduleTask(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleRun = async (ids: string[]) => {
  await runScheduleTask(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const errorVisible = ref<boolean>(false);
const errorInfo = ref<string>();
const handleShowError = (bean: any) => {
  errorVisible.value = true;
  errorInfo.value = bean.errorInfo;
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('scheduleTask.name')" name="Q_Contains_name"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('scheduleTask:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('scheduleTask:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="scheduleTask" />
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
        <column-list name="scheduleTask">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column width="42">
            <el-icon
              class="text-lg align-middle"
              :class="isSorted || perm('scheduleTask:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
            >
              <Grid />
            </el-icon>
          </el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="name" :label="$t('scheduleTask.name')" sortable="custom" :min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="type" :label="$t('scheduleTask.type')" sortable="custom" width="130">
            <template #default="{ row }">{{ $t(`scheduleTask.type.${row.type}`) }}</template>
          </el-table-column>
          <el-table-column property="cron" :label="$t('scheduleTask.cron')" :min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="enabled" :label="$t('scheduleTask.enabled')" sortable="custom" width="120">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="lastRunAt" :label="$t('scheduleTask.lastRunAt')" sortable="custom" width="170">
            <template #default="{ row }">{{ row.lastRunAt ? dayjs(row.lastRunAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}</template>
          </el-table-column>
          <el-table-column property="nextRunAt" :label="$t('scheduleTask.nextRunAt')" width="170">
            <template #default="{ row }">{{ row.nextRunAt ? dayjs(row.nextRunAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}</template>
          </el-table-column>
          <el-table-column property="lastStatus" :label="$t('scheduleTask.lastStatus')" sortable="custom" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.lastStatus === 1" type="success" size="small">{{ $t(`scheduleTask.status.${row.lastStatus}`) }}</el-tag>
              <el-tag v-else-if="row.lastStatus === 2" type="danger" size="small">{{ $t(`scheduleTask.status.${row.lastStatus}`) }}</el-tag>
              <el-tag v-else-if="row.lastStatus === 3" type="warning" size="small">{{ $t(`scheduleTask.status.${row.lastStatus}`) }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $t(`scheduleTask.status.${row.lastStatus}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="230">
            <template #default="{ row }">
              <el-popconfirm :title="$t('scheduleTask.confirmRun')" @confirm="() => handleRun([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('scheduleTask:run') || row.lastStatus === 3" size="small" link>{{ $t('scheduleTask.op.run') }}</el-button>
                </template>
              </el-popconfirm>
              <el-button type="primary" :disabled="perm('scheduleTask:show') || !row.errorInfo" size="small" link @click="() => handleShowError(row)">
                {{ $t('scheduleTask.op.showError') }}
              </el-button>
              <el-button type="primary" :disabled="perm('scheduleTask:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('scheduleTask:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <schedule-task-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="() => fetchData()" />
    <el-dialog v-model="errorVisible" :title="$t('scheduleTask.op.showError')" width="98%" top="16px">
      <pre><code class="whitespace-pre-wrap">{{ errorInfo }}</code></pre>
    </el-dialog>
  </div>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

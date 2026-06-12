<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteTask, queryTaskPage } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import TaskForm from '@/views/system/TaskForm.vue';

defineOptions({
  name: 'TaskList',
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
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryTaskPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

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

const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteTask(ids);
  fetchData();
  ElMessage.success(t('success'));
};
// 方便包含页面重新加载列表
defineExpose({ fetchData });
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('task.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('task:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="task" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="task">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="name" :label="$t('task.name')" sortable="custom" min-width="150" show-overflow-tooltip></el-table-column>
          <el-table-column property="beginDate" :label="$t('task.beginDate')" sortable="custom" width="170">
            <template #default="{ row }">{{ dayjs(row.beginDate).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="endDate" :label="$t('task.endDate')" sortable="custom" width="170" display="none">
            <template #default="{ row }">{{ dayjs(row.endDate).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="current" :label="$t('task.current')" min-width="70" align="right"></el-table-column>
          <el-table-column property="processedIn" :label="$t('task.processedIn')" min-width="70" align="right"></el-table-column>
          <el-table-column property="user.username" :label="$t('task.user')" sort-by="user-username" sortable="custom" min-width="80" align="right"></el-table-column>
          <el-table-column property="status" :label="$t('task.status')" sortable="custom" width="80" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="info" size="small">{{ $t(`task.status.${row.status}`) }}</el-tag>
              <el-tag v-if="row.status === 1" size="small">{{ $t(`task.status.${row.status}`) }}</el-tag>
              <el-tag v-if="row.status === 2" type="danger" size="small">{{ $t(`task.status.${row.status}`) }}</el-tag>
              <el-tag v-if="row.status === 3" type="warning" size="small">{{ $t(`task.status.${row.status}`) }}</el-tag>
              <el-tag v-if="row.status === 4" type="success" size="small">{{ $t(`task.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('task:show')" size="small" link @click="() => handleEdit(row.id)">{{ $t('detail') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('task:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
        size="small"
        background
        class="justify-end px-3 py-2"
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <task-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout } from '@/utils/common';
import { deleteProcessHistory, queryProcessHistoryList } from '@/api/system';
import { queryDictListByAlias } from '@/api/config';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import ProcessTaskList from './ProcessTaskList.vue';

defineOptions({
  name: 'ProcessHistoryList',
});
const { t } = useI18n();
const table = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const instanceId = ref<string>();
const taskListVisible = ref<boolean>(false);
const categoryList = ref<any[]>([]);
const fetchCategoryList = async () => {
  categoryList.value = await queryDictListByAlias('sys_process_category');
};
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryProcessHistoryList({ page: currentPage.value, pageSize: pageSize.value });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(() => {
  fetchData();
  fetchCategoryList();
});

const handleTask = (id: string) => {
  instanceId.value = id;
  taskListVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteProcessHistory(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('processHistory:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="processHistory" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)">
        <column-list name="processHistory">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="310" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="processDefinitionName" :label="$t('processModel.name')"></el-table-column>
          <el-table-column property="processDefinitionCategory" :label="$t('processModel.category')">
            <template #default="{ row }">{{ categoryList.find((item) => item.value === row.processDefinitionCategory)?.name }}</template>
          </el-table-column>
          <el-table-column property="businessKey" :label="$t('processInstance.businessKey')"></el-table-column>
          <el-table-column property="processDefinitionKey" :label="$t('processModel.key')" display="none"></el-table-column>
          <el-table-column property="processDefinitionVersion" :label="$t('processModel.version')" display="none" width="60"></el-table-column>
          <el-table-column property="startedBy" :label="$t('processInstance.startedBy')"></el-table-column>
          <el-table-column property="started" :label="$t('processInstance.started')" width="150">
            <template #default="{ row }">{{ dayjs(row.started).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="ended" :label="$t('processInstance.ended')" width="150">
            <template #default="{ row }">{{ row.ended ? dayjs(row.ended).format('YYYY-MM-DD HH:mm') : undefined }}</template>
          </el-table-column>
          <el-table-column property="deleteReason" :label="$t('processInstance.deleteReason')" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('table.action')" width="140">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('processInstance:task')" size="small" link @click="() => handleTask(row.id)">{{ $t('processInstance.op.task') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('processHistory:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <process-task-list v-model="taskListVisible" :instance-id="instanceId" />
  </div>
</template>

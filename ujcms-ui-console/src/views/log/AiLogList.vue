<script setup lang="ts">
import { onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { queryAiLogPage } from '@/api/ai';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';

defineOptions({
  name: 'AiLogList',
});
const params = ref<any>({});
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const loading = ref<boolean>(false);
const features = [1, 2, 3, 4, 5, 6, 7];

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryAiLogPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

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
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('aiLog.feature')" name="Q_EQ_feature_Short" :options="features.map((item) => ({ label: $t(`aiLog.feature.${item}`), value: item }))"></query-item>
        <query-item
          :label="$t('aiLog.status')"
          name="Q_In_status_Short"
          :options="[0, 1, 2].map((item) => ({ label: $t(`aiLog.status.${item}`), value: item }))"
        ></query-item>
        <query-item :label="$t('aiLog.provider')" name="Q_Like_provider"></query-item>
        <query-item :label="$t('aiLog.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
      </query-form>
    </div>
    <div>
      <column-setting name="aiLog" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data @sort-change="handleSort">
        <column-list name="aiLog">
          <el-table-column property="created" :label="$t('aiLog.created')" min-width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="feature" :label="$t('aiLog.feature')" width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag size="small">{{ $t(`aiLog.feature.${row.feature}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="provider" :label="$t('aiLog.provider')" min-width="150" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="model" :label="$t('aiLog.model')" min-width="130" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('aiLog.tokens')" width="120">
            <template #default="{ row }">{{ Number(row.promptTokens) + Number(row.completionTokens) }}</template>
          </el-table-column>
          <el-table-column property="latencyMs" :label="$t('aiLog.latencyMs')" width="130" sortable="custom"></el-table-column>
          <el-table-column property="status" :label="$t('aiLog.status')" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="success" size="small">{{ $t(`aiLog.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="danger" size="small" :title="row.error">{{ $t(`aiLog.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="accepted" :label="$t('aiLog.accepted')" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.accepted === true" type="success" size="small">{{ $t('yes') }}</el-tag>
              <el-tag v-else-if="row.accepted === false" type="info" size="small">{{ $t('no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('aiLog.user')" width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="error" :label="$t('aiLog.error')" min-width="150" show-overflow-tooltip></el-table-column>
        </column-list>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total
        :page-sizes
        :layout="pageLayout"
        size="small"
        background
        class="justify-end px-3 py-2"
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
  </div>
</template>

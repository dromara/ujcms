<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Delete, ArrowDown } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, stringParam, numberParam } from '@/composables/useRouteState';
import { queryCensusIssuePage, processCensusIssue, deleteCensusIssue, queryCensusPage } from '@/api/census';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';

defineOptions({
  name: 'CensusIssueList',
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
const censusList = ref<any[]>([]);
// 导航上下文（所属普查任务、状态行）存于路由 query：刷新、页签往返后恢复；censusId 亦是普查任务列表「查看问题」的入口。
// censusId 可在本页下拉中改写，须列入路由的 meta.stateQuery，否则每次改写都会重建视图。
// 不按下拉列表校验 censusId：列表只取前 100 条，且用户可能无 census:list 权限
const { censusId, status } = useRouteState({
  censusId: stringParam(),
  status: numberParam(-1, [0, 1, 2]),
});
const type = ref<number | undefined>();

const processVisible = ref<boolean>(false);
const processIds = ref<string[]>([]);
const processStatus = ref<number>(1);
const processText = ref<string>();
const processLoading = ref<boolean>(false);

const fetchCensusList = async () => {
  // 普查任务下拉仅用于筛选。用户可能只有 censusIssue 权限而无 census:list，失败时静默降级为空
  if (perm('census:list')) {
    return;
  }
  const { content } = await queryCensusPage({ page: 1, pageSize: 100 });
  censusList.value = content;
};
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryCensusIssuePage({
      ...toParams(params.value),
      Q_EQ_censusId_Long: censusId.value,
      Q_EQ_type_Short: type.value,
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(() => {
  fetchCensusList();
  fetchData();
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
// 切换状态行：回到第一页
const handleStatusChange = () => {
  currentPage.value = 1;
  fetchData();
};
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  censusId.value = undefined;
  type.value = undefined;
  fetchData();
};

const handleProcess = (ids: string[], toStatus: number) => {
  processIds.value = ids;
  processStatus.value = toStatus;
  processText.value = undefined;
  if (toStatus === 0) {
    // 恢复待处理无需填写处理说明
    submitProcess(ids, toStatus, undefined);
    return;
  }
  processVisible.value = true;
};
const submitProcess = async (ids: string[], toStatus: number, text?: string) => {
  processLoading.value = true;
  try {
    await processCensusIssue(ids, toStatus, text);
    ElMessage.success(t('success'));
    processVisible.value = false;
    fetchData();
  } finally {
    processLoading.value = false;
  }
};
const handleDelete = async (ids: string[]) => {
  await deleteCensusIssue(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('censusIssue.title')" name="Q_Contains_title"></query-item>
        <query-item :label="$t('censusIssue.word')" name="Q_Contains_word"></query-item>
        <query-item :label="$t('censusIssue.url')" name="Q_Contains_url"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-select v-model="censusId" :placeholder="$t('censusIssue.census')" clearable filterable class="w-64" @change="() => fetchData()">
        <el-option v-for="item in censusList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-select v-model="type" :placeholder="$t('censusIssue.type')" clearable class="w-40" @change="() => fetchData()">
        <el-option v-for="n in 9" :key="n" :label="$t(`censusIssue.type.${n}`)" :value="n"></el-option>
      </el-select>
      <el-dropdown :disabled="selection.length <= 0 || perm('censusIssue:process')">
        <el-button :disabled="selection.length <= 0 || perm('censusIssue:process')">
          {{ $t('censusIssue.op.process') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="() => handleProcess(selection.map((row) => row.id), 1)">{{ $t('censusIssue.op.resolve') }}</el-dropdown-item>
            <el-dropdown-item @click="() => handleProcess(selection.map((row) => row.id), 2)">{{ $t('censusIssue.op.ignore') }}</el-dropdown-item>
            <el-dropdown-item @click="() => handleProcess(selection.map((row) => row.id), 0)">{{ $t('censusIssue.op.pending') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('censusIssue:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="censusIssue" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="status" class="block-tabs" @tab-change="handleStatusChange">
        <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
        <el-tab-pane v-for="s in [0, 1, 2]" :key="s" :name="s" :label="$t(`censusIssue.status.${s}`)"></el-tab-pane>
      </el-tabs>
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @sort-change="handleSort">
        <column-list name="censusIssue">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="type" :label="$t('censusIssue.type')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.type >= 6 && row.type <= 8 ? 'danger' : 'warning'" size="small" disable-transitions>{{ $t(`censusIssue.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="title" :label="$t('censusIssue.title')" min-width="180" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="word" :label="$t('censusIssue.word')" min-width="90" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="font-bold text-red-500">{{ row.word }}</span>
            </template>
          </el-table-column>
          <el-table-column property="context" :label="$t('censusIssue.context')" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column property="url" :label="$t('censusIssue.url')" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <a v-if="row.url" :href="row.url" target="_blank" rel="noopener noreferrer" class="text-blue-500 hover:underline">{{ row.url }}</a>
            </template>
          </el-table-column>
          <el-table-column property="httpStatus" :label="$t('censusIssue.httpStatus')" min-width="100"></el-table-column>
          <el-table-column property="status" :label="$t('censusIssue.status')" min-width="90" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="warning" size="small" disable-transitions>{{ $t(`censusIssue.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="success" size="small" disable-transitions>{{ $t(`censusIssue.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>{{ $t(`censusIssue.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="processUser.username" :label="$t('censusIssue.processUser')" min-width="90" show-overflow-tooltip></el-table-column>
          <el-table-column property="processText" :label="$t('censusIssue.processText')" min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('censusIssue.created')" min-width="150" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="190">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 0"
                type="primary"
                :disabled="perm('censusIssue:process')"
                size="small"
                link
                @click="() => handleProcess([row.id], 1)"
              >
                {{ $t('censusIssue.op.resolve') }}
              </el-button>
              <el-button
                v-if="row.status === 0"
                type="primary"
                :disabled="perm('censusIssue:process')"
                size="small"
                link
                @click="() => handleProcess([row.id], 2)"
              >
                {{ $t('censusIssue.op.ignore') }}
              </el-button>
              <el-button
                v-if="row.status !== 0"
                type="primary"
                :disabled="perm('censusIssue:process')"
                size="small"
                link
                @click="() => handleProcess([row.id], 0)"
              >
                {{ $t('censusIssue.op.pending') }}
              </el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('censusIssue:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <el-dialog v-model="processVisible" :title="$t(processStatus === 1 ? 'censusIssue.op.resolve' : 'censusIssue.op.ignore')" width="576px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item :label="$t('censusIssue.processText')">
          <el-input v-model="processText" type="textarea" :rows="4" maxlength="500"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="() => (processVisible = false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="processLoading" @click="() => submitProcess(processIds, processStatus, processText)">{{ $t('submit') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

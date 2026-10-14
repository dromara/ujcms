<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { Plus, Delete, List } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, numberParam } from '@/composables/useRouteState';
import { queryCensusPage, stopCensus, deleteCensus } from '@/api/census';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CensusForm from './CensusForm.vue';
import CensusScoreForm from './CensusScoreForm.vue';
import CensusReportDialog from './CensusReportDialog.vue';

defineOptions({
  name: 'CensusList',
});
const { t } = useI18n();
const router = useRouter();
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
const scoreVisible = ref<boolean>(false);
const reportVisible = ref<boolean>(false);
const beanId = ref<string>();
// 导航上下文（状态行）存于路由 query：刷新、页签往返后恢复
const { status } = useRouteState({ status: numberParam(-1, [0, 1, 2, 3, 4]) });

// 运行中的普查每3秒刷新一次进度
let timer: ReturnType<typeof setInterval> | undefined;
const stopPolling = () => {
  if (timer != null) {
    clearInterval(timer);
    timer = undefined;
  }
};
const startPolling = () => {
  if (timer == null) {
    timer = setInterval(() => fetchData(false), 3000);
  }
};

const fetchData = async (showLoading: boolean = true) => {
  if (showLoading) {
    loading.value = true;
  }
  try {
    const {
      content,
      page: { totalElements },
    } = await queryCensusPage({
      ...toParams(params.value),
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
    if (data.value.some((row) => row.status <= 1)) {
      startPolling();
    } else {
      stopPolling();
    }
  } finally {
    if (showLoading) {
      loading.value = false;
    }
  }
};
onMounted(fetchData);
onUnmounted(stopPolling);

const percentage = (row: any): number => (row.total > 0 ? Math.min(100, Math.round((row.current / row.total) * 100)) : 0);

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
  fetchData();
};

const handleAdd = () => {
  formVisible.value = true;
};
const handleScore = (id: string) => {
  beanId.value = id;
  scoreVisible.value = true;
};
const handleReport = (id: string) => {
  beanId.value = id;
  reportVisible.value = true;
};
const handleIssue = (censusId?: string) => {
  router.push({ name: 'CensusIssueList', query: censusId != null ? { censusId } : undefined });
};
const handleStop = async (ids: string[]) => {
  await stopCensus(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteCensus(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('census.name')" name="Q_Contains_name"></query-item>
        <query-item :label="$t('census.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('census:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-button :disabled="perm('censusIssue:list')" :icon="List" @click="() => handleIssue()">{{ $t('census.op.issue') }}</el-button>
      <el-popconfirm :title="$t('census.confirmStop')" @confirm="() => handleStop(selection.filter((row) => row.status <= 1).map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.filter((row) => row.status <= 1).length <= 0 || perm('census:stop')">{{ $t('census.op.stop') }}</el-button>
        </template>
      </el-popconfirm>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('census:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="census" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="status" class="block-tabs" @tab-change="handleStatusChange">
        <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
        <el-tab-pane v-for="s in [0, 1, 2, 3, 4]" :key="s" :name="s" :label="$t(`census.status.${s}`)"></el-tab-pane>
      </el-tabs>
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleReport(row.id)" @sort-change="handleSort">
        <column-list name="census">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="name" :label="$t('census.name')" min-width="180" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="status" :label="$t('census.status')" min-width="130" sortable="custom">
            <template #default="{ row }">
              <el-progress v-if="row.status === 1" :percentage="percentage(row)"></el-progress>
              <el-tag v-else-if="row.status === 0" type="info" size="small" disable-transitions>{{ $t(`census.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t(`census.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 3" type="warning" size="small" disable-transitions>{{ $t(`census.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="success" size="small" disable-transitions>{{ $t(`census.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="articleCount" :label="$t('census.articleCount')" min-width="80"></el-table-column>
          <el-table-column property="blankChannelCount" :label="$t('census.blankChannelCount')" min-width="80">
            <template #default="{ row }">
              <span :class="row.blankChannelCount > 0 ? 'font-bold text-red-500' : ''">{{ row.blankChannelCount }}</span>
            </template>
          </el-table-column>
          <el-table-column property="staleChannelCount" :label="$t('census.staleChannelCount')" min-width="130">
            <template #default="{ row }">
              <span :class="row.staleChannelCount > 0 ? 'font-bold text-red-500' : ''">{{ row.staleChannelCount }}</span>
            </template>
          </el-table-column>
          <el-table-column property="errorWordCount" :label="$t('census.errorWordCount')" min-width="80">
            <template #default="{ row }">
              <span :class="row.errorWordCount > 0 ? 'font-bold text-red-500' : ''">{{ row.errorWordCount }}</span>
            </template>
          </el-table-column>
          <el-table-column property="sensitiveWordCount" :label="$t('census.sensitiveWordCount')" min-width="80">
            <template #default="{ row }">
              <span :class="row.sensitiveWordCount > 0 ? 'font-bold text-red-500' : ''">{{ row.sensitiveWordCount }}</span>
            </template>
          </el-table-column>
          <el-table-column property="deadLinkCount" :label="$t('census.deadLinkCount')" min-width="80">
            <template #default="{ row }">
              <span :class="row.deadLinkCount > 0 ? 'font-bold text-red-500' : ''">{{ row.deadLinkCount }}</span>
            </template>
          </el-table-column>
          <el-table-column property="finalScore" :label="$t('census.finalScore')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <span v-if="row.finalScore != null" class="font-bold">{{ row.finalScore }}</span>
            </template>
          </el-table-column>
          <el-table-column property="conclusion" :label="$t('census.conclusion')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.conclusion === 1" type="success" size="small" disable-transitions>{{ $t('census.conclusion.1') }}</el-tag>
              <el-tag v-else-if="row.conclusion === 2" type="danger" size="small" disable-transitions>{{ $t('census.conclusion.2') }}</el-tag>
              <el-tooltip v-else-if="row.status === 4 && row.veto" :content="$t('census.score.vetoPending')" placement="top">
                <el-tag type="danger" size="small" disable-transitions>{{ $t('census.score.veto') }}</el-tag>
              </el-tooltip>
              <el-tag v-else-if="row.status === 4" type="info" size="small" disable-transitions>{{ $t('census.conclusion.none') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('census.user')" min-width="90" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('census.created')" min-width="150" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="260">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('census:show')" size="small" link @click="() => handleReport(row.id)">{{ $t('census.op.report') }}</el-button>
              <el-button v-if="row.status === 4" type="primary" :disabled="perm('census:score')" size="small" link @click="() => handleScore(row.id)">
                {{ $t('census.op.score') }}
              </el-button>
              <el-button type="primary" :disabled="perm('censusIssue:list')" size="small" link @click="() => handleIssue(row.id)">{{ $t('census.op.issue') }}</el-button>
              <el-popconfirm v-if="row.status <= 1" :title="$t('census.confirmStop')" @confirm="() => handleStop([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('census:stop')" size="small" link>{{ $t('census.op.stop') }}</el-button>
                </template>
              </el-popconfirm>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('census:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <census-form v-model="formVisible" @finished="fetchData" />
    <census-score-form v-model="scoreVisible" :bean-id="beanId" @finished="fetchData" />
    <census-report-dialog v-model="reportVisible" :bean-id="beanId" />
  </div>
</template>

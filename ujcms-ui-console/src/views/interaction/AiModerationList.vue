<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { queryAiModerationPage, retryAiModeration } from '@/api/ai';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';

// AI审核队列（docs/spec/ai/ugc-moderation.md §6）。跨类型判定队列：按类型/状态/类别/动作/日期过滤，
// 行链接直达目标的编辑视图，失败行可重试。判定仅供参考，内容状态以各自的审核列表为准
defineOptions({
  name: 'AiModerationList',
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
const loading = ref<boolean>(false);
const types = ['comment', 'messageBoard'];
const categories = [1, 2, 3, 4, 5, 6, 7];

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryAiModerationPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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
const handleRetry = async (id: string) => {
  await retryAiModeration(id);
  fetchData();
  ElMessage.success(t('success'));
};
// 目标的编辑视图：评论与留言列表支持 ?id= 直达打开编辑对话框
const handleView = (row: any) => {
  if (row.type === 'comment') {
    router.push({ name: 'CommentList', query: { id: row.refId } });
  } else {
    router.push({ name: 'MessageBoardList', query: { id: row.refId } });
  }
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item
          :label="$t('aiModeration.type')"
          name="Q_EQ_type"
          :multiple="false"
          :options="types.map((item) => ({ label: $t(`aiModeration.type.${item}`), value: item }))"
        ></query-item>
        <query-item
          :label="$t('aiModeration.status')"
          name="Q_In_status_Short"
          :options="[0, 1, 2].map((item) => ({ label: $t(`aiModeration.status.${item}`), value: item }))"
        ></query-item>
        <query-item
          :label="$t('aiModeration.category')"
          name="Q_In_category_Short"
          :options="categories.map((item) => ({ label: $t(`aiModeration.category.${item}`), value: item }))"
        ></query-item>
        <query-item
          :label="$t('aiModeration.action')"
          name="Q_In_action_Short"
          :options="[0, 1, 2].map((item) => ({ label: $t(`aiModeration.action.${item}`), value: item }))"
        ></query-item>
        <query-item :label="$t('aiModeration.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
      </query-form>
    </div>
    <div>
      <column-setting name="aiModeration" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data @sort-change="handleSort">
        <column-list name="aiModeration">
          <el-table-column property="created" :label="$t('aiModeration.created')" min-width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="type" :label="$t('aiModeration.type')" width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag size="small" disable-transitions>{{ $t(`aiModeration.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="refId" :label="$t('aiModeration.refId')" width="170" show-overflow-tooltip></el-table-column>
          <el-table-column property="status" :label="$t('aiModeration.status')" width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="info" size="small" disable-transitions>{{ $t(`aiModeration.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="success" size="small" disable-transitions>{{ $t(`aiModeration.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="danger" size="small" disable-transitions>{{ $t(`aiModeration.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="category" :label="$t('aiModeration.category')" width="120" sortable="custom">
            <template #default="{ row }">
              <template v-if="row.category != null">
                <el-tag v-if="row.category === 1" type="success" size="small" disable-transitions>{{ $t(`aiModeration.category.${row.category}`) }}</el-tag>
                <el-tag v-else type="warning" size="small" disable-transitions>{{ $t(`aiModeration.category.${row.category}`) }} {{ row.confidence }}</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column property="action" :label="$t('aiModeration.action')" width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.action === 1" type="success" size="small" disable-transitions>{{ $t(`aiModeration.action.${row.action}`) }}</el-tag>
              <el-tag v-else-if="row.action === 2" type="danger" size="small" disable-transitions>{{ $t(`aiModeration.action.${row.action}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>{{ $t(`aiModeration.action.${row.action}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="reason" :label="$t('aiModeration.reason')" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column property="dictHits" :label="$t('aiModeration.dictHits')" min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="provider" :label="$t('aiModeration.provider')" width="100" show-overflow-tooltip></el-table-column>
          <el-table-column property="model" :label="$t('aiModeration.model')" min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('table.action')" width="120">
            <template #default="{ row }">
              <el-button type="primary" size="small" link @click="() => handleView(row)">{{ $t('aiModeration.op.view') }}</el-button>
              <el-button
                v-if="row.status !== 1"
                type="primary"
                :disabled="perm('aiModeration:review')"
                size="small"
                link
                @click="() => handleRetry(row.id)"
              >
                {{ $t('aiModeration.op.retry') }}
              </el-button>
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
        size="small"
        background
        class="justify-end px-3 py-2"
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
  </div>
</template>

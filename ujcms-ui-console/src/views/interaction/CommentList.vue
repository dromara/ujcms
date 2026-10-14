<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Delete, ArrowDown } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, stringParam, numberParam } from '@/composables/useRouteState';
import { deleteComment, queryCommentPage, updateCommentStatus } from '@/api/interaction';
import { queryAiModerationBatch, retryAiModeration } from '@/api/ai';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import CommentForm from './CommentForm.vue';
import AiModerationTag from './AiModerationTag.vue';

defineOptions({
  name: 'CommentList',
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

// 导航上下文（状态行）存于路由 query：刷新、页签往返后恢复；status 亦供首页待办深链（?status=1）。
// id 为一次性入口（AI 审核队列的行链接），挂载时取用后即移除
const { status, id: linkId } = useRouteState({
  status: numberParam(-1, [0, 1, 2]),
  id: stringParam(),
});

// AI审核角标（docs/spec/ai/ugc-moderation.md §6）：商业版T2且有权限时，每页加载后一次批量取回判定。
// 仅装饰列，不参与本列表的查询与排序（专用队列见 AI审核 页面）
const aiModerationVisible = computed(() => currentUser.commercialTier >= 2 && !perm('aiModeration:list'));
const aiVerdicts = ref<Record<string, any>>({});
const fetchVerdicts = async () => {
  if (!aiModerationVisible.value || data.value.length <= 0) {
    aiVerdicts.value = {};
    return;
  }
  const list = await queryAiModerationBatch(
    'comment',
    data.value.map((row) => String(row.id)),
  );
  aiVerdicts.value = Object.fromEntries(list.map((item: any) => [String(item.refId), item]));
};
const handleVerdictRetry = async (id: string) => {
  await retryAiModeration(id);
  fetchVerdicts();
  ElMessage.success(t('success'));
};

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryCommentPage({
      ...toParams(params.value),
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
    fetchVerdicts();
  } finally {
    loading.value = false;
  }
};
onMounted(() => {
  fetchData();
  // AI审核队列的行链接经 ?id= 直达目标编辑视图。用后即从地址移除：页签记录的是完整地址，
  // 留着它，刷新或切回本页签都会再次弹出编辑框
  if (linkId.value != null) {
    handleEdit(linkId.value);
    linkId.value = undefined;
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

const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteComment(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleStatus = async (ids: string[], status: number) => {
  await updateCommentStatus(ids, status);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('comment.text')" name="Q_Contains_text"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-dropdown :disabled="selection.length <= 0 || perm('comment:updateStatus')">
        <el-button :disabled="selection.length <= 0 || perm('comment:updateStatus')">
          {{ $t('comment.op.status') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="n in [0, 1, 2]"
              :key="n"
              @click="
                () =>
                  handleStatus(
                    selection.map((row) => row.id),
                    n,
                  )
              "
            >
              {{ $t(`comment.status.${n}`) }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('comment:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="comment" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="status" class="block-tabs" @tab-change="handleStatusChange">
        <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
        <el-tab-pane v-for="s in [0, 1, 2]" :key="s" :name="s" :label="$t(`comment.status.${s}`)"></el-tab-pane>
      </el-tabs>
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="comment">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="text" :label="$t('comment.text')" min-width="260" show-overflow-tooltip></el-table-column>
          <el-table-column property="images" :label="$t('comment.images')" min-width="120">
            <template #default="{ row }">
              <el-image
                v-for="(url, index) in row.images ?? []"
                :key="url"
                :src="url"
                :preview-src-list="row.images"
                :initial-index="Number(index)"
                preview-teleported
                fit="cover"
                class="mr-1 align-middle"
                style="width: 32px; height: 32px"
              ></el-image>
            </template>
          </el-table-column>
          <el-table-column property="article.title" :label="$t('comment.article')" min-width="160" show-overflow-tooltip></el-table-column>
          <el-table-column
            property="user.username"
            :label="$t('comment.user')"
            min-width="80"
            sort-by="user-username"
            sortable="custom"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column property="created" :label="$t('comment.created')" min-width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="status" :label="$t('comment.status')" min-width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="success" size="small" disable-transitions>{{ $t(`comment.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="info" size="small" disable-transitions>{{ $t(`comment.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t(`comment.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="aiModerationVisible" :label="$t('aiModeration')" min-width="130">
            <template #default="{ row }">
              <ai-moderation-tag
                :verdict="aiVerdicts[row.id]"
                :retryable="!perm('aiModeration:review')"
                @retry="() => handleVerdictRetry(aiVerdicts[row.id].id)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('comment:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('comment:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <comment-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

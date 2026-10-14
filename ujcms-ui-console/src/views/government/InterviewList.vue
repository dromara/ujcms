<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, ArrowDown } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, numberParam } from '@/composables/useRouteState';
import { deleteInterview, queryInterviewPage, publishInterview, unpublishInterview } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import InterviewForm from './InterviewForm.vue';
import InterviewLiveForm from './InterviewLiveForm.vue';

defineOptions({
  name: 'InterviewList',
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
const liveVisible = ref<boolean>(false);
const liveForm = ref<any>();
const beanId = ref<string>();
const liveBeanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));

// 导航上下文（状态行）存于路由 query：刷新、页签往返后恢复；亦供首页待办等深链（?status=）
const { status } = useRouteState({ status: numberParam(-1, [0, 1, 2]) });

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryInterviewPage({
      ...toParams(params.value),
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
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleLive = (id: string) => {
  liveBeanId.value = id;
  liveVisible.value = true;
};
// 编辑对话框可从“管理”工作台唤起（修改主持人、嘉宾等），保存后同步刷新工作台头部信息与发言人选项
const handleFormFinished = () => {
  fetchData();
  liveForm.value?.refresh();
};
const handleDelete = async (ids: string[]) => {
  await deleteInterview(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handlePublish = async (ids: string[]) => {
  await publishInterview(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleUnpublish = async (ids: string[]) => {
  await unpublishInterview(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('interview.title')" name="Q_Contains_title"></query-item>
        <query-item :label="$t('interview.org')" name="Q_Contains_org"></query-item>
        <query-item :label="$t('interview.beginDate')" name="Q_GE_beginDate_DateTime,Q_LE_beginDate_DateTime" type="datetime"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('interview:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-dropdown :disabled="selection.length <= 0 || perm('interview:publish')">
        <el-button :disabled="selection.length <= 0 || perm('interview:publish')">
          {{ $t('interview.op.publish') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :disabled="perm('interview:publish')" @click="() => handlePublish(selection.map((row) => row.id))">
              {{ $t('interview.op.publish') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('interview:publish')" @click="() => handleUnpublish(selection.map((row) => row.id))">
              {{ $t('interview.op.unpublish') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('interview:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="interview" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="status" class="block-tabs" @tab-change="handleStatusChange">
        <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
        <el-tab-pane v-for="s in [0, 1, 2]" :key="s" :name="s" :label="$t(`interview.status.${s}`)"></el-tab-pane>
      </el-tabs>
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="interview">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="title" :label="$t('interview.title')" min-width="220" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="org" :label="$t('interview.org')" min-width="120" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="host" :label="$t('interview.host')" min-width="90" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="beginDate" :label="$t('interview.beginDate')" min-width="130" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.beginDate).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="status" :label="$t('interview.status')" min-width="90" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="info" size="small" disable-transitions>{{ $t(`interview.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="danger" size="small" disable-transitions>{{ $t(`interview.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="success" size="small" disable-transitions>{{ $t(`interview.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="published" :label="$t('interview.published')" min-width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.published ? 'success' : 'info'" size="small" disable-transitions>{{ $t(row.published ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="questionEnabled" :label="$t('interview.questionEnabled')" min-width="90" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.questionEnabled ? 'success' : 'info'" size="small" disable-transitions>{{ $t(row.questionEnabled ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="created" :label="$t('interview.created')" min-width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="210">
            <template #default="{ row }">
              <el-button
                type="primary"
                :disabled="perm('interview:live') && perm('interviewMessage:list') && perm('interviewQuestion:list')"
                size="small"
                link
                @click="() => handleLive(row.id)"
              >
                {{ $t('interview.op.manage') }}
              </el-button>
              <el-button v-if="!row.published" type="primary" :disabled="perm('interview:publish')" size="small" link @click="() => handlePublish([row.id])">
                {{ $t('interview.op.publish') }}
              </el-button>
              <el-button v-else type="primary" :disabled="perm('interview:publish')" size="small" link @click="() => handleUnpublish([row.id])">
                {{ $t('interview.op.unpublish') }}
              </el-button>
              <el-button type="primary" :disabled="perm('interview:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('interview:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <interview-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="handleFormFinished" />
    <interview-live-form ref="liveForm" v-model="liveVisible" :bean-id="liveBeanId" @finished="fetchData" @edit="handleEdit" />
  </div>
</template>

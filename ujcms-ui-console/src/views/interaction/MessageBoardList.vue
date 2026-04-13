<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, ArrowDown } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteMessageBoard, queryMessageBoardPage, updateMessageBoardStatus } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import MessageBoardForm from './MessageBoardForm.vue';

defineOptions({
  name: 'MessageBoardList',
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

const route = useRoute();
const status = ref<number>(Number(route.query.status ?? '-1'));

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryMessageBoardPage({
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
  await deleteMessageBoard(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleStatus = async (ids: string[], status: number) => {
  await updateMessageBoardStatus(ids, status);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('messageBoard.title')" name="Q_Contains_title"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('messageBoard:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-dropdown :disabled="selection.length <= 0 || perm('messageBoard:updateStatus')">
        <el-button :disabled="selection.length <= 0 || perm('messageBoard:updateStatus')">
          {{ $t('messageBoard.op.status') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
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
              {{ $t(`messageBoard.status.${n}`) }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('messageBoard:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="messageBoard" />
    </div>
    <el-radio-group v-model="status" class="mt-3" @change="() => fetchData()">
      <el-radio-button :value="-1">{{ $t('all') }}</el-radio-button>
      <el-radio-button :value="0">{{ $t('messageBoard.status.0') }}</el-radio-button>
      <el-radio-button :value="1">{{ $t('messageBoard.status.1') }}</el-radio-button>
      <el-radio-button :value="2">{{ $t('messageBoard.status.2') }}</el-radio-button>
    </el-radio-group>
    <div class="app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="messageBoard">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="title" :label="$t('messageBoard.title')" min-width="260" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="type.name" :label="$t('messageBoard.type')" min-width="80" sort-by="type@dict-name" sortable="custom"></el-table-column>
          <el-table-column
            property="user.username"
            :label="$t('messageBoard.user')"
            min-width="80"
            sort-by="user-username"
            sortable="custom"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column property="created" :label="$t('messageBoard.created')" min-width="120" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="replied" :label="$t('messageBoard.replied')" min-width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.replied ? 'success' : 'info'" size="small" disable-transitions>{{ $t(row.replied ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="status" :label="$t('messageBoard.status')" min-width="80" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="success" size="small" disable-transitions>{{ $t(`messageBoard.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="info" size="small" disable-transitions>{{ $t(`messageBoard.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t(`messageBoard.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('messageBoard:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('messageBoard:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <message-board-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

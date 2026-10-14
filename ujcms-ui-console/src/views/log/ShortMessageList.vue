<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteShortMessage, queryShortMessagePage } from '@/api/log';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ShortMessageForm from './ShortMessageForm.vue';

defineOptions({
  name: 'ShortMessageList',
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
    } = await queryShortMessagePage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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

const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteShortMessage(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('shortMessage.receiver')" name="Q_Contains_receiver"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('shortMessage:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="shortMessage" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="shortMessage">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="type" :label="$t('shortMessage.type')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.type === 1" size="small">{{ $t(`shortMessage.type.${row.type}`) }}</el-tag>
              <el-tag v-else type="success" size="small">{{ $t(`shortMessage.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="receiver" :label="$t('shortMessage.receiver')" sortable="custom" show-overflow-tooltip></el-table-column>
          <!-- 仍有效的验证码就是可用的登录第二因子，后端不下发，也不允许按此列查询、排序 -->
          <el-table-column property="code" :label="$t('shortMessage.code')" width="100">
            <template #default="{ row }">
              <span v-if="row.code != null">{{ row.code }}</span>
              <el-tooltip v-else :content="$t('shortMessage.code.hidden')" placement="top">
                <span class="text-gray-placeholder">******</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column property="sendDate" :label="$t('shortMessage.sendDate')" sortable="custom" width="170">
            <template #default="{ row }">{{ dayjs(row.sendDate).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="ip" :label="$t('shortMessage.ip')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="attempts" :label="$t('shortMessage.attempts')" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="usage" :label="$t('shortMessage.usage')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.type" size="small">{{ $t(`shortMessage.usage.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="status" :label="$t('shortMessage.status')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" size="small">{{ $t(`shortMessage.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="[2, 3].includes(row.status)" type="danger" size="small">{{ $t(`shortMessage.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="[4].includes(row.status)" type="warning" size="small">{{ $t(`shortMessage.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status != null" type="info" size="small">{{ $t(`shortMessage.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('shortMessage:show')" size="small" link @click="() => handleEdit(row.id)">{{ $t('view') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('shortMessage:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <short-message-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

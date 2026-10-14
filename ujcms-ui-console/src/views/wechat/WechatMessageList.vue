<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteWechatMessage, queryWechatAccountList, queryWechatMessagePage } from '@/api/wechat';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';

defineOptions({
  name: 'WechatMessageList',
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
const accountList = ref<any[]>([]);
const accountId = ref<string>();
const direction = ref<number>(-1);

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryWechatMessagePage({
      ...toParams(params.value),
      Q_EQ_accountId_Long: accountId.value,
      Q_EQ_direction_Short: direction.value !== -1 ? direction.value : undefined,
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
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
};
onMounted(() => {
  fetchData();
  fetchAccountList();
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
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  accountId.value = undefined;
  direction.value = -1;
  sort.value = undefined;
  fetchData();
};
const handleDelete = async (ids: string[]) => {
  await deleteWechatMessage(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const accountName = (id: string): string => accountList.value.find((item) => item.id === id)?.name ?? '';
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('wechatMessage.openid')" name="Q_Contains_openid"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-select v-model="accountId" clearable :placeholder="$t('wechatMessage.account')" class="w-48" @change="() => fetchData()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-select v-model="direction" class="w-36" @change="() => fetchData()">
        <el-option :value="-1" :label="$t('wechatMessage.direction.all')"></el-option>
        <el-option v-for="n in [0, 1]" :key="n" :value="n" :label="$t(`wechatMessage.direction.${n}`)"></el-option>
      </el-select>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('wechatMessage:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="wechatMessage" />
    </div>
    <div class="mt-3 app-block">
      <el-table
        id="dataTable"
        ref="table"
        v-loading="loading"
        row-key="id"
        :data="data"
        @selection-change="(rows) => (selection = rows)"
        @sort-change="handleSort"
      >
        <column-list name="wechatMessage">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="accountId" :label="$t('wechatMessage.account')" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column property="openid" :label="$t('wechatMessage.openid')" sortable="custom" :min-width="180" show-overflow-tooltip></el-table-column>
          <el-table-column property="direction" :label="$t('wechatMessage.direction')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag :type="row.direction === 0 ? 'primary' : 'success'" size="small">{{ $t(`wechatMessage.direction.${row.direction}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="msgType" :label="$t('wechatMessage.msgType')" sortable="custom" width="110"></el-table-column>
          <el-table-column property="content" :label="$t('wechatMessage.content')" :min-width="240" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('wechatMessage.created')" sortable="custom" width="170">
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="80">
            <template #default="{ row }">
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatMessage:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
  </div>
</template>

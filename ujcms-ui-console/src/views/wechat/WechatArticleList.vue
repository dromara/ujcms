<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteWechatArticle, publishWechatArticle, queryWechatAccountList, queryWechatArticlePage, refreshWechatArticle } from '@/api/wechat';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import WechatItemsTag from './WechatItemsTag.vue';

defineOptions({
  name: 'WechatArticleList',
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
const status = ref<number>(-1);

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryWechatArticlePage({
      ...toParams(params.value),
      Q_EQ_accountId_Long: accountId.value,
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
  status.value = -1;
  sort.value = undefined;
  fetchData();
};

const handlePublish = async (ids: string[]) => {
  await publishWechatArticle(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleRefresh = async (ids: string[]) => {
  await refreshWechatArticle(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteWechatArticle(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const statusType = (value: number): 'info' | 'warning' | 'success' | 'danger' => {
  switch (value) {
    case 0:
      return 'info';
    case 1:
      return 'warning';
    case 2:
      return 'success';
    default:
      return 'danger';
  }
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('wechatArticle.title')" name="Q_Contains_title"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-select v-model="accountId" clearable :placeholder="$t('wechatArticle.account')" class="w-48" @change="() => fetchData()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-select v-model="status" class="w-36" @change="() => fetchData()">
        <el-option :value="-1" :label="$t('wechatArticle.status.all')"></el-option>
        <el-option v-for="n in [0, 1, 2, 3]" :key="n" :value="n" :label="$t(`wechatArticle.status.${n}`)"></el-option>
      </el-select>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('wechatArticle:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="wechatArticle" />
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
        <column-list name="wechatArticle">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="title" :label="$t('wechatArticle.title')" sortable="custom" :min-width="200">
            <template #default="{ row }">
              <!-- 标题可截断，“多图文 N 篇”标签始终可见：它是悬停查看各篇标题的触发点，不能被单元格的省略号裁掉 -->
              <div class="flex min-w-0 items-center">
                <span class="min-w-0 truncate" :title="row.title">{{ row.title }}</span>
                <wechat-items-tag :items="row.items ?? []" url-field="articleUrl" />
              </div>
            </template>
          </el-table-column>
          <el-table-column property="account.name" :label="$t('wechatArticle.account')" width="140" show-overflow-tooltip></el-table-column>
          <el-table-column property="status" :label="$t('wechatArticle.status')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ $t(`wechatArticle.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="error" :label="$t('wechatArticle.error')" :min-width="180" show-overflow-tooltip></el-table-column>
          <el-table-column property="articleUrl" :label="$t('wechatArticle.articleUrl')" width="100">
            <template #default="{ row }">
              <a v-if="row.articleUrl" :href="row.articleUrl" target="_blank" class="text-primary">{{ $t('wechatArticle.articleUrl.view') }}</a>
            </template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('wechatArticle.user')" width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('wechatArticle.created')" sortable="custom" width="170">
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="180">
            <template #default="{ row }">
              <el-popconfirm :title="$t('wechatArticle.publishConfirm')" :width="320" @confirm="() => handlePublish([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatArticle:publish') || row.status !== 0" size="small" link>
                    {{ $t('wechatArticle.op.publish') }}
                  </el-button>
                </template>
              </el-popconfirm>
              <el-button
                type="primary"
                :disabled="perm('wechatArticle:refresh') || row.status !== 1"
                size="small"
                link
                @click="() => handleRefresh([row.id])"
              >
                {{ $t('wechatArticle.op.refresh') }}
              </el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatArticle:delete')" size="small" link>{{ $t('delete') }}</el-button>
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

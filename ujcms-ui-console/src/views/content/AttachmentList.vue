<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteAttachment, queryAttachmentPage } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';

defineOptions({
  name: 'AttachmentList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryAttachmentPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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

const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteAttachment(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('attachment.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('attachment:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="attachment" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="attachment">
          <el-table-column type="selection" :selectable="(row) => !row.used" width="45"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="name" :label="$t('attachment.name')" sortable="custom" min-width="130" show-overflow-tooltip></el-table-column>
          <el-table-column property="path" :label="$t('attachment.path')" sortable="custom" min-width="200" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="url" :label="$t('attachment.url')" sortable="custom" min-width="350" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="length" :label="$t('attachment.length')" sortable="custom" align="right" show-overflow-tooltip>
            <template #default="{ row }">{{ row.size }}</template>
          </el-table-column>
          <el-table-column property="created" :label="$t('attachment.created')" sortable="custom" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('attachment.user')" sort-by="user-username" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('attachment.refer')" show-overflow-tooltip>
            <template #default="{ row }">
              <div v-for="refer in row.referList" :key="`${refer.referType}-${refer.referId}`">{{ `${refer.referType}-${refer.referId}` }}</div>
            </template>
          </el-table-column>
          <el-table-column property="used" :label="$t('attachment.used')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag :type="row.used ? 'success' : 'info'" size="small">{{ $t(row.used ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="row.used || perm('attachment:delete')" size="small" link>{{ $t('delete') }}</el-button>
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

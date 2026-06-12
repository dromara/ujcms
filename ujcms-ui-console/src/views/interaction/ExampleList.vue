<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteExample, queryExamplePage } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ExampleForm from './ExampleForm.vue';

defineOptions({
  name: 'ExampleList',
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
    } = await queryExamplePage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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
  await deleteExample(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('example.name')" name="Q_Contains_name"></query-item>
        <query-item :label="$t('example.description')" name="Q_Contains_description"></query-item>
        <query-item :label="$t('example.height')" name="Q_GE_height_Int,Q_LE_height_Int" type="number"></query-item>
        <query-item :label="$t('example.birthday')" name="Q_GE_birthday_DateTime,Q_LE_birthday_DateTime" type="datetime"></query-item>
        <query-item :label="$t('example.enabled')" name="Q_In_enabled" :options="['1', '0'].map((item) => ({ label: $t(item === '1' ? 'yes' : 'no'), value: item }))"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :disabled="perm('example:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('example:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="example" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="example">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="name" :label="$t('example.name')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="description" :label="$t('example.description')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="height" :label="$t('example.height')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="birthday" :label="$t('example.birthday')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.publishDate).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="enabled" :label="$t('example.enabled')" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('example:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('example:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
        class="justify-end px-3 py-2"
        size="small"
        background
        @size-change="() => fetchData()"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
    <example-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

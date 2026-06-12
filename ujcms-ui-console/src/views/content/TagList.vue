<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteTag, queryTagPage } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import TagForm from './TagForm.vue';

defineOptions({
  name: 'TagList',
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
    } = await queryTagPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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
  await deleteTag(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('tag.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :disabled="perm('tag:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('tag:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="tag" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="tag">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="name" :label="$t('tag.name')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="description" :label="$t('tag.description')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('tag.created')" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('tag.user')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="refers" :label="$t('tag.refers')" sortable="refers" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('tag:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('tag:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <tag-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

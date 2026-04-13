<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { moveList, toParams, resetParams } from '@/utils/common';
import { queryDictTypeList } from '@/api/config';
import { deleteDict, queryDictList, updateDictOrder } from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ListMove from '@/components/ListMove.vue';
import DictForm from './DictForm.vue';

defineOptions({
  name: 'DictList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const filtered = ref<boolean>(false);
const typeList = ref<any[]>([]);
const typeId = ref<string>();
const dictType = computed(() => typeList.value.find((item) => item.id === typeId.value));
const deletable = (bean: any) => bean.id >= 500;
const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryDictList({ ...toParams(params.value), typeId: typeId.value, Q_OrderBy: sort.value });
    filtered.value = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0 || sort.value !== undefined;
  } finally {
    loading.value = false;
  }
};
const fetchDictTypeList = async () => {
  typeList.value = await queryDictTypeList();
  typeId.value = String(typeList.value[0].id);
};
onMounted(async () => {
  await fetchDictTypeList();
  fetchData();
});

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
  await deleteDict(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const move = async (selected: any[], type: 'top' | 'up' | 'down' | 'bottom') => {
  const list = moveList(selected, data.value, type);
  await updateDictOrder(list.map((item) => item.id));
};
</script>

<template>
  <el-container>
    <el-aside width="180px" class="pr-3">
      <el-tabs v-model="typeId" tab-position="left" stretch class="bg-white" @tab-change="() => fetchData()">
        <el-tab-pane v-for="tp in typeList" :key="tp.id" :name="String(tp.id)" :label="tp.name"></el-tab-pane>
      </el-tabs>
    </el-aside>
    <el-main class="p-0">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('dict.name')" name="Q_Contains_name"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="perm('dict:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
          <template #reference>
            <el-button :disabled="selection.length <= 0 || perm('dict:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
          </template>
        </el-popconfirm>
        <list-move :disabled="selection.length <= 0 || filtered || perm('org:update')" class="ml-2" @move="(type) => move(selection, type)" />
        <column-setting name="dict" class="ml-2" />
      </div>
      <div class="mt-3 app-block">
        <el-table
          ref="table"
          v-loading="loading"
          :data="data"
          @selection-change="(rows: any) => (selection = rows)"
          @row-dblclick="(row: any) => handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="dict">
            <el-table-column type="selection" :selectable="deletable" width="45"></el-table-column>
            <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
            <el-table-column property="name" :label="$t('dict.name')" sortable="custom" show-overflow-tooltip></el-table-column>
            <el-table-column property="value" :label="$t('dict.value')" sortable="custom" show-overflow-tooltip></el-table-column>
            <el-table-column property="enabled" :label="$t('dict.enabled')" sortable="custom">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'yes' : 'no') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column property="sys" :label="$t('dict.sys')" sortable="custom">
              <template #default="{ row }">
                <el-tag :type="row.sys ? 'success' : 'info'" size="small">{{ $t(row.sys ? 'yes' : 'no') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('dict:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="!deletable(row) || perm('dict:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
      </div>
      <dict-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :type="dictType" @finished="fetchData" />
    </el-main>
  </el-container>
</template>

<style lang="scss" scoped>
.el-tabs {
  :deep(.el-tabs__header) {
    margin-right: 1px;
  }
  :deep(.el-tabs__content) {
    flex-grow: 0;
  }
}
</style>

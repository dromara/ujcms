<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { moveList, toParams, resetParams } from '@/utils/common';
import { deleteGroup, queryGroupList, updateGroupOrder } from '@/api/user';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ListMove from '@/components/ListMove.vue';
import GroupForm from './GroupForm.vue';
import GroupPermissionForm from './GroupPermissionForm.vue';

defineOptions({
  name: 'GroupList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const permissionFormVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const filtered = ref<boolean>(false);
const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryGroupList({ ...toParams(params.value), Q_OrderBy: sort.value });
    filtered.value = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0 || sort.value !== undefined;
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
const handlePermissionEdit = (id: string) => {
  beanId.value = id;
  permissionFormVisible.value = true;
};
const deletable = (id: string) => Number(id) > 10;
const handleDelete = async (ids: string[]) => {
  const deletableIds = ids.filter((id) => deletable(id));
  if (deletableIds.length > 0) {
    await deleteGroup(deletableIds);
    fetchData();
    ElMessage.success(t('success'));
  }
};

const move = async (selected: any[], type: 'top' | 'up' | 'down' | 'bottom') => {
  const list = moveList(selected, data.value, type);
  await updateGroupOrder(list.map((item: any) => item.id));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('group.name')" name="Q_Contains_name"></query-item>
        <query-item :label="$t('group.description')" name="Q_Contains_description"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :disabled="perm('group:create')" :icon="Plus" @click="handleAdd">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.filter((row) => deletable(row.id)).length <= 0 || perm('group:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <list-move class="ml-2" :disabled="selection.length <= 0 || filtered || perm('org:update')" @move="(type) => move(selection, type)" />
      <column-setting name="group" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="group">
          <el-table-column type="selection" width="50"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="name" :label="$t('group.name')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="description" :label="$t('group.description')" min-width="150" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="allAccessPermission" :label="$t('group.allAccessPermission')" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.allAccessPermission ? 'success' : 'info'" size="small">{{ $t(row.allAccessPermission ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="type" :label="$t('group.type')" sortable="custom" show-overflow-tooltip :formatter="(row) => $t(`group.type.${row.type}`)" />
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('group:update')" size="small" link @click="handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-button type="primary" :disabled="perm('group:updatePermission')" size="small" link @click="() => handlePermissionEdit(row.id)">
                {{ $t('permissionSettings') }}
              </el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="!deletable(row.id) || perm('group:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <group-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
    <group-permission-form v-model="permissionFormVisible" :bean-id="beanId" @finished="fetchData"></group-permission-form>
  </div>
</template>

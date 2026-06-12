<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm, currentUser } from '@/stores/useCurrentUser';
import { deleteRole, queryRoleList, updateRoleOrder } from '@/api/user';
import { moveList, toParams, resetParams } from '@/utils/common';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ListMove from '@/components/ListMove.vue';
import RoleForm from './RoleForm.vue';
import RolePermissionForm from './RolePermissionForm.vue';

defineOptions({
  name: 'RoleList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const permissionFormVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const filtered = ref<boolean>(false);
const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryRoleList({ ...toParams(params.value), Q_OrderBy: sort.value });
    filtered.value = Object.values(params.value).filter((v) => v !== undefined && v !== '').length > 0 || sort.value !== undefined;
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

const disabled = (bean: any): boolean => (bean.global && !currentUser.globalPermission) || currentUser.rank > bean.rank;

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
const handleDelete = async (ids: string[]) => {
  await deleteRole(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const move = async (selected: any[], type: 'top' | 'up' | 'down' | 'bottom') => {
  const list = moveList(selected, data.value, type);
  await updateRoleOrder(list.map((item) => item.id));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('role.name')" name="Q_Contains_name"></query-item>
        <query-item :label="$t('role.description')" name="Q_Contains_description"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <list-move class="ml-2" :disabled="selection.length <= 0 || filtered || perm('role:update')" @move="(type) => move(selection, type)" />
      <column-setting name="role" class="ml-2" />
    </div>
    <el-table
      ref="table"
      v-loading="loading"
      :data="data"
      class="mt-3 bg-white shadow"
      @selection-change="(rows) => (selection = rows)"
      @row-dblclick="(row) => handleEdit(row.id)"
      @sort-change="handleSort"
    >
      <column-list name="role">
        <el-table-column type="selection" :selectable="(bean) => !disabled(bean)" width="50"></el-table-column>
        <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
        <el-table-column property="name" :label="$t('role.name')" sortable="custom" show-overflow-tooltip></el-table-column>
        <el-table-column property="description" :label="$t('role.description')" sortable="custom" show-overflow-tooltip></el-table-column>
        <el-table-column property="rank" :label="$t('role.rank')" sortable="custom" show-overflow-tooltip></el-table-column>
        <el-table-column property="globalPermission" :label="$t('role.globalPermission')" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.globalPermission ? 'success' : 'info'" size="small">{{ $t(row.globalPermission ? 'yes' : 'no') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="type" :label="$t('role.type')" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.type === 1" size="small">{{ $t(`role.type.${row.type}`) }}</el-tag>
            <el-tag v-else-if="row.type === 2" type="warning" size="small">{{ $t(`role.type.${row.type}`) }}</el-tag>
            <el-tag v-else-if="row.type === 3" type="success" size="small">{{ $t(`role.type.${row.type}`) }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t(`role.type.${row.type}`) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="scope" :label="$t('role.scope')" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.scope === 2" type="success" size="small">{{ $t(`role.scope.${row.scope}`) }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t(`role.scope.${row.scope}`) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" width="180">
          <template #default="{ row }">
            <el-button type="primary" :disabled="perm('role:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
            <el-button type="primary" :disabled="perm('role:updatePermission')" size="small" link @click="() => handlePermissionEdit(row.id)">
              {{ $t('permissionSettings') }}
            </el-button>
            <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
              <template #reference>
                <el-button type="primary" size="small" :disabled="disabled(row)" link>{{ $t('delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </column-list>
    </el-table>
    <role-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
    <role-permission-form v-model="permissionFormVisible" :bean-id="beanId" @finished="fetchData"></role-permission-form>
  </div>
</template>

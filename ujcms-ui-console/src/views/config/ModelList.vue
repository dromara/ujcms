<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { moveList, toParams, resetParams } from '@/utils/common';
import { deleteModel, queryModelList, updateModelOrder } from '@/api/config';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import ListMove from '@/components/ListMove.vue';
import ModelForm from './ModelForm.vue';
import ModelSystemFields from './ModelSystemFields.vue';
import ModelCustomFields from './ModelCustomFields.vue';

defineOptions({
  name: 'ModelList',
});
const { t } = useI18n();
const params = ref<any>({});
const modelType = ref<string>('article');
const sort = ref<any>();
const table = ref<any>();
const data = ref<Array<any>>([]);
const selection = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const systemFieldsVisible = ref<boolean>(false);
const customFieldsVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const filtered = ref<boolean>(false);
const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryModelList({ ...toParams(params.value), type: modelType.value, Q_OrderBy: sort.value });
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

const handleSystemFields = (id: string) => {
  beanId.value = id;
  systemFieldsVisible.value = true;
};
const handleCustomFields = (id: string) => {
  beanId.value = id;
  customFieldsVisible.value = true;
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
  await deleteModel(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const move = async (selected: any[], type: 'top' | 'up' | 'down' | 'bottom') => {
  const list = moveList(selected, data.value, type);
  await updateModelOrder(list.map((item) => item.id));
};
const deletable = (bean: any) => bean.id > 10;
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('model.name')" name="Q_Contains_name"></query-item>
      </query-form>
    </div>
    <div>
      <el-button type="primary" :disabled="perm('model:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('model:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <list-move :disabled="selection.length <= 0 || filtered || perm('org:update')" class="ml-2" @move="(type) => move(selection, type)" />
      <column-setting name="model" class="ml-2" />
    </div>
    <el-radio-group v-model="modelType" class="mt-3" @change="() => fetchData()">
      <!-- ['article', 'channel', 'user', 'site', 'global'] -->
      <el-radio-button v-for="n in currentUser.epRank >= 3 ? ['article', 'channel', 'form', 'site', 'global'] : ['article', 'channel', 'site', 'global']" :key="n" :value="n">{{ $t(`model.type.${n}`) }}</el-radio-button>
    </el-radio-group>
    <div class="app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="model">
          <el-table-column type="selection" :selectable="deletable" width="45"></el-table-column>
          <el-table-column property="id" label="ID" width="170" sortable="custom"></el-table-column>
          <el-table-column property="name" :label="$t('model.name')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="type" :label="$t('model.type')" sortable="custom" :formatter="(row) => $t(`model.type.${row.type}`)" />
          <el-table-column property="scope" :label="$t('model.scope')" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.scope === 2" type="success" size="small">{{ $t(`model.scope.${row.scope}`) }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $t(`model.scope.${row.scope}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('model:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-button
                v-if="!['form', 'global', 'site'].includes(row.type)"
                type="primary"
                :disabled="perm('model:update')"
                size="small"
                link
                @click="() => handleSystemFields(row.id)"
              >
                {{ $t('model.fun.systemFields') }}
              </el-button>
              <el-button type="primary" :disabled="perm('model:update')" size="small" link @click="() => handleCustomFields(row.id)">
                {{ $t('model.fun.customFields') }}
              </el-button>
              <el-popconfirm v-if="deletable(row)" :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('model:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <model-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :model-type="modelType" @finished="fetchData" />
    <model-system-fields v-model="systemFieldsVisible" :bean-id="beanId" />
    <model-custom-fields v-model="customFieldsVisible" :bean-id="beanId" />
  </div>
</template>

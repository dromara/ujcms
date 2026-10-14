<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { deployProcessModel, deleteProcessModel, queryProcessModelList } from '@/api/system';
import { queryDictListByAlias } from '@/api/config';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import ProcessModelForm from './ProcessModelForm.vue';
import ProcessModelDesign from './ProcessModelDesign.vue';
import ProcessDefinitionList from './ProcessDefinitionList.vue';

defineOptions({
  name: 'ProcessModelList',
});
const { t } = useI18n();
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const designFormVisible = ref<boolean>(false);
const definitionListVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanKey = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const categoryList = ref<any[]>([]);
const fetchCategoryList = async () => {
  categoryList.value = await queryDictListByAlias('sys_process_category');
};
const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryProcessModelList();
  } finally {
    loading.value = false;
  }
};
onMounted(() => {
  fetchData();
  fetchCategoryList();
});

const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDesign = (id: string) => {
  beanId.value = id;
  designFormVisible.value = true;
};
const handleDefinition = (key: string) => {
  beanKey.value = key;
  definitionListVisible.value = true;
};
const handleDeploy = async (id: string, key: string) => {
  await deployProcessModel(id);
  handleDefinition(key);
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteProcessModel(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div>
      <el-button type="primary" :disabled="perm('processModel:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('processModel:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="processModel" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)">
        <column-list name="processModel">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="310" display="none" show-overflow-tooltip></el-table-column>
          <el-table-column property="name" :label="$t('processModel.name')"></el-table-column>
          <el-table-column property="key" :label="$t('processModel.key')" show-overflow-tooltip></el-table-column>
          <el-table-column property="category" :label="$t('processModel.category')">
            <template #default="{ row }">{{ categoryList.find((item) => item.value === row.category)?.name }}</template>
          </el-table-column>
          <el-table-column property="createTime" :label="$t('processModel.createTime')" width="170">
            <template #default="{ row }">{{ dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="lastUpdateTime" :label="$t('processModel.lastUpdateTime')" width="170">
            <template #default="{ row }">{{ dayjs(row.lastUpdateTime).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="280">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('processModel:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-button type="primary" :disabled="perm('processModel:update')" size="small" link @click="() => handleDesign(row.id)">{{ $t('processModel.op.design') }}</el-button>
              <el-button type="primary" :disabled="perm('processDefinition:list')" size="small" link @click="() => handleDefinition(row.key)">{{
                $t('processModel.op.definition')
              }}</el-button>
              <el-button type="primary" :disabled="perm('processModel:deploy')" size="small" link @click="() => handleDeploy(row.id, row.key)">{{
                $t('processModel.op.deploy')
              }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('processModel:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <process-model-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
    <process-model-design v-model="designFormVisible" :bean-id="beanId" @finished="fetchData" />
    <process-definition-list v-model="definitionListVisible" :bean-key="beanKey" />
  </div>
</template>

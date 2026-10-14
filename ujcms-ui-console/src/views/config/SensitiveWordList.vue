<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, Upload, Download } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { saveAs } from 'file-saver';
import { perm, currentUser } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteSensitiveWord, exportSensitiveWord, querySensitiveWordPage, updateSensitiveWord } from '@/api/system';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import SensitiveWordForm from './SensitiveWordForm.vue';
import WordImportDialog from './WordImportDialog.vue';
import { toTextBlob } from '@/utils/textFile';
import { serializeWords } from './wordFile';

defineOptions({
  name: 'SensitiveWordList',
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
const exporting = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const importVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
// 词库各站点共用（全局数据）：没有全局数据权限时只读
const readonly = computed(() => !currentUser.globalPermission);
// 查询条件为空时的空表才提示“去导入”；带条件查不到是另一回事
const filtered = computed(() => Object.values(toParams(params.value)).some((value) => value != null && value !== ''));
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await querySensitiveWordPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

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
  await deleteSensitiveWord(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleEnabled = async (row: any, enabled: boolean) => {
  try {
    await updateSensitiveWord({ ...row, enabled });
    row.enabled = enabled;
    ElMessage.success(t('success'));
  } catch {
    await fetchData();
  }
};
// 导出当前查询条件下的全部词条（不止当前页）
const handleExport = async () => {
  exporting.value = true;
  try {
    const rows = await exportSensitiveWord({ ...toParams(params.value), Q_OrderBy: sort.value });
    if (rows.length === 0) {
      ElMessage.warning(t('wordDict.export.empty'));
      return;
    }
    saveAs(toTextBlob(serializeWords(rows, 'sensitive')), 'sensitive-words.txt');
    ElMessage.success(t('wordDict.export.result', { count: rows.length }));
  } finally {
    exporting.value = false;
  }
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="() => handleReset()">
        <query-item :label="$t('sensitiveWord.name')" name="Q_Contains_name"></query-item>
        <query-item
          :label="$t('sensitiveWord.enabled')"
          name="Q_EQ_enabled_Boolean"
          :options="[
            { label: $t('yes'), value: 'true' },
            { label: $t('no'), value: 'false' },
          ]"
          :multiple="false"
        ></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="flex items-center">
      <el-button type="primary" :disabled="readonly || perm('sensitiveWord:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-button :disabled="readonly || perm('sensitiveWord:create')" :icon="Upload" @click="() => (importVisible = true)">{{ $t('wordDict.op.import') }}</el-button>
      <el-button :disabled="total <= 0" :loading="exporting" :icon="Download" @click="() => handleExport()">{{ $t('wordDict.op.export') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || readonly || perm('sensitiveWord:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <el-tag v-if="readonly" type="info" class="ml-3" disable-transitions>{{ $t('wordDict.readonly') }}</el-tag>
      <column-setting name="sensitiveWord" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="sensitiveWord">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="name" :label="$t('sensitiveWord.name')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="enabled" :label="$t('sensitiveWord.enabled')" sortable="custom" width="120">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" :disabled="readonly || perm('sensitiveWord:update')" @change="(value: any) => handleEnabled(row, value === true)" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="140">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('sensitiveWord:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t(readonly ? 'detail' : 'edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="readonly || perm('sensitiveWord:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
        <!-- 词库为空时指路“导入”；带查询条件查不到时不渲染内容，回落为表格默认的“暂无数据” -->
        <template #empty>
          <span v-if="!loading && !filtered">{{ $t('sensitiveWord.empty') }}</span>
        </template>
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
    <sensitive-word-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :readonly="readonly" @finished="fetchData" />
    <word-import-dialog v-model="importVisible" mode="sensitive" @finished="fetchData" />
  </div>
</template>

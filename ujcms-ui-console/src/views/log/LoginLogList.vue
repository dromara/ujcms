<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Delete, CircleCheck, WarningFilled, QuestionFilled } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { deleteLoginLog, queryLoginLogPage, verifyLoginLog } from '@/api/log';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import LoginLogForm from './LoginLogForm.vue';

defineOptions({
  name: 'LoginLogList',
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
const tamperedIds = ref<Set<string>>(new Set());
const unsignedIds = ref<Set<string>>(new Set());
const verified = ref<boolean>(false);
const truncated = ref<boolean>(false);
const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryLoginLogPage({ ...toParams(params.value), Q_OrderBy: sort.value, page: currentPage.value, pageSize: pageSize.value });
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
const resetVerification = () => {
  verified.value = false;
  truncated.value = false;
  tamperedIds.value = new Set();
  unsignedIds.value = new Set();
};
const handleSearch = () => {
  resetVerification();
  fetchData();
};
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  resetVerification();
  fetchData();
};

const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteLoginLog(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleVerify = async () => {
  loading.value = true;
  try {
    const result = await verifyLoginLog(toParams(params.value));
    tamperedIds.value = new Set((result.tamperedIds ?? []).map(String));
    unsignedIds.value = new Set((result.unsignedIds ?? []).map(String));
    truncated.value = !!result.truncated;
    verified.value = true;
    const lines = [t('log.verify.summary', { total: result.total, tampered: result.tampered, unsigned: result.unsigned })];
    if (result.truncated) lines.push(t('log.verify.truncated'));
    ElMessageBox.alert(lines.join('<br />'), t('log.verify.title'), {
      type: result.tampered > 0 ? 'error' : result.unsigned > 0 ? 'warning' : 'success',
      dangerouslyUseHTMLString: true,
    });
  } finally {
    loading.value = false;
  }
};
type Integrity = 'none' | 'tampered' | 'unsigned' | 'intact' | 'unknown';
const integrityOf = (row: any): Integrity => {
  if (!verified.value) return 'none';
  const id = String(row.id);
  if (tamperedIds.value.has(id)) return 'tampered';
  if (unsignedIds.value.has(id)) return 'unsigned';
  // 未达扫描上限时，本次过滤范围内的行均已校验，未命中即为完整；
  // 达到上限（truncated）时无法确定该行是否被扫描到，标记为“未校验”。
  return truncated.value ? 'unknown' : 'intact';
};
const rowClassName = ({ row }: { row: any }) => (integrityOf(row) === 'tampered' ? 'audit-tampered-row' : '');
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('loginLog.loginName')" name="Q_Like_loginName"></query-item>
        <query-item :label="$t('loginLog.user')" name="Q_Like_user-username"></query-item>
        <query-item :label="$t('loginLog.ip')" name="Q_Contains_ip"></query-item>
        <query-item :label="$t('loginLog.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
        <query-item :label="$t('loginLog.type')" name="Q_In_type_Int" :options="[1, 2, 9].map((item) => ({ label: $t(`loginLog.type.${item}`), value: item }))"></query-item>
        <query-item
          :label="$t('loginLog.status')"
          name="Q_In_status_Int"
          :options="[0, 1, 2, 3, 4, 5, 11, 12, 13, 14, 15, 16, 17].map((item) => ({ label: $t(`loginLog.status.${item}`), value: item }))"
        ></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('loginLog:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <el-button class="ml-2" :disabled="perm('loginLog:show')" @click="() => handleVerify()">{{ $t('log.verify') }}</el-button>
      <column-setting name="loginLog" class="ml-2" />
    </div>
    <div class="mt-3 app-block">
      <el-table
        ref="table"
        v-loading="loading"
        :data="data"
        :row-class-name="rowClassName"
        @selection-change="(rows) => (selection = rows)"
        @row-dblclick="(row) => handleEdit(row.id)"
        @sort-change="handleSort"
      >
        <column-list name="loginLog">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="ip" :label="$t('loginLog.ip')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('loginLog.created')" sortable="custom" width="170">
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column property="user.username" :label="$t('loginLog.user')" sort-by="user-username" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="loginName" :label="$t('loginLog.loginName')" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="type" :label="$t('loginLog.type')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag v-if="[1, 2].includes(row.type)" size="small" disable-transitions>{{ $t(`loginLog.type.${row.type}`) }}</el-tag>
              <el-tag v-else-if="row.type != null" type="info" size="small" disable-transitions>{{ $t(`loginLog.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="status" :label="$t('loginLog.status')" sortable="custom" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="success" size="small" disable-transitions>{{ $t(`loginLog.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="[1, 2].includes(row.status)" type="danger" size="small" disable-transitions>{{ $t(`loginLog.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="[3, 4, 5].includes(row.status)" type="warning" size="small" disable-transitions>{{ $t(`loginLog.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status != null" type="info" size="small" disable-transitions>{{ $t(`loginLog.status.${row.status}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('log.verify.integrity')" width="100">
            <template #default="{ row }">
              <el-tag v-if="integrityOf(row) === 'tampered'" type="danger" size="small" disable-transitions>
                <span class="inline-flex items-center"><el-icon class="mr-1"><WarningFilled /></el-icon>{{ $t('log.verify.tampered') }}</span>
              </el-tag>
              <el-tag v-else-if="integrityOf(row) === 'unsigned'" type="info" size="small" disable-transitions>
                <span class="inline-flex items-center"><el-icon class="mr-1"><QuestionFilled /></el-icon>{{ $t('log.verify.unsigned') }}</span>
              </el-tag>
              <el-tag v-else-if="integrityOf(row) === 'intact'" type="success" size="small" disable-transitions>
                <span class="inline-flex items-center"><el-icon class="mr-1"><CircleCheck /></el-icon>{{ $t('log.verify.intact') }}</span>
              </el-tag>
              <el-tooltip v-else :content="integrityOf(row) === 'unknown' ? $t('log.verify.outOfRange') : $t('log.verify.uncheckedHint')">
                <span class="audit-muted">—</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('loginLog:show')" size="small" link @click="() => handleEdit(row.id)">{{ $t('view') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('loginLog:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <login-log-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
  </div>
</template>

<style lang="scss" scoped>
:deep(.audit-tampered-row) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9);
}
.audit-muted {
  color: var(--el-text-color-placeholder);
}
</style>

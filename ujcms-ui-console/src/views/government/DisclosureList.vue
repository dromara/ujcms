<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, ArrowDown } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, numberParam } from '@/composables/useRouteState';
import { deleteDisclosure, queryDisclosurePage, acceptDisclosure, extendDisclosure, correctReceivedDisclosure } from '@/api/interaction';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import DisclosureForm from './DisclosureForm.vue';
import DisclosureProcessForm from './DisclosureProcessForm.vue';

defineOptions({
  name: 'DisclosureList',
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
const processVisible = ref<boolean>(false);
const beanId = ref<string>();
const processBeanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));

// 导航上下文（状态行）存于路由 query：刷新、页签往返后恢复；亦供首页待办等深链（?status=）
const { status } = useRouteState({ status: numberParam(-1, [0, 1, 2, 3]) });

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryDisclosurePage({
      ...toParams(params.value),
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      Q_OrderBy: sort.value,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

// 计算工作日天数（不含节假日，仅排除周末，作为近似提示）
const workingDays = (from: string, to?: string): number => {
  let day = dayjs(from).startOf('day');
  const end = dayjs(to ?? undefined).startOf('day');
  let days = 0;
  while (day.isBefore(end)) {
    day = day.add(1, 'day');
    const weekday = day.day();
    if (weekday !== 0 && weekday !== 6) {
      days += 1;
    }
  }
  return days;
};
const elapsedDays = (row: any): number => workingDays(row.acceptDate ?? row.created, row.status === 3 ? row.replyDate : undefined);
const limitDays = (row: any): number => (row.extended ? 40 : 20);

const handleSort = ({ column, prop, order }: { column: any; prop: string | null; order: string | null }) => {
  if (prop && order) {
    sort.value = (column.sortBy ?? prop) + (order === 'descending' ? '_desc' : '');
  } else {
    sort.value = undefined;
  }
  fetchData();
};
const handleSearch = () => fetchData();
// 切换状态行：回到第一页
const handleStatusChange = () => {
  currentPage.value = 1;
  fetchData();
};
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
const handleProcess = (id: string) => {
  processBeanId.value = id;
  processVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteDisclosure(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleAccept = async (ids: string[]) => {
  await acceptDisclosure(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleExtend = async (ids: string[]) => {
  await extendDisclosure(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleCorrectReceived = async (ids: string[]) => {
  await correctReceivedDisclosure(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('disclosure.title')" name="Q_Contains_title"></query-item>
        <query-item :label="$t('disclosure.name')" name="Q_Contains_name"></query-item>
        <query-item :label="$t('disclosure.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('disclosure:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-dropdown :disabled="selection.length <= 0 || (perm('disclosure:accept') && perm('disclosure:extend') && perm('disclosure:correctReceived'))">
        <el-button :disabled="selection.length <= 0 || (perm('disclosure:accept') && perm('disclosure:extend') && perm('disclosure:correctReceived'))">
          {{ $t('disclosure.op.process') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :disabled="perm('disclosure:accept')" @click="() => handleAccept(selection.map((row) => row.id))">
              {{ $t('disclosure.op.accept') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('disclosure:extend')" @click="() => handleExtend(selection.map((row) => row.id))">
              {{ $t('disclosure.op.extend') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('disclosure:correctReceived')" @click="() => handleCorrectReceived(selection.map((row) => row.id))">
              {{ $t('disclosure.op.correctReceived') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('disclosure:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="disclosure" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="status" class="block-tabs" @tab-change="handleStatusChange">
        <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
        <el-tab-pane v-for="s in [0, 1, 2, 3]" :key="s" :name="s" :label="$t(`disclosure.status.${s}`)"></el-tab-pane>
      </el-tabs>
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="disclosure">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="title" :label="$t('disclosure.title')" min-width="220" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="type" :label="$t('disclosure.type')" min-width="120" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.type === 1 ? 'info' : 'warning'" size="small" disable-transitions>{{ $t(`disclosure.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="name" :label="$t('disclosure.name')" min-width="140" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('disclosure.created')" min-width="150" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="acceptDate" :label="$t('disclosure.acceptDate')" min-width="110" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ row.acceptDate ? dayjs(row.acceptDate).format('YYYY-MM-DD') : '' }}</template>
          </el-table-column>
          <el-table-column :label="$t('disclosure.elapsedDays')" min-width="100">
            <template #default="{ row }">
              <!-- 已补正告知(状态2)期间时限暂停，收到补正后重新起算，不显示超期提示 -->
              <span v-if="(row.status === 0 || row.status === 1) && elapsedDays(row) > limitDays(row)" class="font-bold text-red-500">
                {{ elapsedDays(row) }} {{ $t('disclosure.overdue') }}
              </span>
              <span v-else-if="(row.status === 0 || row.status === 1) && elapsedDays(row) >= limitDays(row) - 5" class="text-orange-500">
                {{ elapsedDays(row) }} {{ $t('disclosure.nearDue') }}
              </span>
              <span v-else>{{ elapsedDays(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column property="extended" :label="$t('disclosure.extended')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.extended ? 'warning' : 'info'" size="small" disable-transitions>{{ $t(row.extended ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="status" :label="$t('disclosure.status')" min-width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="warning" size="small" disable-transitions>{{ $t(`disclosure.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="primary" size="small" disable-transitions>{{ $t(`disclosure.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t(`disclosure.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 3" type="success" size="small" disable-transitions>{{ $t(`disclosure.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="replyType" :label="$t('disclosure.replyType')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <span v-if="row.replyType != null">{{ $t(`disclosure.replyType.${row.replyType}`) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="140">
            <template #default="{ row }">
              <el-button
                v-if="row.status < 3"
                type="primary"
                :disabled="perm('disclosure:accept') && perm('disclosure:correctReceived') && perm('disclosure:correct') && perm('disclosure:reply')"
                size="small"
                link
                @click="() => handleProcess(row.id)"
              >
                {{ $t('disclosure.op.process') }}
              </el-button>
              <el-button type="primary" :disabled="perm('disclosure:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('disclosure:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <disclosure-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" @finished="fetchData" />
    <disclosure-process-form v-model="processVisible" :bean-id="processBeanId" @finished="fetchData" />
  </div>
</template>

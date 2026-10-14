<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Plus, Delete, ArrowDown, Setting } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { useRouteState, numberParam } from '@/composables/useRouteState';
import { deleteMailbox, queryMailboxPage, acceptMailbox, extendMailbox, publishMailbox, unpublishMailbox } from '@/api/interaction';
import { queryMailboxTypeList, updateMailboxTypeOrder, deleteMailboxType } from '@/api/config';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import TypeManagerDialog from '@/components/TypeManagerDialog.vue';
import MailboxForm from './MailboxForm.vue';
import MailboxTypeForm from './MailboxTypeForm.vue';
import MailboxProcessForm from './MailboxProcessForm.vue';

defineOptions({
  name: 'MailboxList',
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
// 信箱类型（docs/spec/console-master-detail R3）：只有“名称 + 描述”的可选分类，经工具栏的“类型管理”就地维护；
// 信件按办理状态跨类型处理，许多站点根本不设类型，故不占用左侧栏
const mailboxTypeList = ref<any[]>([]);
const typeManagerVisible = ref<boolean>(false);
const typeManageable = computed(() => !perm('mailboxType:create') || !perm('mailboxType:update') || !perm('mailboxType:delete'));
const fetchMailboxTypeList = async () => {
  mailboxTypeList.value = await queryMailboxTypeList();
};

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryMailboxPage({
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
onMounted(() => {
  fetchData();
  fetchMailboxTypeList();
});

// 办理期限从站点设置读取（站点设置保存后会刷新 currentSite），默认 7/15 个工作日
const currentSiteStore = useCurrentSiteStore();
const handlingDays = computed<number>(() => currentSiteStore.currentSite?.mailbox?.handlingDays ?? 7);
const extendedDays = computed<number>(() => currentSiteStore.currentSite?.mailbox?.extendedDays ?? 15);

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
const limitDays = (row: any): number => (row.extended ? extendedDays.value : handlingDays.value);

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
  await deleteMailbox(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleAccept = async (ids: string[]) => {
  await acceptMailbox(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleExtend = async (ids: string[]) => {
  await extendMailbox(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handlePublish = async (ids: string[]) => {
  await publishMailbox(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleUnpublish = async (ids: string[]) => {
  await unpublishMailbox(ids);
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="mb-3">
      <query-form :params="params" @search="handleSearch" @reset="handleReset">
        <query-item :label="$t('mailbox.title')" name="Q_Contains_title"></query-item>
        <query-item :label="$t('mailbox.name')" name="Q_Contains_name"></query-item>
        <query-item
          v-if="mailboxTypeList.length > 0"
          :label="$t('mailbox.mailboxType')"
          name="Q_EQ_mailboxTypeId_Long"
          :options="mailboxTypeList.map((item) => ({ label: item.name, value: item.id }))"
        ></query-item>
        <query-item :label="$t('mailbox.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
        <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
      </query-form>
    </div>
    <div class="space-x-2">
      <el-button type="primary" :disabled="perm('mailbox:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-dropdown :disabled="selection.length <= 0 || (perm('mailbox:accept') && perm('mailbox:extend') && perm('mailbox:publish'))">
        <el-button :disabled="selection.length <= 0 || (perm('mailbox:accept') && perm('mailbox:extend') && perm('mailbox:publish'))">
          {{ $t('mailbox.op.process') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :disabled="perm('mailbox:accept')" @click="() => handleAccept(selection.map((row) => row.id))">
              {{ $t('mailbox.op.accept') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('mailbox:extend')" @click="() => handleExtend(selection.map((row) => row.id))">
              {{ $t('mailbox.op.extend') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('mailbox:publish')" :title="$t('mailbox.op.publish.tooltip')" @click="() => handlePublish(selection.map((row) => row.id))">
              {{ $t('mailbox.op.publish') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('mailbox:publish')" @click="() => handleUnpublish(selection.map((row) => row.id))">
              {{ $t('mailbox.op.unpublish') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('mailbox:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <el-button :disabled="!typeManageable" :icon="Setting" @click="() => (typeManagerVisible = true)">{{ $t('mailboxType.manage') }}</el-button>
      <column-setting name="mailbox" />
    </div>
    <div class="mt-3 app-block">
      <el-tabs v-model="status" class="block-tabs" @tab-change="handleStatusChange">
        <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
        <el-tab-pane v-for="s in [0, 1, 2, 3]" :key="s" :name="s" :label="$t(`mailbox.status.${s}`)"></el-tab-pane>
      </el-tabs>
      <el-table ref="table" v-loading="loading" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)" @sort-change="handleSort">
        <column-list name="mailbox">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
          <el-table-column property="title" :label="$t('mailbox.title')" min-width="220" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="type" :label="$t('mailbox.type')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag type="info" size="small" disable-transitions>{{ $t(`mailbox.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="mailboxTypeList.length > 0" property="mailboxType.name" :label="$t('mailbox.mailboxType')" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.mailboxType?.name ?? $t('mailbox.mailboxType.unclassified') }}</template>
          </el-table-column>
          <el-table-column property="name" :label="$t('mailbox.name')" min-width="120" sortable="custom" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('mailbox.created')" min-width="150" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
          </el-table-column>
          <el-table-column property="acceptDate" :label="$t('mailbox.acceptDate')" min-width="110" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">{{ row.acceptDate ? dayjs(row.acceptDate).format('YYYY-MM-DD') : '' }}</template>
          </el-table-column>
          <el-table-column :label="$t('mailbox.elapsedDays')" min-width="100">
            <template #default="{ row }">
              <!-- 已退回(状态2)不再办理，时限停止计算，不显示超期提示 -->
              <span v-if="(row.status === 0 || row.status === 1) && elapsedDays(row) > limitDays(row)" class="font-bold text-red-500">
                {{ elapsedDays(row) }} {{ $t('mailbox.overdue') }}
              </span>
              <span v-else-if="(row.status === 0 || row.status === 1) && elapsedDays(row) >= limitDays(row) - 2" class="text-orange-500">
                {{ elapsedDays(row) }} {{ $t('mailbox.nearDue') }}
              </span>
              <span v-else>{{ elapsedDays(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column property="extended" :label="$t('mailbox.extended')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.extended ? 'warning' : 'info'" size="small" disable-transitions>{{ $t(row.extended ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="status" :label="$t('mailbox.status')" min-width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="warning" size="small" disable-transitions>{{ $t(`mailbox.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 1" type="primary" size="small" disable-transitions>{{ $t(`mailbox.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 2" type="danger" size="small" disable-transitions>{{ $t(`mailbox.status.${row.status}`) }}</el-tag>
              <el-tag v-else-if="row.status === 3" type="success" size="small" disable-transitions>{{ $t(`mailbox.status.${row.status}`) }}</el-tag>
              <el-tag v-else type="info" size="small" disable-transitions>unknown</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="published" :label="$t('mailbox.published')" min-width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.published ? 'success' : 'info'" size="small" disable-transitions>{{ $t(row.published ? 'yes' : 'no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="rate" :label="$t('mailbox.rate')" min-width="100" sortable="custom">
            <template #default="{ row }">
              <el-tag v-if="row.rate === 1" type="success" size="small" disable-transitions>{{ $t(`mailbox.rate.${row.rate}`) }}</el-tag>
              <el-tag v-else-if="row.rate === 2" type="primary" size="small" disable-transitions>{{ $t(`mailbox.rate.${row.rate}`) }}</el-tag>
              <el-tag v-else-if="row.rate === 3" type="danger" size="small" disable-transitions>{{ $t(`mailbox.rate.${row.rate}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="140">
            <template #default="{ row }">
              <el-button
                v-if="row.status < 3"
                type="primary"
                :disabled="perm('mailbox:accept') && perm('mailbox:reject') && perm('mailbox:reply')"
                size="small"
                link
                @click="() => handleProcess(row.id)"
              >
                {{ $t('mailbox.op.process') }}
              </el-button>
              <el-button type="primary" :disabled="perm('mailbox:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('mailbox:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <mailbox-form v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :types="mailboxTypeList" @finished="fetchData" />
    <!-- 关闭后类型可能已增删改、换序：重取类型（查询项、类型列、表单下拉）与列表（行内的类型名；删除类型会把信件改为未分类） -->
    <type-manager-dialog
      v-model="typeManagerVisible"
      :title="$t('mailboxType.manage')"
      perms="mailboxType"
      :name-label="$t('mailboxType.name')"
      :description-label="$t('mailboxType.description')"
      :query-list="queryMailboxTypeList"
      :move-order="updateMailboxTypeOrder"
      :delete-beans="deleteMailboxType"
      @closed="
        () => {
          fetchMailboxTypeList();
          fetchData();
        }
      "
    >
      <template #form="{ visible, setVisible, beanId: typeBeanId, beanIds: typeBeanIds, finished }">
        <mailbox-type-form :model-value="visible" :bean-id="typeBeanId" :bean-ids="typeBeanIds" @update:model-value="setVisible" @finished="finished" />
      </template>
    </type-manager-dialog>
    <mailbox-process-form v-model="processVisible" :bean-id="processBeanId" @finished="fetchData" />
  </div>
</template>

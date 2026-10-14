<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Delete, Promotion } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout } from '@/utils/common';
import { deleteWechatMass, queryWechatAccountList, queryWechatMassPage, queryWechatMassQuota, refreshWechatMass, revokeWechatMass } from '@/api/wechat';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import WechatMassForm from './WechatMassForm.vue';

defineOptions({
  name: 'WechatMassList',
});
const { t } = useI18n();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const accountList = ref<any[]>([]);
const accountId = ref<string>();
const status = ref<number>(-1);
const quota = ref<any>(null);
const formVisible = ref<boolean>(false);

const fetchData = async () => {
  loading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryWechatMassPage({
      Q_EQ_accountId_Long: accountId.value,
      Q_EQ_status_Short: status.value !== -1 ? status.value : undefined,
      page: currentPage.value,
      pageSize: pageSize.value,
    });
    data.value = content;
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
const fetchQuota = async () => {
  quota.value = accountId.value ? await queryWechatMassQuota(accountId.value) : null;
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
};
onMounted(() => {
  fetchData();
  fetchAccountList();
});

const handleAccountChange = () => {
  fetchData();
  fetchQuota();
};
// 额度为本地估算：在公众平台官网的群发同样占用额度，本系统无法感知，以微信 45028 错误为准
const quotaText = computed<string>(() => {
  if (quota.value == null) {
    return '';
  }
  const key = quota.value.type === 1 ? 'wechatMass.quota.service' : 'wechatMass.quota.subscription';
  return t(key, { used: quota.value.used, limit: quota.value.limit });
});

const handleAdd = () => {
  formVisible.value = true;
};
const handleFinished = () => {
  fetchData();
  fetchQuota();
};
const handleRefresh = async (ids: string[]) => {
  await refreshWechatMass(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const handleRevoke = async (row: any) => {
  await revokeWechatMass({ id: row.id });
  fetchData();
  ElMessage.success(t('success'));
};
const handleDelete = async (ids: string[]) => {
  await deleteWechatMass(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const accountName = (id: string): string => accountList.value.find((item) => item.id === id)?.name ?? '';
const statusType = (value: number): 'info' | 'success' | 'danger' | 'warning' => (['info', 'success', 'danger', 'warning'] as const)[value] ?? 'info';
const revocable = (row: any): boolean => row.msgType === 'mpnews' && row.msgId != null && (row.status === 0 || row.status === 1);
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" clearable :placeholder="$t('wechatMass.account')" class="w-48" @change="() => handleAccountChange()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-select v-model="status" class="w-36" @change="() => fetchData()">
        <el-option :value="-1" :label="$t('wechatMass.status.all')"></el-option>
        <el-option v-for="n in [0, 1, 2, 3]" :key="n" :value="n" :label="$t(`wechatMass.status.${n}`)"></el-option>
      </el-select>
      <el-button type="primary" :disabled="accountList.length <= 0 || perm('wechatMass:send')" :icon="Promotion" @click="() => handleAdd()">
        {{ $t('wechatMass.op.send') }}
      </el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('wechatMass:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="wechatMass" />
    </div>
    <el-alert v-if="quotaText" :title="quotaText" :description="$t('wechatMass.quota.tip')" type="info" :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-table id="dataTable" ref="table" v-loading="loading" row-key="id" :data="data" @selection-change="(rows) => (selection = rows)">
        <column-list name="wechatMass">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="accountId" :label="$t('wechatMass.account')" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column property="target" :label="$t('wechatMass.target')" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.target === 1">{{ $t('wechatMass.target.1') }}{{ $t('punct.colon') }}{{ row.tagName ?? row.tagId }}</template>
              <template v-else>{{ $t('wechatMass.target.0') }}</template>
            </template>
          </el-table-column>
          <el-table-column property="msgType" :label="$t('wechatMass.msgType')" width="90">
            <template #default="{ row }">
              <el-tag :type="row.msgType === 'mpnews' ? 'primary' : 'success'" size="small">{{ $t(`wechatMass.msgType.${row.msgType}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('wechatMass.content')" :min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.msgType === 'mpnews' ? row.title : row.content }}</template>
          </el-table-column>
          <el-table-column property="status" :label="$t('wechatMass.status')" width="100">
            <template #default="{ row }">
              <el-tooltip :content="row.error" :disabled="!row.error" placement="top">
                <el-tag :type="statusType(row.status)" size="small">{{ $t(`wechatMass.status.${row.status}`) }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column :label="$t('wechatMass.sentCount')" width="120">
            <template #default="{ row }">
              <template v-if="row.sentCount != null">{{ row.sentCount }} / {{ (row.sentCount ?? 0) + (row.errorCount ?? 0) }}</template>
            </template>
          </el-table-column>
          <el-table-column property="created" :label="$t('wechatMass.created')" width="170">
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="170">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 0 && row.msgId != null"
                type="primary"
                :disabled="perm('wechatMass:refresh')"
                size="small"
                link
                @click="() => handleRefresh([row.id])"
              >
                {{ $t('wechatMass.op.refresh') }}
              </el-button>
              <el-popconfirm v-if="revocable(row)" :title="$t('wechatMass.revokeConfirm')" :width="360" @confirm="() => handleRevoke(row)">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatMass:revoke')" size="small" link>{{ $t('wechatMass.op.revoke') }}</el-button>
                </template>
              </el-popconfirm>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatMass:delete')" size="small" link>{{ $t('delete') }}</el-button>
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
    <wechat-mass-form v-model="formVisible" :account-list="accountList" :account-id="accountId" @finished="() => handleFinished()" />
  </div>
</template>

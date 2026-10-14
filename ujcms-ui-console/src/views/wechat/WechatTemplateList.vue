<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout } from '@/utils/common';
import { enumParam, stringParam, useRouteState } from '@/composables/useRouteState';
import {
  deleteWechatTemplate,
  deleteWechatTemplateLog,
  queryWechatAccountList,
  queryWechatTemplateIndustry,
  queryWechatTemplateList,
  queryWechatTemplateLogPage,
} from '@/api/wechat';
import WechatTemplateSendForm from './WechatTemplateSendForm.vue';

defineOptions({
  name: 'WechatTemplateList',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
// 发送记录页签需要 wechatTemplateLog:list 权限；无权限者手改地址无效（perm() 返回“是否无权限”）
const { accountId, tab } = useRouteState({
  accountId: stringParam(() => accountList.value[0]?.id),
  tab: enumParam('template', perm('wechatTemplateLog:list') ? ['template'] : ['template', 'log']),
});
const loading = ref<boolean>(false);
const data = ref<any[]>([]);
const industry = ref<any>(null);
const sendVisible = ref<boolean>(false);
const sendTemplate = ref<any>(null);

const logLoading = ref<boolean>(false);
const logData = ref<any[]>([]);
const logSelection = ref<any[]>([]);
const logCurrentPage = ref<number>(1);
const logPageSize = ref<number>(10);
const logTotal = ref<number>(0);
const logOpenid = ref<string>('');
const logStatus = ref<number>(-1);

// 模板消息为服务号功能，订阅号仅提示不可发送
const isSubscription = computed(() => accountList.value.find((item) => item.id === accountId.value)?.type === 0);

const fetchTemplateList = async () => {
  if (accountId.value == null) {
    data.value = [];
    return;
  }
  loading.value = true;
  try {
    data.value = await queryWechatTemplateList(accountId.value);
  } finally {
    loading.value = false;
  }
};
// 行业接口单独调用，失败不影响模板列表展示
const fetchIndustry = async () => {
  industry.value = null;
  if (accountId.value == null) {
    return;
  }
  try {
    industry.value = await queryWechatTemplateIndustry(accountId.value);
  } catch {
    // 行业获取失败时不展示行业信息
  }
};
const fetchLogData = async () => {
  logLoading.value = true;
  try {
    const {
      content,
      page: { totalElements },
    } = await queryWechatTemplateLogPage({
      Q_EQ_accountId_Long: accountId.value,
      Q_Contains_openid: logOpenid.value || undefined,
      Q_EQ_status_Short: logStatus.value !== -1 ? logStatus.value : undefined,
      page: logCurrentPage.value,
      pageSize: logPageSize.value,
    });
    logData.value = content;
    logTotal.value = Number(totalElements);
  } finally {
    logLoading.value = false;
  }
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchTemplateList();
    fetchIndustry();
    // 恢复出的页签不会触发 tab-change，须在此显式加载
    if (tab.value === 'log') {
      fetchLogData();
    }
  }
};
onMounted(fetchAccountList);

const handleAccountChange = () => {
  logCurrentPage.value = 1;
  fetchTemplateList();
  fetchIndustry();
  if (tab.value === 'log') {
    fetchLogData();
  }
};
const handleTabChange = () => {
  if (tab.value === 'log') {
    fetchLogData();
  }
};
const handleSend = (row: any) => {
  sendTemplate.value = row;
  sendVisible.value = true;
};
const handleSendFinished = () => {
  tab.value = 'log';
  logCurrentPage.value = 1;
  fetchLogData();
};
const handleDeleteTemplate = async (row: any) => {
  if (accountId.value == null) {
    return;
  }
  await deleteWechatTemplate({ accountId: accountId.value, templateId: row.templateId });
  fetchTemplateList();
  ElMessage.success(t('success'));
};
const handleDeleteLog = async (ids: string[]) => {
  await deleteWechatTemplateLog(ids);
  fetchLogData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => handleAccountChange()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <span v-if="industry != null" class="text-sm text-gray-secondary">
        {{ $t('wechatTemplate.industry') }}: {{ industry.primaryIndustry }}
        <template v-if="industry.secondIndustry"> , {{ industry.secondIndustry }}</template>
      </span>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <el-alert v-else-if="isSubscription" :title="$t('wechatTemplate.subscriptionTip')" type="warning" :closable="false" class="mt-3" />
    <div class="mt-3 app-block">
      <el-tabs v-model="tab" class="px-3" @tab-change="() => handleTabChange()">
        <el-tab-pane name="template" :label="$t('wechatTemplate.tab.template')"></el-tab-pane>
        <el-tab-pane v-if="!perm('wechatTemplateLog:list')" name="log" :label="$t('wechatTemplate.tab.log')"></el-tab-pane>
      </el-tabs>
      <template v-if="tab === 'template'">
        <el-table v-loading="loading" :row-key="(row: any) => row.templateId" :data="data">
          <el-table-column property="title" :label="$t('wechatTemplate.title')" width="180" show-overflow-tooltip></el-table-column>
          <el-table-column property="templateId" :label="$t('wechatTemplate.templateId')" :min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column property="primaryIndustry" :label="$t('wechatTemplate.primaryIndustry')" width="110" show-overflow-tooltip></el-table-column>
          <el-table-column property="deputyIndustry" :label="$t('wechatTemplate.deputyIndustry')" width="110" show-overflow-tooltip></el-table-column>
          <el-table-column property="content" :label="$t('wechatTemplate.content')" :min-width="240" show-overflow-tooltip></el-table-column>
          <el-table-column :label="$t('table.action')" width="120">
            <template #default="{ row }">
              <el-button
                type="primary"
                :disabled="isSubscription || perm('wechatTemplate:send')"
                size="small"
                link
                @click="() => handleSend(row)"
              >
                {{ $t('wechatTemplate.op.send') }}
              </el-button>
              <el-popconfirm :title="$t('wechatTemplate.deleteConfirm')" :width="320" @confirm="() => handleDeleteTemplate(row)">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatTemplate:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template v-else>
        <div class="px-3 pb-2 space-x-2">
          <el-input v-model="logOpenid" :placeholder="$t('wechatTemplateLog.openid')" clearable class="!w-56" @change="() => fetchLogData()"></el-input>
          <el-select v-model="logStatus" class="w-36" @change="() => fetchLogData()">
            <el-option :value="-1" :label="$t('wechatTemplateLog.status.all')"></el-option>
            <el-option v-for="n in [0, 1, 2]" :key="n" :value="n" :label="$t(`wechatTemplateLog.status.${n}`)"></el-option>
          </el-select>
          <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDeleteLog(logSelection.map((row) => row.id))">
            <template #reference>
              <el-button :disabled="logSelection.length <= 0 || perm('wechatTemplateLog:delete')">{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
        </div>
        <el-table v-loading="logLoading" row-key="id" :data="logData" @selection-change="(rows: any[]) => (logSelection = rows)">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column property="openid" :label="$t('wechatTemplateLog.openid')" :min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column property="title" :label="$t('wechatTemplateLog.title')" width="150" show-overflow-tooltip></el-table-column>
          <el-table-column property="status" :label="$t('wechatTemplateLog.status')" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'primary'" size="small">
                {{ $t(`wechatTemplateLog.status.${row.status}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column property="error" :label="$t('wechatTemplateLog.error')" :min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column property="created" :label="$t('wechatTemplateLog.created')" width="170">
            <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm:ss') }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="80">
            <template #default="{ row }">
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDeleteLog([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatTemplateLog:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="logCurrentPage"
          v-model:page-size="logPageSize"
          :total="logTotal"
          :page-sizes="pageSizes"
          :layout="pageLayout"
          size="small"
          background
          class="justify-end px-3 py-2"
          @size-change="() => fetchLogData()"
          @current-change="() => fetchLogData()"
        ></el-pagination>
      </template>
    </div>
    <wechat-template-send-form
      v-if="accountId != null"
      v-model="sendVisible"
      :account-id="accountId"
      :template="sendTemplate"
      @finished="() => handleSendFinished()"
    />
  </div>
</template>

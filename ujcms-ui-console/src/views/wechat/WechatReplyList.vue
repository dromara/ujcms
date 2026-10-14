<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Plus, Delete, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { deleteWechatReply, queryWechatAccountList, queryWechatReplyList, updateWechatReplyOrder } from '@/api/wechat';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { stringParam, useRouteState } from '@/composables/useRouteState';
import WechatReplyForm from './WechatReplyForm.vue';

defineOptions({
  name: 'WechatReplyList',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
const { accountId } = useRouteState({ accountId: stringParam(() => accountList.value[0]?.id) });
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const loading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));

const fetchData = async () => {
  if (accountId.value == null) {
    data.value = [];
    return;
  }
  loading.value = true;
  try {
    data.value = await queryWechatReplyList({ Q_EQ_accountId_Long: accountId.value });
  } finally {
    loading.value = false;
  }
};
const fetchAccountList = async () => {
  accountList.value = await queryWechatAccountList();
  if (accountList.value.length > 0) {
    // 恢复出的公众号须仍在列表中（切换站点后整页重载而地址不变、公众号已删除），否则回落到首个
    if (!accountList.value.some((item) => item.id === accountId.value)) {
      accountId.value = accountList.value[0].id;
    }
    fetchData();
  }
};
let sortable;
const initDragTable = () => {
  const tbody = document.querySelector('#dataTable .el-table__body-wrapper tbody');
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 200,
    chosenClass: 'sortable-chosen',
    onEnd: async function (event: any) {
      const { oldIndex, newIndex } = event;
      if (oldIndex !== newIndex) {
        await updateWechatReplyOrder(data.value[oldIndex].id, data.value[newIndex].id);
        data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
        ElMessage.success(t('success'));
      }
    },
  });
};
onMounted(() => {
  fetchAccountList();
  initDragTable();
});
onBeforeUnmount(() => {
  if (sortable !== undefined) {
    sortable.destroy();
  }
});

const handleAdd = () => {
  beanId.value = undefined;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  beanId.value = id;
  formVisible.value = true;
};
const handleDelete = async (ids: string[]) => {
  await deleteWechatReply(ids);
  fetchData();
  ElMessage.success(t('success'));
};
const contentPreview = (row: any): string => {
  if (row.replyType === 1) {
    return row.mediaId ?? '';
  }
  if (row.replyType === 2 && row.content) {
    try {
      const [news] = JSON.parse(row.content);
      return news?.title ?? '';
    } catch {
      return row.content;
    }
  }
  return row.content ?? '';
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => fetchData()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
      <el-button type="primary" :disabled="accountId == null || perm('wechatReply:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
      <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(selection.map((row) => row.id))">
        <template #reference>
          <el-button :disabled="selection.length <= 0 || perm('wechatReply:delete')" :icon="Delete">{{ $t('delete') }}</el-button>
        </template>
      </el-popconfirm>
      <column-setting name="wechatReply" />
    </div>
    <div class="mt-3 app-block">
      <el-table id="dataTable" ref="table" v-loading="loading" row-key="id" :data="data" @selection-change="(rows) => (selection = rows)" @row-dblclick="(row) => handleEdit(row.id)">
        <column-list name="wechatReply">
          <el-table-column type="selection" width="38"></el-table-column>
          <el-table-column width="42">
            <el-icon
              class="text-lg align-middle"
              :class="perm('wechatReply:update') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
            >
              <Grid />
            </el-icon>
          </el-table-column>
          <el-table-column property="type" :label="$t('wechatReply.type')" width="110">
            <template #default="{ row }">
              <el-tag :type="row.type === 1 ? 'primary' : row.type === 0 ? 'success' : 'warning'" size="small">{{ $t(`wechatReply.type.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="keyword" :label="$t('wechatReply.keyword')" :min-width="120" show-overflow-tooltip></el-table-column>
          <el-table-column property="matchType" :label="$t('wechatReply.matchType')" width="100">
            <template #default="{ row }">
              <span v-if="row.type === 1">{{ $t(`wechatReply.matchType.${row.matchType}`) }}</span>
            </template>
          </el-table-column>
          <el-table-column property="replyType" :label="$t('wechatReply.replyType')" width="100">
            <template #default="{ row }">{{ $t(`wechatReply.replyType.${row.replyType}`) }}</template>
          </el-table-column>
          <el-table-column property="content" :label="$t('wechatReply.content')" :min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ contentPreview(row) }}</template>
          </el-table-column>
          <el-table-column property="enabled" :label="$t('wechatReply.enabled')" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ $t(row.enabled ? 'enable' : 'disable') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="120">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('wechatReply:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
              <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete([row.id])">
                <template #reference>
                  <el-button type="primary" :disabled="perm('wechatReply:delete')" size="small" link>{{ $t('delete') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </column-list>
      </el-table>
    </div>
    <wechat-reply-form v-if="accountId != null" v-model="formVisible" :bean-id="beanId" :bean-ids="beanIds" :account-id="accountId" @finished="() => fetchData()" />
  </div>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

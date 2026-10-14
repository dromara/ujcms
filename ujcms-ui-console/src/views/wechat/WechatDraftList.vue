<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { deleteWechatDraft, publishWechatDraft, queryWechatAccountList, queryWechatDraftList } from '@/api/wechat';
import { stringParam, useRouteState } from '@/composables/useRouteState';
import WechatItemsTag from './WechatItemsTag.vue';

defineOptions({
  name: 'WechatDraftList',
});
const { t } = useI18n();
const accountList = ref<any[]>([]);
// 当前公众号存于 query.accountId（docs/spec/console-view-state），默认首个公众号：视图在刷新、页签往返时都会重建，
// 不记住就会悄悄回到首个公众号，运营者可能在错误的公众号上继续操作
const { accountId } = useRouteState({ accountId: stringParam(() => accountList.value[0]?.id) });
const currentPage = ref<number>(1);
const total = ref<number>(0);
const data = ref<any[]>([]);
const loading = ref<boolean>(false);

const fetchData = async () => {
  if (accountId.value == null) {
    data.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await queryWechatDraftList({ accountId: accountId.value, page: currentPage.value });
    data.value = result.items ?? [];
    total.value = Number(result.totalCount ?? 0);
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
onMounted(fetchAccountList);

const handleAccountChange = () => {
  currentPage.value = 1;
  fetchData();
};
const handlePublish = async (mediaId: string) => {
  if (accountId.value == null) return;
  await publishWechatDraft({ accountId: accountId.value, mediaId });
  ElMessage.success(t('success'));
};
const handleDelete = async (mediaId: string) => {
  if (accountId.value == null) return;
  await deleteWechatDraft({ accountId: accountId.value, mediaId });
  fetchData();
  ElMessage.success(t('success'));
};
</script>

<template>
  <div>
    <div class="space-x-2">
      <el-select v-model="accountId" :placeholder="$t('wechat.selectAccount')" class="w-48" @change="() => handleAccountChange()">
        <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
      </el-select>
    </div>
    <el-alert v-if="accountList.length <= 0" :title="$t('wechat.noAccount')" type="info" :closable="false" class="mt-3" />
    <el-alert v-else :title="$t('wechatDraft.tip')" type="info" :closable="false" show-icon class="mt-3" />
    <div class="mt-3 app-block">
      <el-table v-loading="loading" :row-key="(row: any) => row.mediaId" :data="data">
        <el-table-column :label="$t('wechatMaterial.preview')" width="120">
          <template #default="{ row }">
            <el-image v-if="row.articles?.[0]?.thumbUrl" :src="row.articles[0].thumbUrl" fit="cover" class="w-20 h-20" referrerpolicy="no-referrer" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('wechatArticle.title')" :min-width="240">
          <template #default="{ row }">
            <!-- 头条标题可截断，“多图文 N 篇”标签始终可见，悬停列出各篇并可打开网页预览 -->
            <div class="flex min-w-0 items-center">
              <span class="min-w-0 truncate" :title="row.articles?.[0]?.title">{{ row.articles?.[0]?.title }}</span>
              <wechat-items-tag :items="row.articles ?? []" />
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('wechatArticle.digest')" :min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.articles?.[0]?.digest }}</template>
        </el-table-column>
        <el-table-column property="mediaId" label="MediaID" :min-width="220" show-overflow-tooltip></el-table-column>
        <el-table-column property="updateTime" :label="$t('wechatMaterial.updateTime')" width="170">
          <template #default="{ row }">{{ row.updateTime ? dayjs(Number(row.updateTime) * 1000).format('YYYY-MM-DD HH:mm:ss') : '' }}</template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" width="190">
          <template #default="{ row }">
            <!-- 网页预览：微信随草稿返回的临时链接，无需权限，在浏览器中打开正文 -->
            <el-button
              v-if="row.articles?.[0]?.url"
              type="primary"
              size="small"
              link
              tag="a"
              :href="row.articles[0].url"
              target="_blank"
              rel="noopener"
              :title="$t('wechatDraft.preview.tooltip')"
            >
              {{ $t('wechatDraft.op.preview') }}
            </el-button>
            <el-popconfirm :title="$t('wechatDraft.publishConfirm')" :width="320" @confirm="() => handlePublish(row.mediaId)">
              <template #reference>
                <el-button type="primary" :disabled="perm('wechatDraft:publish')" size="small" link>{{ $t('wechatDraft.op.publish') }}</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm :title="$t('confirmDelete')" @confirm="() => handleDelete(row.mediaId)">
              <template #reference>
                <el-button type="primary" :disabled="perm('wechatDraft:delete')" size="small" link>{{ $t('delete') }}</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        :total="total"
        :page-size="20"
        layout="total, prev, pager, next"
        size="small"
        background
        class="justify-end px-3 py-2"
        @current-change="() => fetchData()"
      ></el-pagination>
    </div>
  </div>
</template>

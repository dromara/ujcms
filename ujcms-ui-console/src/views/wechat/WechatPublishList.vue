<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { deleteWechatPublish, queryWechatAccountList, queryWechatPublishList } from '@/api/wechat';
import { stringParam, useRouteState } from '@/composables/useRouteState';
import WechatItemsTag from './WechatItemsTag.vue';

defineOptions({
  name: 'WechatPublishList',
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
    const result = await queryWechatPublishList({ accountId: accountId.value, page: currentPage.value });
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
const handleDelete = async (articleId: string) => {
  if (accountId.value == null) return;
  await deleteWechatPublish({ accountId: accountId.value, articleId });
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
    <div class="mt-3 app-block">
      <el-table v-loading="loading" :row-key="(row: any) => row.articleId" :data="data">
        <el-table-column :label="$t('wechatMaterial.preview')" width="120">
          <template #default="{ row }">
            <el-image v-if="row.articles?.[0]?.thumbUrl" :src="row.articles[0].thumbUrl" fit="cover" class="w-20 h-20" referrerpolicy="no-referrer" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('wechatArticle.title')" :min-width="240">
          <template #default="{ row }">
            <!-- 与推送记录一致：头条标题可截断，“多图文 N 篇”标签始终可见，悬停列出全部篇目 -->
            <div class="flex min-w-0 items-center">
              <a v-if="row.articles?.[0]?.url" :href="row.articles[0].url" target="_blank" class="text-primary min-w-0 truncate" :title="row.articles[0].title">
                {{ row.articles[0].title }}
              </a>
              <span v-else class="min-w-0 truncate" :title="row.articles?.[0]?.title">{{ row.articles?.[0]?.title }}</span>
              <wechat-items-tag :items="row.articles ?? []" />
              <el-tag v-if="row.articles?.[0]?.isDeleted" type="danger" size="small" class="ml-1 shrink-0">{{ $t('wechatPublish.isDeleted') }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column property="articleId" :label="$t('wechatPublish.articleId')" :min-width="220" show-overflow-tooltip></el-table-column>
        <el-table-column property="updateTime" :label="$t('wechatMaterial.updateTime')" width="170">
          <template #default="{ row }">{{ row.updateTime ? dayjs(Number(row.updateTime) * 1000).format('YYYY-MM-DD HH:mm:ss') : '' }}</template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" width="100">
          <template #default="{ row }">
            <el-popconfirm :title="$t('wechatPublish.deleteConfirm')" @confirm="() => handleDelete(row.articleId)">
              <template #reference>
                <el-button type="primary" :disabled="perm('wechatPublish:delete')" size="small" link>{{ $t('delete') }}</el-button>
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

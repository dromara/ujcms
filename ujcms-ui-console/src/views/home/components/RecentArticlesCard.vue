<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { queryArticlePage } from '@/api/content';
import { articleStatusTagType, openArticleLink } from '@/views/content/components/articleUtils';
import HomeCard from './HomeCard.vue';

/** 最近更新：本站最近修改的 8 篇文章（列表默认按 order_/id_ 降序，order_ 会被拖拽排序改写，故显式按修改时间） */
defineOptions({
  name: 'RecentArticlesCard',
});
const router = useRouter();
const articles = ref<any[]>([]);
const loading = ref<boolean>(true);
onMounted(async () => {
  try {
    const { content } = await queryArticlePage({ page: 1, pageSize: 8, Q_OrderBy: 'modified_desc' });
    articles.value = content ?? [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <home-card :title="$t('home.recent')">
    <template #action>
      <el-button link type="primary" size="small" @click="() => router.push({ name: 'ArticleList' })">{{ $t('home.recent.viewAll') }}</el-button>
    </template>
    <div v-loading="loading" class="min-h-[3rem]">
      <div v-if="articles.length > 0" class="divide-y divide-gray-100">
        <div v-for="row in articles" :key="row.id" class="flex items-center gap-3 py-2 text-sm">
          <el-tooltip v-if="row.status === 22" :content="row.rejectReason" placement="top">
            <el-tag type="danger" size="small">{{ $t(`article.status.${row.status}`) }}</el-tag>
          </el-tooltip>
          <el-tag v-else :type="articleStatusTagType(row.status)" size="small">{{ $t(`article.status.${row.status}`) }}</el-tag>
          <!-- 已发布的打开正式地址，其余以预览方式打开（须传完整地址，见 openArticleLink） -->
          <button type="button" class="flex-1 min-w-0 text-left truncate text-gray-primary hover:text-primary" @click="() => openArticleLink(row.status, row.fullUrl, row.dynamicFullUrl)">
            {{ row.title }}
          </button>
          <span class="w-28 text-xs text-gray-secondary truncate" :title="row.channel?.paths?.map((item: any) => item.name).join(' / ')">{{ row.channel?.name }}</span>
          <span class="text-xs tabular-nums text-gray-secondary">{{ dayjs(row.modified).format('YYYY-MM-DD HH:mm') }}</span>
          <span class="w-20 text-xs text-gray-secondary truncate">{{ row.user?.username }}</span>
        </div>
      </div>
      <div v-else-if="!loading" class="py-3 text-sm text-gray-secondary">{{ $t('home.recent.empty') }}</div>
    </div>
  </home-card>
</template>

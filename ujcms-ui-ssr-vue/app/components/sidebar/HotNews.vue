<script setup lang="ts">
import { fetchArticles } from '~/api/article';

/**
 * 热点新闻 sidebar widget — top N articles by week views, all-site by default.
 * Mirrors the template-theme `right.html` 1st section.
 */
const props = withDefaults(defineProps<{ siteId: string | undefined; limit?: number }>(), { limit: 9 });

const { data: articles } = await useAsyncData(
  () => `hot-news-${props.siteId ?? 'all'}-${props.limit}`,
  () =>
    fetchArticles({
      siteId: props.siteId,
      isAllSite: !props.siteId,
      orderBy: '@articleExt-weekViews_desc',
      limit: props.limit,
    }),
  { default: () => [] },
);

const rankClass = (rank: number): string => {
  if (rank === 1) return 'bg-red-600 text-white';
  if (rank === 2) return 'bg-amber-500 text-white';
  if (rank === 3) return 'bg-cms-primary text-white';
  return 'bg-slate-300 text-slate-700';
};
</script>

<template>
  <section v-if="articles.length > 0">
    <h2 class="mb-4 text-base font-semibold">热点新闻</h2>
    <ul class="space-y-3">
      <li v-for="(article, index) in articles" :key="article.id" class="flex items-start gap-2">
        <span :class="`inline-block w-6 rounded text-center text-xs font-bold leading-5 ${rankClass(index + 1)}`" aria-hidden="true">{{ index + 1 }}</span>
        <NuxtLink :to="article.url ?? article.dynamicUrl ?? `/article/${article.id}`" class="line-clamp-1 flex-1 text-sm text-cms-content hover:text-cms-primary">
          {{ article.title ?? '无标题' }}
        </NuxtLink>
      </li>
    </ul>
  </section>
</template>

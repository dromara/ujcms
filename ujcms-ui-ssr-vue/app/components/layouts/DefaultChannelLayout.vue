<script setup lang="ts">
import type { Article, Channel, PagedModel, Site } from '#shared/types/api';

/**
 * Default channel layout — paginated article list + hot-news aside.
 */
const props = defineProps<{
  channel: Channel;
  site: Site | null;
  searchParams: Record<string, string>;
  basePath: string;
  articlePage: PagedModel<Article> | null;
  currentPage: number;
  totalPages: number;
  extraSearch: string;
}>();

const articles = computed(() => props.articlePage?.content ?? []);
</script>

<template>
  <ChannelShell :channel="channel" :search-params="searchParams" :base-path="basePath">
    <div class="grid grid-cols-1 gap-8 lg:grid-cols-3">
      <div class="space-y-4 lg:col-span-2">
        <p v-if="articles.length === 0" class="rounded-md border border-cms-border bg-cms-surface p-8 text-center text-sm text-cms-content-muted">暂无文章</p>
        <ul v-else class="divide-y divide-cms-border">
          <ArticleListItem v-for="article in articles" :key="article.id" :article="article" />
        </ul>
        <AppPagination :current-page="currentPage" :total-pages="totalPages" :base-path="basePath" :extra-search="extraSearch" />
      </div>
      <aside class="hidden space-y-6 lg:col-span-1 lg:block">
        <HotNews :site-id="site?.id" />
      </aside>
    </div>
  </ChannelShell>
</template>

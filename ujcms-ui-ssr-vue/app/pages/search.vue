<script setup lang="ts">
import { fetchEsArticlePage } from '~/api/es-article';

/**
 * Search page. Mirrors templates/sys_search.html.
 *
 * Form-and-results pattern: form is rendered with current values pre-filled;
 * results render only when q is non-empty (matching baseline). Submission is
 * a plain GET, so it works without JS.
 *
 * Coverage:
 *   - q + channelId + beginPublishDate + endPublishDate
 *   - ES results with highlights
 *   - Pagination
 *   - Quick-preset date buttons (`<SearchDatePresets>`)
 */
const route = useRoute();
const { session } = useSessionState();
const site = computed(() => session.value.site);

const q = computed(() => pickQueryString(route.query.q));
const channelId = computed(() => pickQueryString(route.query.channelId));
const beginPublishDate = computed(() => pickQueryString(route.query.beginPublishDate));
const endPublishDate = computed(() => pickQueryString(route.query.endPublishDate));
const currentPage = computed(() => parsePageParam(route.query.page));
const hasQuery = computed(() => q.value.length > 0);

const { data: result } = await useAsyncData(
  () => `search-${q.value}-${channelId.value}-${beginPublishDate.value}-${endPublishDate.value}-${currentPage.value}`,
  () =>
    hasQuery.value
      ? fetchEsArticlePage({
          siteId: site.value?.id,
          q: q.value,
          channelId: channelId.value || undefined,
          beginPublishDate: beginPublishDate.value || undefined,
          endPublishDate: endPublishDate.value || undefined,
          isOperatorAnd: true,
          fragmentSize: 200,
          dateExpScale: 365,
          page: currentPage.value,
          pageSize: 10,
        })
      : Promise.resolve({ content: [], page: undefined }),
  { default: () => ({ content: [], page: undefined }) },
);

const total = computed(() => toPageInt(result.value.page?.totalElements, 0));
const totalPages = computed(() => toPageInt(result.value.page?.totalPages, 1));

const extraSearch = computed(() => {
  const extra = new URLSearchParams();
  if (q.value) extra.set('q', q.value);
  if (channelId.value) extra.set('channelId', channelId.value);
  if (beginPublishDate.value) extra.set('beginPublishDate', beginPublishDate.value);
  if (endPublishDate.value) extra.set('endPublishDate', endPublishDate.value);
  return extra.toString();
});

useSeoMeta({ title: '搜索' });
</script>

<template>
  <div>
    <div class="bg-slate-100">
      <AppBreadcrumb :items="[{ label: '首页', href: '/' }, { label: '搜索' }]" />
    </div>
    <div class="mx-auto mt-6 grid max-w-6xl grid-cols-1 gap-8 px-4 lg:grid-cols-3">
      <div class="space-y-4 lg:col-span-2">
        <SearchForm :q="q" :channel-id="channelId" :begin-publish-date="beginPublishDate" :end-publish-date="endPublishDate" :site-id="site?.id" />
        <template v-if="hasQuery">
          <SearchResults :items="result.content" :total="total" />
          <AppPagination :current-page="currentPage" :total-pages="totalPages" base-path="/search" :extra-search="extraSearch" />
        </template>
      </div>
      <aside class="hidden space-y-6 lg:col-span-1 lg:block">
        <HotNews :site-id="site?.id" />
      </aside>
    </div>
  </div>
</template>

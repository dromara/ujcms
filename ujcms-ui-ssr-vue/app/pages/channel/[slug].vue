<script setup lang="ts">
import { fetchArticlePage } from '~/api/article';
import { fetchChannelBySlug } from '~/api/channel';

/**
 * Channel listing page — paginated article list rendered by
 * `<DefaultChannelLayout>`.
 *
 * Pagination + facet-filter URL state are resolved here. Facet params follow
 * the template-theme contract (`<fieldCode>Key`) and translate to Spring's
 * `Q_ArrayEQ_@articleExt-mainsJson$<code>Key[_Int]` shape — mirrors
 * `PaginateTag.applyFacetsFromChannel`.
 */
const FACET_TYPES = new Set(['radio', 'checkbox', 'select', 'multipleSelect']);

const route = useRoute();
const { session } = useSessionState();
const site = computed(() => session.value.site);

const slug = computed(() => String(route.params.slug ?? ''));
const sp = computed(() => flattenQuery(route.query));
const currentPage = computed(() => parsePageParam(route.query.page));

const { data, error } = await useAsyncData(
  () => `channel-${slug.value}-${JSON.stringify(sp.value)}`,
  async () => {
    const channel = await fetchChannelBySlug(slug.value, site.value?.id);
    if (!channel) return { channel: null, articlePage: null };

    // Extract facet query params and translate them to Spring's mainsJson key shape.
    const fieldList = channel.articleModel?.fieldList ?? [];
    const facetQuery: Record<string, string> = {};
    for (const field of fieldList) {
      if (!field.type || !FACET_TYPES.has(field.type) || !field.dictTypeId) continue;
      const paramName = `${field.code}Key`;
      const value = sp.value[paramName];
      if (!value) continue;
      const isString = (field.dataType ?? 'string') === 'string';
      const key = `Q_ArrayEQ_@articleExt-mainsJson$${paramName}${isString ? '' : '_Int'}`;
      facetQuery[key] = value;
    }

    const orderBy = channel.orderDesc ? 'sticky_desc,order_desc' : 'sticky_desc,order_asc';
    const pageSize = channel.pageSize ?? 10;

    const articlePage = await fetchArticlePage({
      siteId: site.value?.id,
      channelId: channel.id,
      isIncludeSubChannel: true,
      page: currentPage.value,
      pageSize,
      orderBy,
      query: facetQuery,
    });
    return { channel, articlePage };
  },
);

if (error.value) {
  throw createError({ statusCode: 500, statusMessage: 'channel fetch failed', cause: error.value });
}
const channel = computed(() => data.value?.channel ?? null);
if (!channel.value) {
  throw createError({ statusCode: 404, statusMessage: 'Channel Not Found' });
}

const basePath = computed(() => channel.value?.dynamicUrl ?? channel.value?.url ?? `/channel/${slug.value}`);
const totalPages = computed(() => toPageInt(data.value?.articlePage?.page?.totalPages, 1));

const extraSearch = computed(() => {
  const extra = new URLSearchParams();
  for (const [key, value] of Object.entries(sp.value)) {
    if (key === 'page') continue;
    if (value) extra.set(key, value);
  }
  return extra.toString();
});

useSeoMeta({
  title: () => channel.value?.title ?? channel.value?.name ?? slug.value,
  description: () => channel.value?.seoDescription,
  keywords: () => channel.value?.seoKeywords,
});
</script>

<template>
  <DefaultChannelLayout
    v-if="channel"
    :channel="channel"
    :site="site"
    :search-params="sp"
    :base-path="basePath"
    :article-page="data?.articlePage ?? null"
    :current-page="currentPage"
    :total-pages="totalPages"
    :extra-search="extraSearch"
  />
</template>

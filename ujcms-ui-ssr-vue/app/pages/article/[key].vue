<script setup lang="ts">
import { fetchArticle, fetchArticleBySlug } from '~/api/article';

/**
 * Article detail page — rendered by `<DefaultArticleLayout>`.
 *
 * The route key follows the backend's article URL shape:
 *   - `/article/{id}`        — numeric id (no custom slug)
 *   - `/article/{id}-{slug}` — hybrid (default mode)
 *   - `/article/{slug}`      — slug-only mode
 * Resolution: an all-digit key is an id first, a `{id}-{slug}` key is an id
 * first; whenever the id lookup misses (or the key is not id-shaped) the whole
 * key is retried as a slug via `/api/article/slug/{slug}`. Nothing → 404.
 */
const route = useRoute();
const { session } = useSessionState();
const site = computed(() => session.value.site);

const key = computed(() => String(route.params.key ?? ''));

const resolveArticle = async (routeKey: string, siteId?: string) => {
  if (/^\d+$/.test(routeKey)) {
    return (await fetchArticle(routeKey)) ?? (await fetchArticleBySlug(routeKey, siteId));
  }
  const hybrid = /^(\d+)-(.+)$/.exec(routeKey);
  const hybridId = hybrid?.[1];
  if (hybridId) {
    return (await fetchArticle(hybridId)) ?? (await fetchArticleBySlug(routeKey, siteId));
  }
  return fetchArticleBySlug(routeKey, siteId);
};

const { data: article, error } = await useAsyncData(
  () => `article-${key.value}`,
  () => resolveArticle(key.value, site.value?.id),
);

if (error.value) {
  throw createError({ statusCode: 500, statusMessage: 'article fetch failed', cause: error.value });
}
if (!article.value) {
  throw createError({ statusCode: 404, statusMessage: 'Article Not Found' });
}

useSeoMeta({
  title: () => article.value?.title,
  description: () => article.value?.seoDescription,
  keywords: () => article.value?.seoKeywords,
  ogTitle: () => article.value?.title,
  ogDescription: () => article.value?.seoDescription,
  ogType: 'article',
  ogImage: () => article.value?.image,
  articlePublishedTime: () => article.value?.publishDate,
});
</script>

<template>
  <DefaultArticleLayout v-if="article" :article="article" :site="site" />
</template>

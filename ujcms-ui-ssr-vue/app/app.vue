<script setup lang="ts">
import { fetchConfig, fetchCurrentSite } from '~/api/site';

/**
 * Root shell. Fetches site/config in parallel during SSR, seeds them into
 * the shared session state, and sets the per-request head metadata from the
 * current site so view-source shows real content (Baidu requirement).
 *
 * Pages don't render their own chrome — the default layout supplies the
 * header + footer; pages render only the inner <main> content.
 */
const { session } = useSessionState();

await useAsyncData('ujcms-session', async () => {
  const [site, config] = await Promise.all([fetchCurrentSite(), fetchConfig()]);
  session.value = { site, config };
  return true;
});

const siteTitle = computed(() => session.value.site?.seoTitle ?? session.value.site?.name ?? 'UJCMS');

useHead({
  titleTemplate: (title?: string) => (title ? `${title} | ${siteTitle.value}` : siteTitle.value),
});
useSeoMeta({
  description: () => (typeof session.value.site?.seoDescription === 'string' ? session.value.site.seoDescription : undefined),
  keywords: () => (typeof session.value.site?.seoKeywords === 'string' ? session.value.site.seoKeywords : undefined),
});
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>

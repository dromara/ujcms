<script setup lang="ts">
import type { Article } from '#shared/types/api';

import type { BreadcrumbItem } from '~/components/AppBreadcrumb.vue';

/**
 * Breadcrumb for article pages: 首页 / channel.paths / article title.
 * Mirrors the strip at the top of `templates/article.html`.
 *
 * Article title goes in as a non-link last item — the user is already on it.
 */
const props = defineProps<{ article: Article }>();

const items = computed<BreadcrumbItem[]>(() => {
  const out: BreadcrumbItem[] = [{ label: '首页', href: '/' }];
  for (const c of props.article.channel?.paths ?? []) {
    out.push({ label: c.name ?? '未命名', href: c.url ?? `/channel/${c.slug ?? c.id}` });
  }
  if (props.article.title) out.push({ label: props.article.title });
  return out;
});
</script>

<template>
  <div class="bg-slate-100">
    <AppBreadcrumb :items="items" />
  </div>
</template>

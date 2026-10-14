<script setup lang="ts">
import type { Article } from '#shared/types/api';

import { fetchArticleNext, fetchArticlePrev } from '~/api/article';

/**
 * Prev / Next article links beneath the body — fetches both neighbours in
 * parallel during SSR.
 *
 * Direction semantics (matches template-theme baseline / ArticleController):
 *   isDesc = channel.orderDesc
 *   /api/article/next  ↔ "上一条" (next-in-order = previous-in-time when desc)
 *   /api/article/prev  ↔ "下一条" (prev-in-order = next-in-time when desc)
 *
 * The template-theme labels are correct — don't rename the API endpoints.
 */
const props = defineProps<{ article: Article }>();

const hasParams = computed(() => Boolean(props.article.channelId && props.article.order));

const { data: neighbours } = await useAsyncData(
  () => `article-neighbours-${props.article.id}`,
  async () => {
    if (!hasParams.value) return { next: null, prev: null };
    const params = {
      id: props.article.id,
      order: props.article.order,
      channelId: props.article.channelId,
      isDesc: props.article.channel?.orderDesc ?? true,
    };
    const [next, prev] = await Promise.all([fetchArticleNext(params), fetchArticlePrev(params)]);
    return { next, prev };
  },
  { default: () => ({ next: null, prev: null }) },
);

const itemHref = (item: Article): string => item.url ?? item.dynamicUrl ?? `/article/${item.id}`;
</script>

<template>
  <ul v-if="hasParams" class="space-y-1 text-sm">
    <li class="truncate">
      <strong>上一条：</strong>
      <NuxtLink v-if="neighbours.next" :to="itemHref(neighbours.next)" class="text-cms-primary hover:underline">{{ neighbours.next.title ?? '无标题' }}</NuxtLink>
      <span v-else class="text-cms-content-muted">没有了</span>
    </li>
    <li class="truncate">
      <strong>下一条：</strong>
      <NuxtLink v-if="neighbours.prev" :to="itemHref(neighbours.prev)" class="text-cms-primary hover:underline">{{ neighbours.prev.title ?? '无标题' }}</NuxtLink>
      <span v-else class="text-cms-content-muted">没有了</span>
    </li>
  </ul>
</template>

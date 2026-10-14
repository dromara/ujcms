<script setup lang="ts">
import { fetchArticles } from '~/api/article';
import { fetchBlockItems } from '~/api/block';

/**
 * Homepage — a plain headless-API composition: focus-block hero
 * (`/api/block-item?block=focus`) + latest-articles list (`/api/article`)
 * + hot-news aside. Everything is fetched during SSR from the same public
 * REST API that mobile apps and mini-programs consume; there is no
 * SSR-special backend endpoint behind this page.
 */
const { session } = useSessionState();
const siteId = computed(() => session.value.site?.id);

const { data } = await useAsyncData(
  () => `home-${siteId.value ?? 'none'}`,
  async () => {
    const [focusItems, latest] = await Promise.all([
      fetchBlockItems({ siteId: siteId.value, block: 'focus', isEnabled: true, limit: 4 }),
      fetchArticles({ siteId: siteId.value, limit: 10 }),
    ]);
    return { focusItems, latest };
  },
  { default: () => ({ focusItems: [], latest: [] }) },
);

const focusItems = computed(() => data.value.focusItems);
const latest = computed(() => data.value.latest);
</script>

<template>
  <div class="mx-auto mt-6 grid max-w-6xl grid-cols-1 gap-8 px-4 lg:grid-cols-3">
    <div class="space-y-8 lg:col-span-2">
      <section v-if="focusItems.length > 0" class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <NuxtLink
          v-for="item in focusItems"
          :key="item.id"
          :to="item.url ?? '/'"
          :target="item.targetBlank ? '_blank' : undefined"
          class="group relative overflow-hidden rounded-lg bg-slate-200"
        >
          <img v-if="item.image" :src="item.image" :alt="item.title ?? ''" class="aspect-video w-full object-cover transition group-hover:scale-105" />
          <span class="absolute inset-x-0 bottom-0 bg-linear-to-t from-black/70 to-transparent p-3 text-sm font-medium text-white">
            {{ item.title ?? '无标题' }}
          </span>
        </NuxtLink>
      </section>

      <section>
        <h2 class="mb-2 border-b border-cms-border pb-2 text-lg font-semibold">最新文章</h2>
        <p v-if="latest.length === 0" class="rounded-md border border-cms-border bg-cms-surface p-8 text-center text-sm text-cms-content-muted">暂无文章</p>
        <ul v-else class="divide-y divide-cms-border">
          <ArticleListItem v-for="article in latest" :key="article.id" :article="article" />
        </ul>
      </section>
    </div>

    <aside class="hidden space-y-6 lg:col-span-1 lg:block">
      <HotNews :site-id="siteId" />
    </aside>
  </div>
</template>

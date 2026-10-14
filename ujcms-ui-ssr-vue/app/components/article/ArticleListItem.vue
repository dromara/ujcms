<script setup lang="ts">
import type { Article } from '#shared/types/api';

/**
 * Single article row for the homepage / channel list. Mirrors the template theme
 * snippet `part_article_items.html`:
 *  - thumbnail (if image) on the left
 *  - title (linked) + 60-char description preview
 *  - channel-paths breadcrumb chips, view/download counter, publish date
 *
 * `metric` selects whether to show views (default) or downloads — channel
 * variants like `channel_download` pass `metric="downloads"`.
 */
const props = withDefaults(defineProps<{ article: Article; metric?: 'views' | 'downloads' }>(), { metric: 'views' });

const href = computed(() => props.article.url ?? props.article.dynamicUrl ?? `/article/${props.article.id}`);
const description = computed(() => truncate(stripHtml(props.article.description), 60));
const channelPaths = computed(() => props.article.channel?.paths ?? []);

// Inline icon paths in this template: Material Symbols (Google), Apache License 2.0, unmodified; see NOTICE.
</script>

<template>
  <li class="flex gap-4 border-b border-cms-border py-3">
    <NuxtLink v-if="article.image" :to="href" class="shrink-0">
      <img :src="article.image" :alt="article.title ?? ''" class="h-20 w-32 rounded object-cover sm:h-24 sm:w-40" loading="lazy" />
    </NuxtLink>
    <div class="min-w-0 flex-1">
      <p class="mb-1">
        <NuxtLink :to="href" class="font-medium text-cms-content hover:text-cms-primary">
          {{ article.title ?? '无标题' }}
        </NuxtLink>
      </p>
      <p v-if="description" class="mb-2 hidden text-sm text-cms-content-muted sm:block line-clamp-2">
        <NuxtLink :to="href" class="text-inherit no-underline">{{ description }}</NuxtLink>
      </p>
      <div class="flex items-center justify-between text-xs text-cms-content-muted">
        <div class="flex flex-wrap items-center gap-2">
          <span class="space-x-1">
            <span v-for="(c, idx) in channelPaths" :key="c.id">
              <NuxtLink v-if="c.url" :to="c.url" class="hover:text-cms-primary">{{ c.name }}</NuxtLink>
              <span v-else>{{ c.name }}</span>
              <span v-if="idx < channelPaths.length - 1" aria-hidden="true"> &gt; </span>
            </span>
          </span>
          <span class="inline-flex items-center gap-1" :aria-label="metric === 'downloads' ? '下载量' : '浏览量'">
            <svg v-if="metric === 'downloads'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" class="size-3">
              <path d="m12 16l-5-5l1.4-1.45l2.6 2.6V4h2v8.15l2.6-2.6L17 11zm-6 4q-.825 0-1.412-.587T4 18v-3h2v3h12v-3h2v3q0 .825-.587 1.413T18 20z" />
            </svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" class="size-3">
              <path
                d="M15.188 14.688Q16.5 13.375 16.5 11.5t-1.312-3.187T12 7T8.813 8.313T7.5 11.5t1.313 3.188T12 16t3.188-1.312m-5.1-1.276Q9.3 12.625 9.3 11.5t.788-1.912T12 8.8t1.913.788t.787 1.912t-.787 1.913T12 14.2t-1.912-.787m-4.738 3.55Q2.35 14.925 1 11.5q1.35-3.425 4.35-5.462T12 4t6.65 2.038T23 11.5q-1.35 3.425-4.35 5.463T12 19t-6.65-2.037m11.838-1.45Q19.55 14.025 20.8 11.5q-1.25-2.525-3.613-4.012T12 6T6.813 7.488T3.2 11.5q1.25 2.525 3.613 4.013T12 17t5.188-1.487"
              />
            </svg>
            <span>{{ metric === 'downloads' ? (article.downloads ?? 0) : (article.views ?? 0) }}</span>
          </span>
        </div>
        <span class="inline-flex items-center gap-1">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" class="size-3">
            <path
              d="m15.3 16.7l1.4-1.4l-3.7-3.7V7h-2v5.4zM12 22q-2.075 0-3.9-.788t-3.175-2.137T2.788 15.9T2 12t.788-3.9t2.137-3.175T8.1 2.788T12 2t3.9.788t3.175 2.137T21.213 8.1T22 12t-.788 3.9t-2.137 3.175t-3.175 2.138T12 22m0-2q3.325 0 5.663-2.337T20 12t-2.337-5.663T12 4T6.337 6.338T4 12t2.338 5.663T12 20"
            />
          </svg>
          <span>{{ formatDate(article.publishDate) }}</span>
        </span>
      </div>
    </div>
  </li>
</template>

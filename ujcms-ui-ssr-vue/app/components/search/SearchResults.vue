<script setup lang="ts">
import type { EsArticle } from '#shared/types/api';

/**
 * Search results list. Mirrors the result-rendering block of
 * templates/sys_search.html:
 *  - prefer highlightTitle/highlightBody (HTML with <em> wraps) when present
 *  - fall back to title and stripped+truncated body
 *
 * The highlight HTML is server-controlled (Lucene wraps matches in <em>) so
 * v-html is safe — the source HTML was OWASP-sanitised before indexing, and
 * Lucene's highlighter only adds <em> markup.
 */
defineProps<{ items: EsArticle[]; total: number }>();

const itemHref = (bean: EsArticle): string => bean.url ?? `/article/${bean.id}`;
</script>

<template>
  <div class="space-y-3">
    <h3 class="border-b border-cms-border pb-2 text-lg font-semibold">搜索结果</h3>
    <p class="text-xs text-cms-content-muted">为您找到相关结果 {{ total }} 篇</p>

    <p v-if="items.length === 0" class="py-8 text-center text-cms-content-muted">暂无搜索结果</p>
    <ul v-else class="divide-y divide-cms-border">
      <li v-for="bean in items" :key="bean.id" class="flex gap-4 py-3">
        <NuxtLink v-if="bean.image" :to="itemHref(bean)" class="shrink-0">
          <img :src="bean.image" :alt="bean.title ?? ''" class="h-20 w-32 rounded object-cover sm:h-24 sm:w-40" loading="lazy" />
        </NuxtLink>
        <div class="es-highlight min-w-0 flex-1">
          <p class="mb-1">
            <NuxtLink :to="itemHref(bean)" class="font-medium text-cms-content hover:text-cms-primary">
              <span v-if="bean.highlightTitle" v-html="bean.highlightTitle" />
              <span v-else>{{ bean.title ?? '无标题' }}</span>
            </NuxtLink>
          </p>
          <p class="mb-2 hidden text-sm text-cms-content-muted line-clamp-2 sm:block">
            <NuxtLink :to="itemHref(bean)" class="text-inherit no-underline">
              <span v-if="bean.highlightBody" v-html="bean.highlightBody" />
              <span v-else>{{ truncate(stripHtml(bean.body), 200) }}</span>
            </NuxtLink>
          </p>
          <div class="flex items-center justify-between text-xs text-cms-content-muted">
            <span class="space-x-1">
              <span v-for="(c, idx) in bean.channel?.paths ?? []" :key="c.id">
                <NuxtLink v-if="c.url" :to="c.url" class="hover:text-cms-primary">{{ c.name }}</NuxtLink>
                <span v-else>{{ c.name }}</span>
                <span v-if="idx < (bean.channel?.paths ?? []).length - 1" aria-hidden="true"> &gt; </span>
              </span>
            </span>
            <span>{{ formatDate(bean.publishDate) }}</span>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>

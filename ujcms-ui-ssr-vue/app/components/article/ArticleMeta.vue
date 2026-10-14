<script setup lang="ts">
import type { Article } from '#shared/types/api';

/**
 * Article meta row: publish date, source, author, view count. Mirrors the
 * meta strip in `templates/article.html`.
 */
defineProps<{ article: Article }>();
</script>

<template>
  <div class="flex flex-wrap items-center gap-3 border-b border-cms-border pb-2 text-sm text-cms-content-muted">
    <time :datetime="article.publishDate">{{ formatDate(article.publishDate, 'yyyy-MM-dd HH:mm') }}</time>
    <span v-if="article.source">{{ article.source }}</span>
    <span v-if="article.author">{{ article.author }}</span>
    <span aria-label="浏览量">浏览 <ArticleViewCounter :article-id="article.id" :initial-views="article.views" /></span>
  </div>
</template>

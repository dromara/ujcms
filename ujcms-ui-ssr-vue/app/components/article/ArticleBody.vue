<script setup lang="ts">
import type { Article } from '#shared/types/api';

/**
 * Article body renderer.
 *
 * Trust boundary: the backend OWASP-sanitises HTML before it lands in
 * `article.text` (see ujcms-common HtmlSanitizer config); the frontend does
 * NOT re-sanitise. If a future change loosens backend sanitisation, that
 * fix lives there, not here.
 *
 * The .article-body class scopes typography rules in assets/css/main.css —
 * paragraph spacing, image sizing, table borders, etc.
 */
defineProps<{ article: Article }>();
</script>

<template>
  <!-- Backend pre-sanitises article.text; do not pass through DOMPurify. -->
  <div v-if="article.text" class="article-body max-w-none" v-html="article.text" />
</template>

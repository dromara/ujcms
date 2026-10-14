<script setup lang="ts">
import { fetchRelatedArticles } from '~/api/article';
import type { Article, Site } from '#shared/types/api';

/**
 * Default article layout — breadcrumb + title + meta + media + body +
 * prev/next + related news rail + hot-news aside.
 *
 * Related-news rail is fetched during SSR so the rendered HTML carries it
 * (Baidu requirement). Mirrors the `[@ArticleRelatedList]` block of
 * `article.html`: hand-picked related articles first, auto-filled after.
 */
const props = defineProps<{
  article: Article;
  site: Site | null;
}>();

const imageList = computed(() => props.article.imageList ?? []);

const { data: relatedList } = await useAsyncData(
  () => `article-related-${props.article.id}`,
  () => fetchRelatedArticles(props.article.id, 5),
  { default: () => [] },
);
</script>

<template>
  <ArticleBreadcrumb :article="article" />
  <div class="mx-auto mt-6 grid max-w-6xl grid-cols-1 gap-8 px-4 lg:grid-cols-3">
    <article class="space-y-4 lg:col-span-2">
      <h1 class="text-2xl font-bold leading-tight">{{ article.title ?? '无标题' }}</h1>

      <ArticleMeta :article="article" />

      <video v-if="article.video" controls class="w-full max-w-full rounded">
        <source :src="article.video" type="video/mp4" />
        您的浏览器不支持视频(Video)标签。
      </video>
      <audio v-if="article.audio" controls class="w-full">
        <source :src="article.audio" />
        您的浏览器不支持音频(Audio)标签
      </audio>
      <div v-if="imageList.length > 0" class="space-y-3">
        <figure v-for="(image, index) in imageList" :key="image.url">
          <img :src="image.url" :alt="image.description ?? ''" class="w-full rounded" loading="lazy" />
          <figcaption class="mt-1 text-sm text-cms-content-muted">
            <strong>{{ index + 1 }}</strong
            >/{{ imageList.length }} {{ image.description }}
          </figcaption>
        </figure>
      </div>

      <ArticleBody :article="article" />

      <PrevNextLinks :article="article" />

      <div v-if="relatedList.length > 0">
        <h5 class="mt-6 border-b border-slate-200 pb-1 font-semibold">相关新闻</h5>
        <ul class="mt-2 space-y-1 text-sm">
          <li v-for="related in relatedList" :key="related.id" class="truncate">
            <NuxtLink :to="related.url ?? `/article/${related.id}`" class="text-cms-primary hover:underline">{{ related.title ?? '无标题' }}</NuxtLink>
          </li>
        </ul>
      </div>
    </article>

    <aside class="hidden space-y-6 lg:col-span-1 lg:block">
      <HotNews :site-id="site?.id" />
    </aside>
  </div>
</template>

<script setup lang="ts">
/**
 * Client island that increments + reads the per-article view count.
 *
 * Why client-side: `/api/article/view/{id}` mutates server state (increments
 * the counter) on every call. Calling it during SSR would make every render
 * bump the count — including bot prerenders. template-theme baseline fires this from
 * a `<script>` tag on mount; we mirror that behaviour with onMounted + a
 * guarded fetch.
 *
 * Falls back to the server-supplied initial value (article.views) so
 * unhydrated HTML still shows a number, not '—'.
 */
const props = defineProps<{ articleId: string; initialViews: string | number | undefined }>();

const initial = typeof props.initialViews === 'string' ? Number.parseInt(props.initialViews, 10) : props.initialViews;
const count = ref<number | undefined>(Number.isFinite(initial) ? (initial as number) : undefined);

onMounted(() => {
  fetch(`/api/article/view/${encodeURIComponent(props.articleId)}`, { credentials: 'include' })
    .then((r) => (r.ok ? (r.json() as Promise<number>) : null))
    .then((n) => {
      if (typeof n === 'number') count.value = n;
    })
    .catch(() => {
      // Fire-and-forget; the server-supplied initial value already covers SSR.
    });
});
</script>

<template>
  <span>{{ typeof count === 'number' ? count.toLocaleString('zh-CN') : '—' }}</span>
</template>

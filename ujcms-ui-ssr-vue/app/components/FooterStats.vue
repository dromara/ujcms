<script setup lang="ts">
import type { SiteViewBuffer } from '#shared/types/api';

/**
 * Live counter island for the footer.
 *
 * Spec (visit-tracking.md): "Do not render live counter values server-side"
 * — these go stale instantly. The server renders the labels + an em-dash
 * placeholder; this component populates after mount.
 */
const props = defineProps<{ siteId: string | null | undefined }>();

const online = ref<number | null>(null);
const buffer = ref<SiteViewBuffer | null>(null);

onMounted(() => {
  fetch('/api/visit/online-visitors', { credentials: 'include' })
    .then((r) => (r.ok ? (r.json() as Promise<number>) : null))
    .then((n) => {
      if (typeof n === 'number') online.value = n;
    })
    .catch(() => {});

  if (props.siteId) {
    fetch(`/api/site/view/${props.siteId}`, { credentials: 'include' })
      .then((r) => (r.ok ? (r.json() as Promise<SiteViewBuffer>) : null))
      .then((b) => {
        if (b) buffer.value = b;
      })
      .catch(() => {});
  }
});

const fmt = (n: number | null | undefined): string => (typeof n === 'number' ? n.toLocaleString('zh-CN') : '—');
</script>

<template>
  <span
    >在线访客：<span data-stat="online">{{ fmt(online) }}</span></span
  >
  <span class="ml-3"
    >今日访问量：<span data-stat="today">{{ fmt(buffer?.todayViews) }}</span></span
  >
  <span class="ml-3"
    >昨日访问量：<span data-stat="yesterday">{{ fmt(buffer?.yesterdayViews) }}</span></span
  >
  <span class="ml-3"
    >总访问量：<span data-stat="total">{{ fmt(buffer?.views) }}</span></span
  >
</template>

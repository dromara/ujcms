<script setup lang="ts">
import type { NuxtError } from '#app';

/**
 * App-level error page — covers both the 404 (not-found) and generic error
 * boundaries of the React track. Rendered without the default layout, so the
 * minimal centred card matches `app/not-found.tsx` / `app/error.tsx`.
 */
const props = defineProps<{ error: NuxtError }>();

const isNotFound = computed(() => props.error.statusCode === 404);

function reload(): void {
  clearError({ redirect: undefined });
  if (import.meta.client) window.location.reload();
}
</script>

<template>
  <main class="mx-auto max-w-3xl px-6 py-16 text-center">
    <template v-if="isNotFound">
      <h1 class="text-3xl font-semibold">404</h1>
      <p class="mt-3 text-cms-content-muted">页面不存在。</p>
    </template>
    <template v-else>
      <h1 class="text-3xl font-semibold">出错了</h1>
      <p class="mt-3 text-cms-content-muted">页面加载异常，请稍后重试。</p>
      <button type="button" class="mt-6 rounded-md bg-cms-primary px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-cms-primary/90" @click="reload">重新加载</button>
    </template>
  </main>
</template>

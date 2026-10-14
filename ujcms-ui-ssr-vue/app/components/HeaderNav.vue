<script setup lang="ts">
import type { Channel } from '#shared/types/api';

/**
 * Collapsible nav region. Mirrors `snippets/header.html`'s
 * `x-data="{ navOpen: false }"` pattern:
 *
 * - Below `xl:` (< 1280px): a hamburger button is visible; the nav region
 *   is hidden by default and drops down as an absolute-positioned panel
 *   below the header when toggled open.
 * - At `xl:` and above: the hamburger is hidden, the nav region renders
 *   inline as a row.
 *
 * The default slot is the trailing right-side block from `<AppHeader>` (the
 * search link + `<UserBlock>`). The panel auto-closes on route change (so
 * navigating from `/vote` to `/survey` collapses the dropdown without
 * leaving it stuck open).
 */
defineProps<{ channels: Channel[] }>();

const open = ref(false);
const route = useRoute();
watch(
  () => route.path,
  () => {
    open.value = false;
  },
);

// Inline icon paths in this template: Material Symbols (Google), Apache License 2.0, unmodified; see NOTICE.
</script>

<template>
  <button type="button" :aria-expanded="open" aria-label="切换导航" class="ml-auto rounded p-2 text-cms-content hover:bg-slate-100 xl:hidden" @click="open = !open">
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" class="size-6">
      <path v-if="open" d="M6.4 19L5 17.6l5.6-5.6L5 6.4L6.4 5l5.6 5.6L17.6 5L19 6.4L13.4 12l5.6 5.6l-1.4 1.4l-5.6-5.6z" />
      <path v-else d="M3 18v-2h18v2zm0-5v-2h18v2zm0-5V6h18v2z" />
    </svg>
  </button>

  <div
    :class="[
      // Mobile: absolute dropdown panel below the header bar.
      'absolute top-full right-0 left-0 z-30 flex-col items-stretch gap-1 border-b border-cms-border bg-cms-surface px-4 py-2',
      // Desktop (xl+): inline row, no panel chrome.
      'xl:static xl:flex xl:flex-1 xl:flex-row xl:items-center xl:gap-2 xl:border-0 xl:bg-transparent xl:p-0',
      // Mobile open/closed.
      open ? 'flex' : 'hidden',
    ]"
  >
    <nav aria-label="主导航" class="flex flex-1 flex-col xl:flex-row xl:items-center">
      <MainNav :channels="channels" />
    </nav>
    <slot />
  </div>
</template>

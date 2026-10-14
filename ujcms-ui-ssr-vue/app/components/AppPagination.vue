<script setup lang="ts">
/**
 * Sliding-window pagination (window of 5). Mirrors the template-theme baseline
 * `page.html` rendering. Pages compute the current page + total + base
 * path; this component handles the link math.
 *
 * Page numbers are 1-indexed externally (URL ?page=N). The PagedModel from
 * Spring uses 0-indexed `page.number`, so callers add 1 before passing.
 *
 * basePath is the route the user is on (e.g. '/channel/news'); link
 * construction appends `?page=N` (or strips it on page=1 to canonicalise).
 * Existing search params on the URL are preserved via `extraSearch`.
 */
const props = withDefaults(
  defineProps<{
    currentPage: number;
    totalPages: number;
    basePath: string;
    /** Existing query string (without leading ?), preserved across page links. */
    extraSearch?: string;
  }>(),
  { extraSearch: '' },
);

const buildHref = (page: number): string => {
  const sp = new URLSearchParams(props.extraSearch);
  if (page <= 1) {
    sp.delete('page');
  } else {
    sp.set('page', String(page));
  }
  const qs = sp.toString();
  return qs ? `${props.basePath}?${qs}` : props.basePath;
};

// Render even when totalPages <= 1 — the feature should be visibly present on
// every paginated list. First/Prev/Next/Last collapse to disabled spans and
// the window shows just the current page, so the control reads "1 / 1".
const safeTotal = computed(() => Math.max(1, props.totalPages));
const safeCurrent = computed(() => Math.min(Math.max(1, props.currentPage), safeTotal.value));
const isFirst = computed(() => safeCurrent.value <= 1);
const isLast = computed(() => safeCurrent.value >= safeTotal.value);

const pageWindow = computed<number[]>(() => {
  const windowEnd = Math.min(safeCurrent.value + 2, safeTotal.value);
  const windowStart = Math.max(windowEnd - 4, 1);
  const out: number[] = [];
  for (let p = windowStart; p <= windowEnd; p += 1) out.push(p);
  return out;
});

const linkClass = 'rounded border border-slate-300 px-3 py-1.5 hover:bg-slate-100';
const disabledClass = 'rounded border border-slate-300 px-3 py-1.5 text-slate-400';
const currentClass = 'rounded bg-cms-primary px-3 py-1.5 text-white';
</script>

<template>
  <nav aria-label="分页" class="mt-6 flex justify-center">
    <ul class="inline-flex items-center gap-1 text-sm">
      <li>
        <span v-if="isFirst" :class="disabledClass" aria-disabled="true">首页</span>
        <NuxtLink v-else :to="buildHref(1)" :class="linkClass">首页</NuxtLink>
      </li>
      <li>
        <span v-if="isFirst" :class="disabledClass" aria-disabled="true">上一页</span>
        <NuxtLink v-else :to="buildHref(safeCurrent - 1)" :class="linkClass">上一页</NuxtLink>
      </li>
      <li v-for="p in pageWindow" :key="p">
        <span v-if="p === safeCurrent" :class="currentClass" aria-current="page">{{ p }}</span>
        <NuxtLink v-else :to="buildHref(p)" :class="linkClass">{{ p }}</NuxtLink>
      </li>
      <li>
        <span v-if="isLast" :class="disabledClass" aria-disabled="true">下一页</span>
        <NuxtLink v-else :to="buildHref(safeCurrent + 1)" :class="linkClass">下一页</NuxtLink>
      </li>
      <li>
        <span v-if="isLast" :class="disabledClass" aria-disabled="true">末页</span>
        <NuxtLink v-else :to="buildHref(safeTotal)" :class="linkClass">末页</NuxtLink>
      </li>
    </ul>
  </nav>
</template>

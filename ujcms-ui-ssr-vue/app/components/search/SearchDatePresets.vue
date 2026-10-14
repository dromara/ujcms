<script setup lang="ts">
/**
 * Quick-preset date filter row for /search. Mirrors the template-theme baseline at
 * `sys_search.html` lines 60–78: a row of buttons that, on click, write
 * computed begin/end dates into the form's `<input type="date">` siblings
 * and resubmit the form.
 *
 * Why imperative DOM writes and not bound state: the UX is "preserve
 * whatever the user typed into q / channelId, just override the dates" —
 * the form is a plain GET form whose inputs are intentionally uncontrolled.
 *
 * The "全部" pill's active/inactive state is derived from the URL
 * (begin/end empty ↔ active), passed in by the parent.
 */
defineProps<{
  /** True when neither beginPublishDate nor endPublishDate is set in the URL — the "全部" pill renders active. */
  isAllActive: boolean;
}>();

const fmt = (d: Date): string => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const submitWithRange = (unit: 'year' | 'month' | 'day', n: number) => {
  const form = document.getElementById('searchForm') as HTMLFormElement | null;
  if (!form) return;
  const begin = new Date();
  if (unit === 'year') {
    begin.setFullYear(begin.getFullYear() - n);
    begin.setDate(begin.getDate() + 1);
  } else if (unit === 'month') {
    begin.setMonth(begin.getMonth() - n);
    begin.setDate(begin.getDate() + 1);
  } else {
    begin.setDate(begin.getDate() - n);
    if (n > 0) begin.setDate(begin.getDate() + 1);
  }
  const end = new Date();
  end.setDate(end.getDate() + 1);
  const beginInput = form.querySelector<HTMLInputElement>('input[name="beginPublishDate"]');
  const endInput = form.querySelector<HTMLInputElement>('input[name="endPublishDate"]');
  if (beginInput) beginInput.value = fmt(begin);
  if (endInput) endInput.value = fmt(end);
  form.submit();
};

const submitAll = () => {
  const form = document.getElementById('searchForm') as HTMLFormElement | null;
  if (!form) return;
  const beginInput = form.querySelector<HTMLInputElement>('input[name="beginPublishDate"]');
  const endInput = form.querySelector<HTMLInputElement>('input[name="endPublishDate"]');
  if (beginInput) beginInput.value = '';
  if (endInput) endInput.value = '';
  form.submit();
};

const inactiveClass = 'rounded px-2 py-1 text-cms-primary hover:bg-slate-200';
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <button v-if="isAllActive" type="button" disabled class="rounded bg-cms-primary px-2 py-1 text-white">全部</button>
    <button v-else type="button" :class="inactiveClass" @click="submitAll">全部</button>
    <button type="button" :class="inactiveClass" @click="submitWithRange('year', 1)">一年内</button>
    <button type="button" :class="inactiveClass" @click="submitWithRange('month', 1)">一月内</button>
    <button type="button" :class="inactiveClass" @click="submitWithRange('day', 7)">一周内</button>
    <button type="button" :class="inactiveClass" @click="submitWithRange('day', 0)">当天</button>
  </div>
</template>

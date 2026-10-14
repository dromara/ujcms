<script setup lang="ts">
import { fetchChannels } from '~/api/channel';

/**
 * Search form — rendered with current values from the URL pre-filled.
 * Submission is a plain GET to /search, so navigation works without JS
 * (Baidu-friendly).
 *
 * Fields: q, channelId, beginPublishDate, endPublishDate.
 *
 * Quick-preset date buttons (一年内 / 一月内 / 一周内 / 当天 / 全部) live in
 * `<SearchDatePresets>` — they mutate the date inputs and submit the form to
 * preserve any typed-but-not-submitted q/channelId. The "全部" active state
 * is computed from the URL.
 */
const props = defineProps<{
  q: string;
  channelId: string;
  beginPublishDate: string;
  endPublishDate: string;
  siteId: string | undefined;
}>();

const { data: channels } = await useAsyncData(
  () => `search-form-channels-${props.siteId ?? 'none'}`,
  () => (props.siteId ? fetchChannels({ siteId: props.siteId, isNav: true }) : Promise.resolve([])),
  { default: () => [] },
);
</script>

<template>
  <form id="searchForm" action="/search" method="get" class="space-y-3">
    <div class="flex">
      <input
        id="q"
        type="text"
        name="q"
        :value="q"
        placeholder="搜索"
        aria-label="搜索"
        class="min-w-0 flex-1 rounded-l border border-slate-300 px-3 py-2 focus:border-cms-primary focus:outline-none focus:ring-1 focus:ring-cms-primary"
      />
      <button type="submit" class="rounded-r bg-cms-primary px-4 text-white hover:opacity-90">搜索</button>
    </div>

    <div class="space-y-3 rounded border border-slate-200 bg-slate-50 p-3 text-sm">
      <div class="flex items-center gap-3">
        <label for="channelId" class="w-20 shrink-0 text-cms-content-muted">所属栏目</label>
        <select
          id="channelId"
          name="channelId"
          :value="channelId"
          class="flex-1 rounded border border-slate-300 bg-white px-2 py-1 focus:border-cms-primary focus:outline-none focus:ring-1 focus:ring-cms-primary"
        >
          <option value="">全部</option>
          <option v-for="c in channels" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>

      <div class="flex items-start gap-3">
        <span class="w-20 shrink-0 pt-1 text-cms-content-muted">时间范围</span>
        <div class="flex-1 space-y-2">
          <SearchDatePresets :is-all-active="beginPublishDate === '' && endPublishDate === ''" />
          <div class="flex flex-wrap items-center gap-2">
            <input
              id="beginPublishDate"
              type="date"
              name="beginPublishDate"
              :value="beginPublishDate"
              class="rounded border border-slate-300 bg-white px-2 py-1 focus:border-cms-primary focus:outline-none focus:ring-1 focus:ring-cms-primary"
            />
            <span class="text-cms-content-muted">至</span>
            <input
              id="endPublishDate"
              type="date"
              name="endPublishDate"
              :value="endPublishDate"
              class="rounded border border-slate-300 bg-white px-2 py-1 focus:border-cms-primary focus:outline-none focus:ring-1 focus:ring-cms-primary"
            />
            <button type="submit" class="rounded bg-cms-primary px-3 py-1 text-white hover:opacity-90">确定</button>
          </div>
        </div>
      </div>
    </div>
  </form>
</template>

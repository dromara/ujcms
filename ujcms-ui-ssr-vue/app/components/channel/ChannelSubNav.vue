<script setup lang="ts">
import type { Channel } from '#shared/types/api';

import { fetchChannels } from '~/api/channel';

/**
 * Channel sub-nav strip — "全部" + sibling channels under the same parent.
 * Mirrors the template-theme `channel.html` opening block.
 *
 * Resolution rules (matching template-theme `parent = channel.parent | default: channel`):
 *   - top-level channel (no parent): "全部" → channel itself; siblings → its
 *     own children
 *   - sub-channel: "全部" → parent channel; siblings → parent's children
 */
const props = defineProps<{ channel: Channel }>();

const parent = computed(() => props.channel.parent ?? props.channel);
const parentUrl = computed(() => parent.value.url ?? `/channel/${parent.value.slug ?? parent.value.id}`);
const isAllActive = computed(() => parent.value.id === props.channel.id);

const { data: siblings } = await useAsyncData(
  () => `channel-subnav-${parent.value.id}`,
  () => fetchChannels({ parentId: parent.value.id, isNav: true }),
  { default: () => [] },
);

const linkClass = (active: boolean): string => (active ? 'rounded bg-cms-primary px-2 py-1 text-white' : 'rounded px-2 py-1 text-cms-primary hover:bg-slate-200');
</script>

<template>
  <div class="bg-slate-100">
    <div class="mx-auto max-w-6xl px-4 py-3">
      <ol class="flex flex-wrap items-center gap-1 text-sm">
        <li>
          <NuxtLink :to="parentUrl" :class="linkClass(isAllActive)">全部</NuxtLink>
        </li>
        <li v-for="sibling in siblings" :key="sibling.id">
          <NuxtLink :to="sibling.url ?? `/channel/${sibling.slug ?? sibling.id}`" :class="linkClass(sibling.id === channel.id)">
            {{ sibling.name ?? '未命名' }}
          </NuxtLink>
        </li>
      </ol>
    </div>
  </div>
</template>

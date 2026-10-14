<script setup lang="ts">
import type { Channel } from '#shared/types/api';

/**
 * Common shell for channel pages. Renders:
 *   - Sub-nav strip (parent + siblings) — opt-out via `:show-sub-nav="false"`
 *   - Facet filter chips                 — opt-out via `:show-facets="false"`
 *   - View-counter beacon                — `/api/channel/view/{id}`
 *   - Body via the default slot
 */
const props = withDefaults(
  defineProps<{
    channel: Channel;
    searchParams: Record<string, string>;
    basePath: string;
    showFacets?: boolean;
    showSubNav?: boolean;
  }>(),
  { showFacets: true, showSubNav: true },
);
</script>

<template>
  <ChannelSubNav v-if="showSubNav" :channel="props.channel" />
  <div class="mx-auto mt-4 max-w-6xl space-y-4 px-4">
    <ChannelFacetFilter v-if="showFacets" :channel="props.channel" :search-params="searchParams" :base-path="basePath" />
    <slot />
  </div>
  <ChannelViewCounter :channel-id="props.channel.id" />
</template>

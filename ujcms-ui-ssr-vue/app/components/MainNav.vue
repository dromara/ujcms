<script setup lang="ts">
import type { Channel } from '#shared/types/api';

/**
 * Main nav — receives channels from `<AppHeader>`, uses the live route for
 * active highlighting. Active rules mirror the template-theme baseline
 * (snippets/header.html):
 *  - 首页 active when pathname === '/'
 *  - Channel item active when its rendered href matches the current
 *    pathname (or is a prefix). Covers both content channels
 *    (`/channel/{slug}`) and direct-URL channels like `/vote`, `/survey`,
 *    `/message-board`, `/form`, `/search`, `/tag` whose `channel.url` is
 *    an absolute path.
 *
 * Absolute URLs (`http(s)://...`) are reduced to their pathname so a same-
 * host match still highlights; off-site / target-blank channels are never
 * marked active.
 */
defineProps<{ channels: Channel[] }>();

const route = useRoute();

// Match the template-theme baseline `header.html` — `px-3 py-2 rounded` only,
// no `text-sm` override (the template-theme nav inherits the document base size).
const linkClass = (active: boolean) => `rounded px-3 py-2 transition-colors ${active ? 'bg-cms-primary text-white' : 'text-cms-content hover:bg-slate-100'}`;

const channelHref = (channel: Channel): string => (typeof channel.url === 'string' && channel.url ? channel.url : `/channel/${channel.slug ?? channel.id}`);

const toPathname = (raw: string): string => {
  if (/^https?:\/\//i.test(raw)) {
    try {
      return new URL(raw).pathname || '/';
    } catch {
      return raw;
    }
  }
  return raw;
};

const isChannelActive = (channel: Channel): boolean => {
  if (channel.targetBlank) return false;
  const path = toPathname(channelHref(channel));
  if (!path || path === '/') return false;
  return route.path === path || route.path.startsWith(`${path}/`);
};
</script>

<template>
  <ul class="flex flex-1 flex-col items-stretch gap-1 xl:flex-row xl:items-center xl:gap-2">
    <li>
      <NuxtLink to="/" :class="`${linkClass(route.path === '/')} block`">首页</NuxtLink>
    </li>
    <li v-for="channel in channels" :key="channel.id">
      <NuxtLink
        :to="channelHref(channel)"
        :target="channel.targetBlank ? '_blank' : undefined"
        :rel="channel.targetBlank ? 'noreferrer' : undefined"
        :class="`${linkClass(isChannelActive(channel))} block`"
      >
        {{ channel.name ?? '未命名' }}
      </NuxtLink>
    </li>
  </ul>
</template>

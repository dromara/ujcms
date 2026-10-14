<script setup lang="ts">
import type { Site } from '#shared/types/api';

import { fetchChannels } from '~/api/channel';

/**
 * Site header. Fetches the nav-channel list during SSR so initial HTML
 * contains the navigation. Hands the list to `<HeaderNav>`, which owns the
 * mobile-hamburger toggle state and renders the channel links inline on
 * desktop / dropdown on mobile.
 *
 * Mirrors `snippets/header.html`:
 *  - 50-tall logo: `site.logo` if operator-set, else `/logo.png` (a copy of
 *    the default theme's `_files/img/logo.png` shipped in public/).
 *  - SVG search icon (matches `{% include 'icon' with 'search' %}`).
 *  - `xl:` breakpoint hamburger toggling the collapsed channel list.
 */
const props = defineProps<{ site: Site | null }>();

const { data: channels } = await useAsyncData(
  () => `header-nav-channels-${props.site?.id ?? 'none'}`,
  () => (props.site?.id ? fetchChannels({ siteId: props.site.id, isNav: true }) : Promise.resolve([])),
  { default: () => [] },
);

const siteName = computed(() => props.site?.name ?? 'UJCMS');
const logoSrc = computed(() => (typeof props.site?.logo === 'string' && props.site.logo ? props.site.logo : '/logo.png'));

// Inline icon paths in this template: Material Symbols (Google), Apache License 2.0, unmodified; see NOTICE.
</script>

<template>
  <header class="border-b border-cms-border bg-cms-surface">
    <div class="relative mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
      <NuxtLink to="/" :title="siteName" class="shrink-0">
        <img :src="logoSrc" :alt="siteName" height="50" class="h-12 w-auto" />
      </NuxtLink>
      <HeaderNav :channels="channels">
        <div class="ml-auto flex items-center gap-2">
          <NuxtLink to="/search" class="rounded p-2 text-cms-content hover:bg-slate-100" aria-label="搜索">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" class="size-5">
              <path
                d="m19.6 21l-6.3-6.3q-.75.6-1.725.95T9.5 16q-2.725 0-4.612-1.888T3 9.5t1.888-4.612T9.5 3t4.613 1.888T16 9.5q0 1.1-.35 2.075T14.7 13.3l6.3 6.3zM9.5 14q1.875 0 3.188-1.312T14 9.5t-1.312-3.187T9.5 5T6.313 6.313T5 9.5t1.313 3.188T9.5 14"
              />
            </svg>
          </NuxtLink>
        </div>
      </HeaderNav>
    </div>
  </header>
</template>

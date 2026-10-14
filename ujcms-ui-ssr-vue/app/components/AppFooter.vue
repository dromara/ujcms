<script setup lang="ts">
import type { Site } from '#shared/types/api';

/**
 * Site footer. Optional site customs (company / ICP / gongan — defined by the
 * site model; the default seed ships none since 2026-08-26, the FreeMarker
 * themes read theme settings instead) are read from the resolved site and
 * rendered into initial HTML so the legal beian links are crawlable; company
 * falls back to the site name. Live counters delegate to <FooterStats>.
 */
const props = defineProps<{ site: Site | null }>();

const customs = computed(() => (props.site?.customs ?? null) as Record<string, unknown> | null);
const company = computed(() => (typeof customs.value?.company === 'string' ? customs.value.company : null));
const icp = computed(() => (typeof customs.value?.icp === 'string' ? customs.value.icp : null));
const ganganIcp = computed(() => (typeof customs.value?.gongan_icp === 'string' ? customs.value.gongan_icp : null));
const ganganCode = computed(() => (typeof customs.value?.gongan_code === 'string' ? customs.value.gongan_code : null));
const year = new Date().getFullYear();
</script>

<template>
  <footer class="mt-16 bg-slate-700 text-slate-100">
    <div class="mx-auto max-w-6xl space-y-2 px-4 py-6 text-center text-sm">
      <p>
        <a href="/sitemap.xml" class="hover:text-white">站点地图</a>
        <span class="ml-3">
          <FooterStats :site-id="site?.id ?? null" />
        </span>
      </p>
      <p>
        <span>&copy; 2021-{{ year }} {{ company ?? site?.name ?? 'UJCMS' }} 版权所有</span>
        <span class="ml-3">
          Powered by
          <a href="https://www.ujcms.com" target="_blank" rel="noreferrer" class="font-semibold hover:text-white">UJCMS</a>
        </span>
      </p>
      <p v-if="icp || ganganIcp" class="space-x-3">
        <a
          v-if="ganganIcp"
          :href="ganganCode ? `https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=${ganganCode}` : 'https://www.beian.gov.cn'"
          target="_blank"
          rel="noreferrer"
          class="hover:text-white"
        >
          {{ ganganIcp }}
        </a>
        <a v-if="icp" href="https://beian.miit.gov.cn" target="_blank" rel="noreferrer" class="hover:text-white">{{ icp }}</a>
      </p>
    </div>
  </footer>
</template>

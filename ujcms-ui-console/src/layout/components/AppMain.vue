<script setup lang="ts">
import { viewEpoch, viewKey } from '@/composables/useRouteState';
// import { computed } from 'vue';
// import { viewTabs } from './useViewTabs';

// 保留状态会导致一个页签的修改无法更新到另一个页签，需要刷新才能显示。
const includes: string[] = [];
// const includes = computed((): string[] => viewTabs.filter((it) => !it.noCache).map((it) => it.component));

// 视图重建键见 viewKey（meta.stateQuery 声明的 UI 状态 query 不参与，视图改写它们不重建、不触发过渡）；
// viewEpoch 在状态 query 被外部改写（手改地址、前进/后退、指向已打开视图的站内链接）时递增，令视图从 URL 重新初始化
</script>

<template>
  <section class="p-4">
    <router-view v-slot="{ Component, route }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive :include="includes">
          <component :is="Component" :key="`${viewKey(route)}#${viewEpoch}`" />
        </keep-alive>
      </transition>
    </router-view>
  </section>
</template>

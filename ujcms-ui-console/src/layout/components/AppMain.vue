<script setup lang="ts">
// import { computed } from 'vue';
// import { viewTabs } from './useViewTabs';

// 保留状态会导致一个页签的修改无法更新到另一个页签，需要刷新才能显示。
const includes: string[] = [];
// const includes = computed((): string[] => viewTabs.filter((it) => !it.noCache).map((it) => it.component));
</script>

<template>
  <section class="p-3">
    <router-view v-slot="{ Component, route }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive :include="includes">
          <component :is="Component" :key="route.fullPath" />
        </keep-alive>
      </transition>
    </router-view>
  </section>
</template>

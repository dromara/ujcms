<script setup lang="ts">
import { useAppStateStore } from '@/stores/appStateStore';
import { AppSidebar, AppHeader, AppTab, AppMain } from './components';
import useResizeHandler from './composables/useResizeHandler';

defineOptions({
  name: 'AppLayout',
});
useResizeHandler();
const appState = useAppStateStore();
</script>

<template>
  <div
    :class="{
      sidebarExpand: appState.sidebar,
      sidebarCollapse: !appState.sidebar,
    }"
    class="min-h-full"
  >
    <!--遮罩层。当手机模式且左边栏打开时，显示遮罩层-->
    <div v-if="appState.sidebar" class="absolute z-30 w-full h-full bg-black opacity-30 md:hidden" @click="() => appState.closeSidebar()" />
    <app-sidebar class="fixed z-40 h-full overflow-hidden duration-300 sidebar transition-width" />
    <div class="duration-300 main transition-margin">
      <app-header />
      <app-tab />
      <app-main />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.sidebarExpand .sidebar {
  @apply w-sidebar;
}
.sidebarExpand .main {
  @apply ml-0 md:ml-sidebar;
}
.sidebarCollapse .sidebar {
  @apply w-0 md:w-sidebar-collapse;
}
.sidebarCollapse .main {
  @apply ml-0 md:ml-sidebar-collapse;
}
</style>

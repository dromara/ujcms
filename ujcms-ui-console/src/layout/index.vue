<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { useAppStateStore } from '@/stores/appStateStore';
import { isEditableTarget, isPaletteShortcut, modalOpen, tinymceDialogOpen } from '@/utils/keyboard';
import { AppSidebar, AppHeader, AppTab, AppMain } from './components';
import useResizeHandler from './composables/useResizeHandler';

defineOptions({
  name: 'AppLayout',
});
useResizeHandler();
const appState = useAppStateStore();

// ⌘K / Ctrl+K 打开命令面板（console-shell D31）。输入中、EP 弹层（含面板自身）或 TinyMCE 对话框打开时不劫持；
// 只在真正打开时 preventDefault。构建器是全屏路由、不套本布局，其自有快捷键不受影响
const handleKeydown = (e: KeyboardEvent) => {
  if (!isPaletteShortcut(e)) return;
  if (isEditableTarget(e.target) || modalOpen() || tinymceDialogOpen()) return;
  e.preventDefault();
  appState.setCommandPaletteDisplay(true);
};
onMounted(() => window.addEventListener('keydown', handleKeydown));
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown));
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

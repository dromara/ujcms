<script setup lang="ts">
import { computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { isShowMenu } from '@/stores/useCurrentUser';
import { useAppStateStore } from '@/stores/appStateStore';
import MenuItem from './MenuItem.vue';
import SidebarLogo from './SidebarLogo.vue';

defineOptions({
  name: 'AppSidebar',
});
const router = useRouter();
const route = useRoute();
const appState = useAppStateStore();

// const routes = computed(() => router.options.routes);

const activeMenu = computed(() => {
  const { meta, path } = route;
  // if set path, the sidebar will highlight the path you set
  if (meta.activeMenu) {
    return meta.activeMenu as string;
  }
  return path;
});

// Cool Gray 700
const sidebarBg = '#374151';
// Cool Gray 400
const textColor = '#9CA3AF';
const activeTextColor = '#FFF';
const { routes } = router.options;
const collapse = computed(() => !appState.sidebar);
</script>

<template>
  <div :style="{ 'background-color': sidebarBg }">
    <sidebar-logo :collapse="collapse" />
    <!-- LOGO 高48px，下方再加上20px的空隙 -->
    <el-scrollbar wrap-style="height: calc(100% - 68px)">
      <!-- border-r-0 去除 el-menu 右侧浅色边框 -->
      <el-menu
        :default-active="activeMenu"
        :collapse="collapse"
        :background-color="sidebarBg"
        :text-color="textColor"
        :active-text-color="activeTextColor"
        :unique-opened="false"
        :collapse-transition="false"
        class="border-r-0"
        mode="vertical"
      >
        <menu-item v-for="item in routes.filter((item) => isShowMenu(item))" :key="item.path" :route="item" :base-path="item.path" />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

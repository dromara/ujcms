<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { RefreshRight, Close, DCaret, Download } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { viewTabs, addViewTab, updateViewTabPath, removeViewTab, removeLeftViewTab, removeRightViewTab } from './useViewTabs';
import { isExternalPath } from '@/utils/common';
import { fetchCurrentUser } from '@/stores/useCurrentUser';
import { recordRecentView } from './recentViews';

const { t } = useI18n({ useScope: 'global' });
const route = useRoute();
const router = useRouter();
const contextMenu = ref<any>();
const tabName = ref<string | number>();
const contextTabName = ref<string>();

const visible = ref<boolean>(false);
const left = ref<number>(0);
const top = ref<number>(0);
const refreshDisabled = computed(() => tabName.value !== contextTabName.value);
const closeDisabled = computed(() => viewTabs.length <= 1);
const closeLeftDisabled = computed(() => viewTabs.findIndex((it) => it.name === contextTabName.value) <= 0);
const closeRightDisabled = computed(() => viewTabs.findIndex((it) => it.name === contextTabName.value) >= viewTabs.length - 1);

// 权限快照每次 SPA 加载只取一次（permission.ts 守卫）。页签刷新是用户显式的"重新加载"意图：先重取
// backend-state，再经 /refresh 重建视图，使 onMounted 里的 perm() 判定、侧栏 isShowMenu 与路由守卫都看到最新权限
const refresh = async (name: string | number | undefined): Promise<void> => {
  if (!name) {
    return;
  }
  try {
    await fetchCurrentUser();
  } catch {
    // 401 已由请求拦截器移除令牌并弹出重新登录框；不再跳转，避免与登录重定向叠加
    return;
  }
  router.replace('/refresh');
};
const select = (name: string | number | undefined): void => {
  if (!name) {
    return;
  }
  const tab = viewTabs.find((it) => it.name === name);
  if (tab) {
    // 以字符串形式跳转：tab.path 为 fullPath，含 query；对象形式 { path } 会丢弃其中的 query
    // （以 query 定身份的视图如主题详情 site/theme 会因此退化为空地址而请求出错）
    router.push(tab.path);
  }
};
const remove = (name: string | number | undefined): void => {
  if (!name) {
    return;
  }
  if (tabName.value === name) {
    viewTabs.forEach((tab, index) => {
      if (tab.name === name) {
        const nextTab = viewTabs[index + 1] || viewTabs[index - 1];
        if (nextTab) {
          tabName.value = nextTab.name;
          select(tabName.value);
        }
      }
    });
  }
  removeViewTab(name);
};

const handleRefreshClick = (e: any) => {
  if (refreshDisabled.value) {
    e.stopPropagation();
    return;
  }
  refresh(contextTabName.value);
};
const handleCloseClick = (e: any) => {
  if (closeDisabled.value) {
    e.stopPropagation();
    return;
  }
  remove(contextTabName.value);
};
const handleCloseLeftClick = (e: any) => {
  if (closeLeftDisabled.value) {
    e.stopPropagation();
    return;
  }
  if (contextTabName.value) {
    select(contextTabName.value);
    removeLeftViewTab(contextTabName.value);
  }
};
const handleCloseRightClick = (e: any) => {
  if (closeRightDisabled.value) {
    e.stopPropagation();
    return;
  }
  if (contextTabName.value) {
    select(contextTabName.value);
    removeRightViewTab(contextTabName.value);
  }
};
const handleCloseOtherClick = (e: any) => {
  if (closeDisabled.value) {
    e.stopPropagation();
    return;
  }
  if (contextTabName.value) {
    select(contextTabName.value);
    removeLeftViewTab(contextTabName.value);
    removeRightViewTab(contextTabName.value);
  }
};

const openContextMenu = (e: any) => {
  // 页签 DOM id 为 tab-<name>；按祖先取，内部若有子元素或标题含 - 也不受影响
  const item = (e.target as HTMLElement | null)?.closest<HTMLElement>('.el-tabs__item');
  if (item?.id) {
    contextTabName.value = item.id.slice('tab-'.length);
    const menuWidth = contextMenu.value.offsetWidth;
    const clientWidth = document.documentElement.clientWidth;
    // 减 4 像素，避免紧靠边缘
    const maxLeft = clientWidth - menuWidth - 4;
    if (e.clientX > maxLeft) {
      left.value = maxLeft + document.documentElement.scrollLeft;
    } else {
      left.value = e.clientX + document.documentElement.scrollLeft;
    }
    top.value = e.clientY + document.documentElement.scrollTop;
    visible.value = true;
  }
};

const closeContextMenu = () => {
  visible.value = false;
};

watch(visible, () => {
  if (visible.value) {
    document.body.addEventListener('click', closeContextMenu);
  } else {
    document.body.removeEventListener('click', closeContextMenu);
  }
});

const handleMouseDown = (disabled: boolean, e: any): void => {
  if (disabled) {
    e.preventDefault();
  }
};

watch(
  route,
  () => {
    const title = route.meta.title;
    const component = route.name;
    if (title && component && !isExternalPath(route.path)) {
      if (viewTabs.findIndex((tab) => tab.name === title) === -1) {
        addViewTab({ name: title, label: t(title), path: route.fullPath, component: String(component), noCache: route.meta.noCache });
      } else {
        updateViewTabPath(title, route.fullPath);
      }
      tabName.value = title;
      // 最近访问（命令面板空态）：只记侧栏叶子——隐藏路由与首页（backend）不记
      if (!route.meta.hidden && route.meta.requiresPermission && route.meta.requiresPermission !== 'backend') {
        recordRecentView(String(component));
      }
    }
  },
  { immediate: true },
);

const handleTabClick = ({ paneName }: { paneName: string | number | undefined }) => {
  select(paneName);
};

const handleTabRemove = (paneName: string | number) => {
  remove(paneName);
};
</script>

<template>
  <div class="overflow-hidden bg-white border-b border-gray-200 flex items-stretch">
    <div class="flex items-stretch">
      <div class="flex items-center px-2 cursor-pointer text-gray-secondary hover:text-primary" @click="() => refresh(tabName)">
        <el-icon :title="$t('contextMenu.refresh')"><RefreshRight /></el-icon>
      </div>
    </div>
    <div class="flex-grow overflow-hidden">
      <!-- 平铺文字页签（console-shell D6）：不用 card 边框，活动项以 EP 自带的 2px 指示条标示 -->
      <el-tabs
        v-model="tabName"
        :closable="!closeDisabled"
        @tab-click="handleTabClick"
        @tab-remove="handleTabRemove"
        @contextmenu.prevent="(event: any) => openContextMenu(event)"
      >
        <el-tab-pane v-for="{ name, label } in viewTabs" :key="name" :name="name" :label="label" />
      </el-tabs>
    </div>
    <ul ref="contextMenu" :style="{ left: left + 'px', top: top + 'px', visibility: visible ? 'visible' : 'hidden' }" class="context-menu">
      <li :class="['context-menu-item', { 'is-disabled': refreshDisabled }]" @click="handleRefreshClick" @mousedown="(e) => handleMouseDown(refreshDisabled, e)">
        <el-icon class="mr-1"><RefreshRight /></el-icon>{{ $t('contextMenu.refresh') }}
      </li>
      <li :class="['context-menu-item', { 'is-disabled': closeDisabled }]" @click="handleCloseClick" @mousedown="(e) => handleMouseDown(closeDisabled, e)">
        <el-icon class="mr-1"><Close /></el-icon>{{ $t('contextMenu.close') }}
      </li>
      <li :class="['context-menu-item', { 'is-disabled': closeLeftDisabled }]" @click="handleCloseLeftClick" @mousedown="(e) => handleMouseDown(closeLeftDisabled, e)">
        <el-icon class="mr-1 rotate-90"><Download /></el-icon>{{ $t('contextMenu.closeLeft') }}
      </li>
      <li :class="['context-menu-item', { 'is-disabled': closeRightDisabled }]" @click="handleCloseRightClick" @mousedown="(e) => handleMouseDown(closeRightDisabled, e)">
        <el-icon class="mr-1 -rotate-90"><Download /></el-icon>{{ $t('contextMenu.closeRight') }}
      </li>
      <li :class="['context-menu-item', { 'is-disabled': closeDisabled }]" @click="handleCloseOtherClick" @mousedown="(e) => handleMouseDown(closeDisabled, e)">
        <el-icon class="mr-1 rotate-90"><DCaret /></el-icon>{{ $t('contextMenu.closeOther') }}
      </li>
    </ul>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-tabs__header) {
  height: auto !important;
  margin-bottom: 0;
}
// EP 的 2px 底线由容器的 border-b 承担
:deep(.el-tabs__nav-wrap::after) {
  display: none;
}
// 非 card 页签会把首尾项的内边距清零，故此处必须 !important
:deep(.el-tabs__item) {
  height: 34px;
  line-height: 34px;
  padding: 0 10px !important;
  font-size: 12px;
  font-weight: 400;
  color: theme('colors.gray.regular');
}
:deep(.el-tabs__item:hover) {
  color: theme('colors.gray.primary');
  background-color: theme('colors.gray.50');
}
:deep(.el-tabs__item.is-active) {
  color: var(--el-color-primary);
}
:deep(.el-tabs__item .is-icon-close) {
  margin-left: 2px;
}
:deep(.el-tabs__nav-prev),
:deep(.el-tabs__nav-next) {
  line-height: 34px;
}
.context-menu {
  @apply absolute z-50 bg-white text-xs text-gray-primary py-1 border rounded shadow-sm;
}
.context-menu-item.is-disabled {
  color: var(--el-text-color-disabled);
  @apply cursor-not-allowed;
}
.context-menu-item {
  @apply px-3 py-1;
}
.context-menu-item:not(.is-disabled) {
  @apply hover:bg-primary-lighter hover:text-primary  cursor-pointer;
}
</style>

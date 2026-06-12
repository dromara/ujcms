<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { RefreshRight, Close, DCaret, Download } from '@element-plus/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { viewTabs, addViewTab, removeViewTab, removeLeftViewTab, removeRightViewTab } from './useViewTabs';
import { isExternalPath } from '@/utils/common';

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

const refresh = (name: string | number | undefined): void => {
  if (!name) {
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
    router.push({ path: tab.path });
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
  if (e.srcElement.id) {
    contextTabName.value = e.srcElement.id.split('-')[1];
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
      }
      tabName.value = title;
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
  <div class="overflow-hidden shadow bg-white flex items-stretch">
    <div class="text-secondary flex items-stretch">
      <div class="flex items-center px-2 border-l border-r border-t rounded-t" @click="() => refresh(tabName)">
        <el-icon class="cursor-pointer" :title="$t('contextMenu.refresh')"><RefreshRight /></el-icon>
      </div>
    </div>
    <div class="flex-grow overflow-hidden">
      <el-tabs
        v-model="tabName"
        type="card"
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
  border-bottom: 0 !important;
}
:deep(.el-tabs__item) {
  height: 30px;
  line-height: 30px;
  padding: 0 10px !important;
  font-size: 12px;
}
:deep(.el-tabs__item .is-icon-close) {
  margin-left: 0;
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

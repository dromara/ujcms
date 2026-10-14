<script setup lang="ts">
import { computed, toRefs } from 'vue';
import { useRouter } from 'vue-router';
import { isExternalPath } from '@/utils/common';
import { isShowMenu, isCommercialLocked } from '@/stores/useCurrentUser';
import { resumePath } from '../useViewTabs';

const props = defineProps({ route: { type: Object, required: true }, basePath: { type: String, default: '' } });
const { route, basePath } = toRefs(props);
const router = useRouter();

// 是否显示。非隐藏路由即显示
const isShow = computed(() => !route.value.meta?.hidden);
// 是否子菜单。有标题且有子元素即为子菜单
const subMenu = computed(() => route.value.meta?.title && route.value.children);
// 目标路由。如果自己没有title，且有子元素，则子元素为目标路由
const targetRoute = computed(() => (!route.value.meta?.title && route.value.children?.length > 0 ? route.value.children[0] : route.value));
const icon = computed(() => targetRoute.value.meta?.icon);
const title = computed(() => targetRoute.value.meta?.title);
// 路由路径。如果targetItem是子元素，则使用子元素路由；使用自己的路由，则为空串，因为在basePath中已经指定了自己的路由
const path = computed(() => (route.value !== targetRoute.value ? targetRoute.value.path : ''));
// 社区版预览模式下被锁定的商业功能，菜单显示商业版标识（皇冠图标，悬停显示所需版本）
const locked = computed(() => isCommercialLocked(targetRoute.value.meta?.requiresPermission));

const resolvePath = (routePath: string) => {
  if (isExternalPath(routePath)) {
    return routePath;
  }
  return `${basePath.value}/${routePath}`;
};
const handleClick = () => {
  const fullPath = resolvePath(path.value);
  if (isExternalPath(fullPath)) {
    window.open(fullPath, '_blank');
  } else {
    // 该视图已有打开的页签：回到页签记录的地址，与点击页签一致；无页签即以裸地址全新打开（见 resumePath）。
    // 字符串形式跳转，保留其中的 query
    router.push(resumePath(title.value, fullPath));
  }
};
</script>

<template>
  <template v-if="isShow">
    <el-sub-menu v-if="subMenu" :index="resolvePath(path)">
      <template #title>
        <el-icon v-if="icon"><component :is="icon"></component></el-icon>
        <span>{{ $t(title) }}</span>
        <commercial-badge v-if="locked" :permission="targetRoute.meta?.requiresPermission" class="ml-1.5" />
      </template>
      <menu-item v-for="item in route.children.filter((item: any) => isShowMenu(item))" :key="item.path" :route="item" :base-path="resolvePath(item.path)" />
    </el-sub-menu>
    <el-menu-item v-else :index="resolvePath(path)" @click="handleClick">
      <el-icon v-if="icon"><component :is="icon"></component></el-icon>
      <template #title>
        <span>{{ $t(title) }}</span>
        <commercial-badge v-if="locked" :permission="targetRoute.meta?.requiresPermission" class="ml-1.5" />
      </template>
    </el-menu-item>
  </template>
</template>

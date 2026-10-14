<script setup lang="ts">
import { useHomeSections } from './useHomeSections';
import TodoCard from './components/TodoCard.vue';
import ShortcutsCard from './components/ShortcutsCard.vue';
import OverviewCards from './components/OverviewCards.vue';
import TrafficCard from './components/TrafficCard.vue';
import RecentArticlesCard from './components/RecentArticlesCard.vue';

/**
 * 控制台首页（docs/spec/console-home/plan.md）：待办 → 快捷入口 → 概览 → 流量 → 最近更新，每块占满整行。
 * 页面本身不带标题行、问候行或按钮：待办是一排胶囊（无待办时整段不渲染），要隐藏哪些区块在账号菜单
 * “外观设置”的“首页区块”里勾选（偏好 home.hidden，服务端按用户保存）——设一次就不再碰的动作不值一整行（D8）。
 * 每个区块按权限决定是否可用，区块一律 v-if（tailwind important:true 下带显示类的元素不能用 v-show 切换）。
 * 不显示“上次登录”：backend-state 里的 loginDate 是本次登录（登录成功时即写入），并非上一次。
 */
defineOptions({
  name: 'HomePage',
});
const { isShown } = useHomeSections();
</script>

<template>
  <div>
    <todo-card v-if="isShown('todo')" class="mb-3" />
    <shortcuts-card v-if="isShown('shortcuts')" class="mb-3" />
    <overview-cards v-if="isShown('overview')" class="mb-3" />
    <traffic-card v-if="isShown('traffic')" class="mb-3" />
    <recent-articles-card v-if="isShown('recent')" class="mb-3" />
  </div>
</template>

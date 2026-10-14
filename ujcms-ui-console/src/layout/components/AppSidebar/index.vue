<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import type { ScrollbarInstance } from 'element-plus';
import { isShowMenu } from '@/stores/useCurrentUser';
import { useAppStateStore } from '@/stores/appStateStore';
import { usePreferencesStore } from '@/stores/preferencesStore';
import MenuItem from './MenuItem.vue';
import SidebarLogo from './SidebarLogo.vue';

defineOptions({
  name: 'AppSidebar',
});
const router = useRouter();
const route = useRoute();
const appState = useAppStateStore();
// 侧栏风格（浅色 / 深色）是个人偏好（docs/spec/console-shell D1）：随 backend-state 播种，外观设置中即时预览
const preferences = usePreferencesStore();

// const routes = computed(() => router.options.routes);

const activeMenu = computed(() => {
  const { meta } = route;
  // 隐藏路由没有自己的菜单项，以 meta.activeMenu 指定代为高亮的菜单项（路由名，如主题详情 → 主题）
  const path = meta.activeMenu ? router.resolve({ name: meta.activeMenu }).path : route.path;
  // 菜单叶子项的 index 由 MenuItem 拼成，恒以斜杠结尾（/content/article/）；按路由名跳转或深链得到的 path 不带斜杠
  // （/content/article）。侧栏点击会回到页签记录的地址而不再归一为带斜杠的形式，故在此补齐，否则无菜单项高亮
  return path.endsWith('/') ? path : `${path}/`;
});

const { routes } = router.options;
const collapse = computed(() => !appState.sidebar);

const scrollbar = ref<ScrollbarInstance>();
// 当前项所在分组尚未展开到位：展开动画结束时须再定位一次（见 handleTransitionEnd）
let pending = false;
/**
 * 让当前菜单项进入侧栏可视区（契约见 docs/spec/console-menu/plan.md「The sidebar follows the view」）。
 *
 * 视图并不总由侧栏进入——点击页签、F5、深链、视图内按路由名跳转、前进/后退、关闭页签——el-menu 此时只会高亮
 * 并展开所在分组，不会滚动；菜单超出一屏时高亮项落在可视区之外，侧栏就失去了“当前位置”的指示作用。
 * 已完整可见则不动（点击侧栏菜单项本身不引起滚动）；否则居中，连同分组标题与相邻项一并进入视野。
 * 例外：指针正停在该项上（点击了只露出一部分的菜单项）时只补齐露出的部分，不把它从指针下移走。
 */
const revealActive = async (settled?: EventTarget | null): Promise<void> => {
  await nextTick();
  const wrap = scrollbar.value?.wrapRef;
  // 侧栏折叠时叶子项位于弹出层，改为定位其所在的一级分组
  const item = wrap?.querySelector<HTMLElement>('.el-menu-item.is-active') ?? wrap?.querySelector<HTMLElement>('.el-sub-menu.is-active > .el-sub-menu__title');
  if (!wrap || !item) {
    pending = false;
    return;
  }
  // 所在分组尚未展开（display: none）或正在展开（max-height 动画）：滚动高度尚未到位，此时滚动会被截断，
  // 动画结束后的再次定位又只会就近对齐（高亮项贴在底边）。故等展开动画结束后一次到位。
  // “已展开到位”以 transitionend 的 target（settled）认定，不看 enter-active 类：Vue 的 Transition 要等三个过渡属性
  // （max-height 与上下 padding）都结束或超时才摘类，而 padding 并无变化、不会结束，max-height 结束时类仍在
  const opening = item.closest('.el-collapse-transition-enter-active');
  pending = item.getClientRects().length === 0 || (opening != null && opening !== settled);
  if (pending) return;
  const box = wrap.getBoundingClientRect();
  const rect = item.getBoundingClientRect();
  const above = rect.top < box.top;
  if (!above && rect.bottom <= box.bottom) return;
  if (item.matches(':hover')) {
    wrap.scrollTop += above ? rect.top - box.top : rect.bottom - box.bottom;
  } else {
    wrap.scrollTop += rect.top - box.top - (box.height - rect.height) / 2;
  }
};
// 分组的展开动画（ElCollapseTransition，过渡属性 max-height）冒泡至此
const handleTransitionEnd = (event: TransitionEvent): void => {
  if (pending && event.propertyName === 'max-height') revealActive(event.target);
};
// activeMenu 只随 path 变化：视图改写自己的状态 query 不会触发。展开侧栏时分组重新展开，同样需要定位
watch([activeMenu, collapse], () => revealActive());
onMounted(() => revealActive());
</script>

<template>
  <div class="sidebar-shell" :data-sidebar-theme="preferences.sidebarTheme" @transitionend="handleTransitionEnd">
    <sidebar-logo :collapse="collapse" />
    <!-- LOGO 高48px，下方再加上20px的空隙 -->
    <el-scrollbar ref="scrollbar" wrap-style="height: calc(100% - 68px)">
      <!--
        border-r-0 去除 el-menu 右侧浅色边框；浅色时由外层 .sidebar-shell 画右边线。
        不再传 background-color / text-color / active-text-color：这三个 prop 会以内联 \-\-el-menu-* 变量写在 ul 上，
        压过一切样式表；两套风格改由下方样式按 data-sidebar-theme 直接改写 .el-menu 的变量（EP 把这些变量声明在 .el-menu 自身上，
        写在外层容器上会被它覆盖）。折叠态的子菜单弹层 teleport 到 body、不在本作用域内，且 EP 对其写死 effect=light，故恒为浅色
      -->
      <el-menu
        :default-active="activeMenu"
        :collapse="collapse"
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

<style lang="scss" scoped>
// 两套侧栏风格（docs/spec/console-shell D1）。行块：展开态内缩 8px、圆角 6px，与 AsideEntityList 的行一致；
// 基础缩进 20 → 12 使图标列位置不变；折叠态（64px）不内缩。行高 44px（EP 默认 56px）。
// 行距只用下外边距，子菜单 ul 的末行不带外边距、由 ::after 的 2px 占位块补出与下一分组的间距。
// ElCollapseTransition 以 overflow:hidden（BFC）把 ul 的 max-height 从 0 过渡到 scrollHeight：上下都有外边距时，
// 收起态（display:none）与 ul 在场时两组标题间的折叠结果差 2px（展开开头 / 收起结尾跳动）；末行带下外边距时，
// 它在动画中被 BFC 包含、平时逸出，且 Chrome 的 scrollHeight 不计它（动画终点 466、静止 468，展开结尾跳动）。
// 占位块是行内内容，任何状态下都在 ul 盒内并计入 scrollHeight，各态高度一致。
.sidebar-shell {
  height: 100%;
}
.sidebar-shell :deep(.el-menu) {
  --el-menu-item-height: 44px;
}
.sidebar-shell :deep(.el-menu:not(.el-menu--collapse)) {
  --el-menu-base-level-padding: 12px;
  .el-menu-item,
  .el-sub-menu__title {
    margin: 0 8px 2px;
    border-radius: 6px;
  }
  .el-menu--inline > :last-child {
    margin-bottom: 0;
  }
  .el-menu--inline::after {
    content: '';
    display: block;
    height: 2px;
  }
}
.sidebar-shell[data-sidebar-theme='light'] {
  // 浅色面 = 白（2026-09-22 调整两次）：白侧栏与白页头合成一个 L 形框架，围住 gray-100 的内容区，白卡片浮在其中、
  // 四周留 16px 灰边，从不与框架相接——浅色后台的标准结构。当天曾改为 gray-50 底 + gray-300 边线（想让侧栏与页头、
  // 卡片分开），但 gray-50 与页面底色 gray-100 只差六个色阶：三块近白的面只靠一条线区分，且侧栏比内容区还浅，读作又一列
  // 白卡片，同日改回白底。框架的两条线（侧栏右边线、页头下边线）同为 gray-200。LOGO 行下不画线：侧栏是一整根白色立柱，页头的下边线止于
  // 侧栏边缘（Notion、Linear 一类浅色外壳的做法）；曾试过加一条与页头下边线齐平的线，结果把 LOGO 框成一格、把外壳切成小格，
  // 且深色面并没有这条线——两种风格只该差颜色，不差结构（操作员否决，2026-09-22）。
  // 悬停用 4% 黑的透明层（白底上即 gray-100）；分组标题淡于叶子项，含当前项的分组取主题色（折叠态只剩分组图标，
  // 这是唯一的“当前位置”指示）；选中行底色主题色 light-9（控制台其它选中态同此）、文字主题色。
  background-color: #fff;
  border-right: 1px solid theme('colors.gray.200');
  :deep(.el-menu) {
    --el-menu-bg-color: #fff;
    --el-menu-text-color: theme('colors.gray.700');
    --el-menu-hover-bg-color: rgb(0 0 0 / 0.04);
    --el-menu-hover-text-color: theme('colors.gray.900');
    --el-menu-active-color: var(--el-color-primary);
  }
  :deep(.el-sub-menu__title) {
    color: theme('colors.gray.500');
  }
  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--el-color-primary);
  }
  :deep(.el-menu-item.is-active) {
    background-color: var(--el-color-primary-light-9);
  }
}
.sidebar-shell[data-sidebar-theme='dark'] {
  // Cool Gray 700 / 400：原侧栏配色
  background-color: #374151;
  :deep(.el-menu) {
    --el-menu-bg-color: #374151;
    --el-menu-text-color: #9ca3af;
    --el-menu-hover-bg-color: rgb(255 255 255 / 0.06);
    --el-menu-hover-text-color: #fff;
    --el-menu-active-color: #fff;
  }
  :deep(.el-menu-item.is-active) {
    background-color: rgb(255 255 255 / 0.1);
  }
}
</style>

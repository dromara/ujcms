<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { Fold, Expand, ArrowDown, House, Message, Bell, User, Lock, Brush, Key, Cpu, Odometer, Check, SwitchButton, Star, StarFilled, Search } from '@element-plus/icons-vue';
import { setCookieLocale } from '@/utils/common';
import { languages } from '@/i18n';
import { queryCurrentSiteList } from '@/api/login';
import { currentUser, perm, hasPermission, isUnlocked, logout } from '@/stores/useCurrentUser';
import { useAppStateStore } from '@/stores/appStateStore';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { useMessageStore } from '@/stores/messageStore';
import { useNotificationStore } from '@/stores/notificationStore';
import { SHORTCUTS_MAX, usePreferencesStore } from '@/stores/preferencesStore';
import { paletteHint } from '@/utils/keyboard';
import BreadCrumb from '@/components/BreadCrumb/index.vue';
import PasswordForm from '@/views/personal/PasswordForm.vue';
import AppearanceForm from '@/views/personal/AppearanceForm.vue';
import CommandPalette from './CommandPalette.vue';
import { isMenuLeafRoute, type CommandAction } from './commandIndex';
import GeneratedKey from '@/views/personal/GeneratedKey.vue';

// 系统信息 / 系统监控两个对话框依赖 echarts（含 zrender 约占入口包四成），这里按需异步加载，
// 使其与统计页共用独立 chunk，而不是随布局头部一起打进入口 chunk。对话框本身仍常驻挂载，行为不变。
const SystemInfo = defineAsyncComponent(() => import('@/views/personal/SystemInfo.vue'));
const SystemMonitor = defineAsyncComponent(() => import('@/views/personal/SystemMonitor.vue'));

const { t, locale } = useI18n({ useScope: 'global' });

const appState = useAppStateStore();
const siteList = ref<any[]>([]);
const currentSiteStore = useCurrentSiteStore();
const { getCurrentSiteId, setCurrentSiteId } = currentSiteStore;
const { currentSite } = storeToRefs(currentSiteStore);
const fetchSiteList = async () => {
  siteList.value = await queryCurrentSiteList();
  currentSite.value = siteList.value.find((item) => item.id === getCurrentSiteId()) ?? siteList.value[0];
};
const route = useRoute();
const router = useRouter();
const changeSiteId = (id: string) => {
  setCurrentSiteId(id);
  // 视图记在地址里的导航上下文（栏目、目录、公众号、所选站点……见 docs/spec/console-view-state）属于原站点：
  // 切换站点时从地址中清除，视图随重载全新打开（切换语言则保留）。直接改写地址栏而不经路由跳转——
  // 跳转会让视图在重载前先重建一次、多发一轮请求
  const stateQuery = route.meta.stateQuery ?? [];
  if (stateQuery.some((key) => key in route.query)) {
    const query = Object.fromEntries(Object.entries(route.query).filter(([key]) => !stateQuery.includes(key)));
    window.history.replaceState(window.history.state, '', router.resolve({ path: route.path, query, hash: route.hash }).href);
  }
  window.location.reload();
};
const messageStore = useMessageStore();
const notificationStore = useNotificationStore();
onMounted(() => {
  fetchSiteList();
  if (hasPermission('message:list')) {
    messageStore.startPolling();
  }
  if (hasPermission('notification:page')) {
    notificationStore.startPolling();
  }
});
onUnmounted(() => {
  messageStore.stopPolling();
  notificationStore.stopPolling();
});

// 切换语言后整页重载：TinyMCE 语言包、bpmn-js 翻译模块、dayjs 语言、图表系列名等均在初始化时读取一次，
// 不随 locale 响应式更新；启动时 i18n 按 cookie 取语言
const changeLocale = (lang: string) => {
  setCookieLocale(lang);
  window.location.reload();
};
const handleLogout = () => {
  logout();
  // router.push(`/login?redirect=${route.fullPath}`);
  window.location.reload();
};

const passwordFormVisible = ref<boolean>(false);
const appearanceFormVisible = ref<boolean>(false);

// 页头左侧是导航辅助（console-shell D31 / D32）：☆ 把当前侧栏叶子加入 / 移出快捷入口（偏好 `home.shortcuts`，服务端；与首页快捷入口、命令面板共用一份列表），搜索胶囊与 ⌘K 打开命令面板
const preferences = usePreferencesStore();
const isShortcut = computed(() => preferences.home.shortcuts.includes(String(route.name)));
const toggleShortcut = async () => {
  const result = await preferences.toggleShortcut(String(route.name));
  if (result === 'full') {
    ElMessage.warning(t('shell.favorite.full', { max: SHORTCUTS_MAX }));
  }
};
const openPalette = () => appState.setCommandPaletteDisplay(true);
// 面板的个人项来源：账号菜单里开对话框的两项 + 两个收件箱（隐藏路由，侧栏遍历不到）
const paletteActions: CommandAction[] = [
  { id: 'password', title: 'changePassword', perm: 'password:update', run: () => (passwordFormVisible.value = true) },
  { id: 'appearance', title: 'menu.personal.appearance', run: () => (appearanceFormVisible.value = true) },
  { id: 'message', title: 'menu.personal.message', perm: 'message:page', name: 'MessageList' },
  { id: 'notification', title: 'menu.personal.notification', perm: 'notification:page', name: 'NotificationList' },
];
const systemInfoVisible = ref<boolean>(false);
const systemMonitorVisible = ref<boolean>(false);
const generatedKeyVisible = ref<boolean>(false);
</script>

<template>
  <div class="flex items-center justify-between h-12 overflow-hidden bg-white border-b border-gray-200">
    <div class="flex items-center h-full">
      <div class="flex items-center h-full px-4 align-middle cursor-pointer hover:bg-gray-100" @click="() => appState.toggleSidebar()">
        <el-icon :size="18"><component :is="appState.sidebar ? Fold : Expand"></component></el-icon>
      </div>
      <bread-crumb class="inline-block" />
      <el-tooltip v-if="isMenuLeafRoute(route)" :content="$t(isShortcut ? 'shell.favorite.remove' : 'shell.favorite.add')" :trigger-keys="[]" placement="bottom">
        <!-- 图标在按钮内以 flex 居中：作为行内内容它会坐在 24px 行框的基线上，视觉中心比面包屑文字高 2px -->
        <button
          type="button"
          class="inline-flex items-center justify-center w-6 h-6 ml-1 rounded hover:text-warning"
          :class="isShortcut ? 'text-warning' : 'text-gray-secondary'"
          :aria-pressed="isShortcut"
          @click="toggleShortcut"
        >
          <el-icon :size="16"><component :is="isShortcut ? StarFilled : Star" /></el-icon>
        </button>
      </el-tooltip>
      <button
        type="button"
        class="hidden md:inline-flex items-center h-7 ml-3 px-3 rounded-full border border-gray-200 bg-gray-50 text-xs text-gray-secondary hover:border-gray-300 hover:text-gray-regular"
        @click="openPalette"
      >
        <el-icon :size="14"><Search /></el-icon>
        <span class="ml-1">{{ $t('shell.search') }}</span>
        <kbd class="ml-2 px-1 rounded border border-gray-200 bg-white font-sans text-gray-placeholder">{{ paletteHint }}</kbd>
      </button>
      <button type="button" class="md:hidden inline-flex items-center h-full px-3 hover:bg-gray-100" :title="$t('shell.search')" @click="openPalette">
        <el-icon :size="18"><Search /></el-icon>
      </button>
    </div>
    <!--
      页头右侧分两段（console-menu「The header」）：先是站点上下文——网站首页图标（常用，须一键直达，不进菜单）与站点名下拉（切换站点）；
      竖线之后是个人项——我的私信、我的通知（D26 / D28：个人收件箱没有侧栏菜单，未读角标必须常显，故是图标）
      与账号菜单（修改密码 · 外观设置 · 密钥生成器 | 系统信息 · 系统监控 | 语言 | 退出）。语言与外观都是个人偏好，
      前者每次会话至多切换一次、后者设一次就不再碰，故都收进账号菜单而不占页头一格（console-shell D33）
    -->
    <div class="flex items-center h-full text-gray-regular">
      <a v-if="currentSite" :href="currentSite.fullUrl" target="_blank" rel="noopener" class="inline-flex items-center h-full px-3 hover:bg-gray-100" :title="$t('siteHome')">
        <el-icon :size="18"><House /></el-icon>
      </a>
      <el-dropdown v-if="currentSite" class="h-full">
        <div class="flex items-center h-full px-3 hover:bg-gray-100">
          {{ currentSite.name }}
          <el-icon :size="12" class="ml-1 text-gray-secondary"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="item in siteList" :key="item.id" :disabled="currentSite?.id === item.id" @click="() => changeSiteId(item.id)">
              <span :style="{ 'margin-left': item.depth * 16 + 'px' }">{{ item.name }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-divider v-if="currentSite" direction="vertical" class="mx-1" />
      <router-link
        v-if="hasPermission('message:page')"
        :to="{ name: 'MessageList' }"
        class="inline-flex items-center h-full px-3 hover:bg-gray-100"
        active-class="text-primary"
        :title="$t('menu.personal.message')"
      >
        <el-badge :value="messageStore.unreadCount" :hidden="messageStore.unreadCount <= 0" :max="99" class="flex">
          <el-icon :size="18"><Message /></el-icon>
        </el-badge>
      </router-link>
      <router-link
        v-if="hasPermission('notification:page')"
        :to="{ name: 'NotificationList' }"
        class="inline-flex items-center h-full px-3 hover:bg-gray-100"
        active-class="text-primary"
        :title="$t('menu.personal.notification')"
      >
        <el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount <= 0" :max="99" class="flex">
          <el-icon :size="18"><Bell /></el-icon>
        </el-badge>
      </router-link>
      <el-dropdown class="h-full">
        <div class="flex items-center h-full px-3 hover:bg-gray-100">
          <el-avatar :size="24" :src="currentUser.avatar"
            ><el-icon :size="14"><User /></el-icon
          ></el-avatar>
          <span class="ml-2">{{ currentUser.username }}</span>
          <el-icon :size="12" class="ml-1 text-gray-secondary"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :disabled="perm('password:update')" @click="() => (passwordFormVisible = true)">
              <el-icon><Lock /></el-icon>{{ $t('changePassword') }}
            </el-dropdown-item>
            <!-- 外观设置：每个后台用户都可设自己的偏好（接口只要求 backend），不设权限码 -->
            <el-dropdown-item @click="() => (appearanceFormVisible = true)">
              <el-icon><Brush /></el-icon>{{ $t('menu.personal.appearance') }}
            </el-dropdown-item>
            <el-dropdown-item :disabled="perm('homepage:generatedKey')" @click="() => (generatedKeyVisible = true)">
              <el-icon><Key /></el-icon>{{ $t('menu.personal.homepage.generatedKey') }}
            </el-dropdown-item>
            <el-dropdown-item divided :disabled="perm('homepage:systemInfo')" @click="() => (systemInfoVisible = true)">
              <el-icon><Cpu /></el-icon>{{ $t('menu.personal.homepage.systemInfo') }}
            </el-dropdown-item>
            <el-dropdown-item v-if="hasPermission('homepage:systemMonitor') && isUnlocked('homepage:systemMonitor')" @click="() => (systemMonitorVisible = true)">
              <el-icon><Odometer /></el-icon>{{ $t('menu.personal.homepage.systemMonitor') }}
            </el-dropdown-item>
            <!-- 当前语言打勾且不可点；其余语言点击后整页重载 -->
            <el-dropdown-item v-for="(name, lang, index) in languages" :key="lang" :divided="index === 0" :disabled="locale === lang" @click="() => changeLocale(lang)">
              <el-icon :class="{ invisible: locale !== lang }"><Check /></el-icon>{{ name }}
            </el-dropdown-item>
            <el-dropdown-item divided @click="() => handleLogout()"
              ><el-icon><SwitchButton /></el-icon>{{ $t('logout') }}</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <password-form v-model="passwordFormVisible" />
    <appearance-form v-model="appearanceFormVisible" />
    <command-palette :actions="paletteActions" />
    <system-info v-if="hasPermission('homepage:systemInfo')" v-model="systemInfoVisible"></system-info>
    <system-monitor v-if="hasPermission('homepage:systemMonitor') && isUnlocked('homepage:systemMonitor')" v-model="systemMonitorVisible"></system-monitor>
    <generated-key v-if="hasPermission('homepage:generatedKey')" v-model="generatedKeyVisible"></generated-key>
  </div>
</template>

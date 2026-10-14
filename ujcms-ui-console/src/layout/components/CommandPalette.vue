<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { Search } from '@element-plus/icons-vue';
import { useAppStateStore } from '@/stores/appStateStore';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { buildCommandIndex, searchCommands, type CommandAction, type CommandEntry } from './commandIndex';
import { loadOtherLocaleLabels } from './commandLabels';
import { readRecentViews } from './recentViews';
import { resumePath } from './useViewTabs';
import { requestSettingsField } from '@/views/config/settingsFocus';

/**
 * 命令面板（docs/spec/console-shell D31–D32、D8）：页头搜索胶囊或 ⌘K / Ctrl+K 打开；空态列出常用（偏好里的快捷入口）与最近访问，
 * 输入即按两种语言的文案子串匹配侧栏叶子、设置页签、页签内的设置项与个人项；↑↓ 选择、Enter 打开、Esc 关闭。
 * 选中设置项：打开所属设置页签并滚动到该项（settingsFocus.ts）。
 */
defineOptions({
  name: 'CommandPalette',
});
const props = defineProps<{ actions: CommandAction[] }>();
const { t, locale } = useI18n({ useScope: 'global' });
const router = useRouter();
const appState = useAppStateStore();
const preferences = usePreferencesStore();

const visible = computed<boolean>({
  get: () => appState.commandPaletteDisplay,
  set: (value) => appState.setCommandPaletteDisplay(value),
});
const input = ref<any>();
const listEl = ref<HTMLElement>();
const query = ref<string>('');
const index = ref<CommandEntry[]>([]);
const active = ref<number>(0);

// 每次打开重建索引（很便宜）：页签刷新会重取权限，面板须与之同步。
// 键不存在时 t() 原样返回键名（如没有提示文案的设置项的 .tooltip），须给空串，否则键名本身会被当作匹配串
const handleOpen = async () => {
  query.value = '';
  active.value = 0;
  const other = await loadOtherLocaleLabels(locale.value);
  index.value = buildCommandIndex(router.options.routes, props.actions, (key) => {
    const own = t(key);
    return [own === key ? '' : own, other[key] ?? ''];
  });
};

interface Row {
  entry: CommandEntry;
  header?: string;
}
const byId = (id: string): CommandEntry | undefined => index.value.find((entry) => entry.id === id);
const rows = computed<Row[]>(() => {
  if (query.value.trim()) {
    return searchCommands(index.value, query.value).map((entry) => ({ entry }));
  }
  // 空态：常用（偏好，按存储顺序）+ 最近访问（本地，去掉已在常用里的；无权限或已不存在的路由名在索引里找不到，自然丢弃）
  const shortcuts = preferences.home.shortcuts;
  const frequent = shortcuts.map((name) => byId(`route:${name}`)).filter((entry): entry is CommandEntry => entry != null);
  const recent = readRecentViews()
    .filter((name) => !shortcuts.includes(name))
    .map((name) => byId(`route:${name}`))
    .filter((entry): entry is CommandEntry => entry != null);
  return [
    ...frequent.map((entry, i) => ({ entry, header: i === 0 ? t('shell.search.frequent') : undefined })),
    ...recent.map((entry, i) => ({ entry, header: i === 0 ? t('shell.search.recent') : undefined })),
  ];
});

const move = async (delta: number) => {
  const count = rows.value.length;
  if (count === 0) return;
  active.value = (active.value + delta + count) % count;
  await nextTick();
  listEl.value?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView({ block: 'nearest' });
};
const run = (entry: CommandEntry) => {
  visible.value = false;
  if (entry.kind === 'action') {
    entry.run?.();
    return;
  }
  if (entry.kind === 'settings' || entry.kind === 'field') {
    // type 是设置页的身份（console-view-state）：显式深链；已打开的设置视图由 viewEpoch 按新地址重建。
    // 设置项先登记定位目标再跳转：视图挂载后（或已停在该页签时立即）滚动到该项
    if (entry.kind === 'field' && entry.name && entry.query?.type && entry.field) {
      requestSettingsField({ name: entry.name, type: entry.query.type, key: entry.field });
    }
    router.push({ name: entry.name, query: entry.query });
    return;
  }
  // 与侧栏一致：该视图的页签开着就回到页签记录的地址（resumePath），否则裸地址全新打开；锁定的商业功能由路由守卫接管
  router.push(resumePath(entry.title, router.resolve({ name: entry.name }).path));
};
const runActive = () => {
  const row = rows.value[active.value];
  if (row) run(row.entry);
};
</script>

<template>
  <el-dialog v-model="visible" width="560px" align-center :show-close="false" append-to-body class="command-palette" @open="handleOpen" @opened="() => input?.focus()">
    <el-input
      ref="input"
      v-model="query"
      :placeholder="$t('shell.search.placeholder')"
      :prefix-icon="Search"
      clearable
      @input="() => (active = 0)"
      @keydown.down.prevent="() => move(1)"
      @keydown.up.prevent="() => move(-1)"
      @keydown.enter.prevent="runActive"
    />
    <div ref="listEl" class="mt-3 -mx-2 max-h-80 overflow-y-auto">
      <template v-for="(row, i) in rows" :key="row.entry.id">
        <div v-if="row.header" class="px-2 pt-2 pb-1 text-xs text-gray-secondary">{{ row.header }}</div>
        <div
          class="flex items-center px-2 py-2 rounded cursor-pointer text-sm"
          :class="i === active ? 'bg-primary-lighter text-primary' : 'text-gray-primary hover:bg-gray-50'"
          :data-active="i === active"
          @mouseenter="() => (active = i)"
          @click="() => run(row.entry)"
        >
          <span class="truncate">{{ $t(row.entry.title) }}</span>
          <commercial-badge v-if="row.entry.locked" class="ml-1.5" />
          <span class="flex-1 ml-3 text-xs text-gray-secondary truncate">{{ row.entry.trail.map((key) => $t(key)).join(' › ') }}</span>
          <kbd v-if="i === active" class="ml-2 px-1 text-xs rounded border border-gray-200 bg-white text-gray-placeholder">↵</kbd>
        </div>
      </template>
      <div v-if="rows.length === 0" class="px-2 py-6 text-center text-sm text-gray-secondary">
        {{ query.trim() ? $t('shell.search.empty') : $t('shell.search.placeholder') }}
      </div>
    </div>
    <div class="mt-2 text-xs text-gray-placeholder">{{ $t('shell.search.hint') }}</div>
  </el-dialog>
</template>

<style lang="scss">
// 对话框 teleport 到 body，作用域样式够不着：无标题，隐藏头部；正文贴紧
.command-palette .el-dialog__header {
  display: none;
}
.command-palette .el-dialog__body {
  padding: 12px 16px;
}
</style>

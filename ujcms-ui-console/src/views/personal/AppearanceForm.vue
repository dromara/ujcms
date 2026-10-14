<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { usePreferencesStore, type SidebarTheme } from '@/stores/preferencesStore';
import type { ThemeColor } from '@/utils/theme';
import ThemeColorSwatches from '@/components/ThemeColorSwatches.vue';
import { HOME_SECTIONS, type HomeSection, type HomeSectionId } from '@/views/home/useHomeSections';

/**
 * 外观设置（docs/spec/console-shell S2、D7）：侧栏风格、主题色、首页区块。改动即时预览（首页在对话框后面随勾选增减区块），
 * 确定落库，取消 / 关闭回放打开时的快照。外观两项未自选时跟随管理员在“全局设置 › 基础设置”指定的控制台默认外观：
 * 风格与色块按有效值高亮，点选即成自选值，“恢复默认”把两项清空（null）回到跟随。首页区块原是首页上的“自定义首页”按钮，
 * 设一次就不再碰的动作与侧栏风格同类，归入账号菜单而不占首页一行（console-home D8）。
 */
defineOptions({
  name: 'AppearanceForm',
});
defineProps({ modelValue: { type: Boolean, required: true } });
const emit = defineEmits({ 'update:modelValue': null });
const { t } = useI18n();
const preferences = usePreferencesStore();

const SIDEBAR_THEMES: SidebarTheme[] = ['light', 'dark'];
// 快照存自选值（null = 跟随默认），回放时原样写回，跟随状态也一并复原
const snapshot = ref<{ sidebarTheme: SidebarTheme | null; themeColor: ThemeColor | null; hidden: string[] }>({ sidebarTheme: null, themeColor: null, hidden: [] });
// 当前用户可用的首页区块（有权限看到内容的）；看不到的区块保留其原有隐藏标记，不在此列出
const sections = ref<HomeSection[]>([]);
const buttonLoading = ref<boolean>(false);
let confirmed = false;

const handleOpen = () => {
  snapshot.value = { sidebarTheme: preferences.own.sidebarTheme, themeColor: preferences.own.themeColor, hidden: [...preferences.home.hidden] };
  sections.value = HOME_SECTIONS.filter((section) => section.visible());
  confirmed = false;
};
const isSectionShown = (id: HomeSectionId): boolean => !preferences.home.hidden.includes(id);
const toggleSection = (id: HomeSectionId, shown: boolean) => {
  const hidden = shown ? preferences.home.hidden.filter((it) => it !== id) : [...new Set([...preferences.home.hidden, id])];
  preferences.preview({ home: { hidden } });
};
// 遮罩、Esc、取消：未确定即回放快照
const handleClose = () => {
  if (!confirmed) {
    preferences.preview({ sidebarTheme: snapshot.value.sidebarTheme, themeColor: snapshot.value.themeColor, home: { hidden: snapshot.value.hidden } });
  }
};
const handleSubmit = async () => {
  buttonLoading.value = true;
  try {
    await preferences.save({ sidebarTheme: preferences.own.sidebarTheme, themeColor: preferences.own.themeColor, home: { hidden: preferences.home.hidden } });
    confirmed = true;
    ElMessage.success(t('success'));
    emit('update:modelValue', false);
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    :title="$t('menu.personal.appearance')"
    :model-value="modelValue"
    width="520px"
    @update:model-value="(event: boolean) => $emit('update:modelValue', event)"
    @open="handleOpen"
    @close="handleClose"
  >
    <div class="text-sm text-gray-regular">{{ $t('shell.appearance.sidebar') }}</div>
    <div class="flex gap-4 mt-2" role="radiogroup" :aria-label="$t('shell.appearance.sidebar')">
      <button
        v-for="theme in SIDEBAR_THEMES"
        :key="theme"
        type="button"
        role="radio"
        :aria-checked="preferences.sidebarTheme === theme"
        class="flex flex-col items-center p-2 rounded-lg border"
        :class="preferences.sidebarTheme === theme ? 'border-primary bg-primary-lighter' : 'border-gray-200 hover:border-gray-300'"
        @click="() => preferences.preview({ sidebarTheme: theme })"
      >
        <!-- 迷你预览：侧栏条 + 页头 + 内容区 -->
        <span class="flex w-24 h-16 overflow-hidden rounded border border-gray-200 bg-gray-100">
          <span class="w-6 h-full" :class="theme === 'dark' ? 'bg-gray-700' : 'bg-white border-r border-gray-200'"></span>
          <span class="flex-1">
            <span class="block h-3 bg-white border-b border-gray-200"></span>
            <span class="block w-12 h-4 mx-2 mt-2 rounded bg-white border border-gray-200"></span>
          </span>
        </span>
        <span class="mt-2 text-xs" :class="preferences.sidebarTheme === theme ? 'text-primary' : 'text-gray-regular'">{{ $t(`shell.appearance.sidebar.${theme}`) }}</span>
      </button>
    </div>
    <div class="mt-5 text-sm text-gray-regular">{{ $t('shell.appearance.themeColor') }}</div>
    <theme-color-swatches class="mt-2" :model-value="preferences.themeColor" :label="$t('shell.appearance.themeColor')" @update:model-value="(color) => preferences.preview({ themeColor: color })" />
    <!-- 跟随安装默认时提示来源；已自选时给“恢复默认”（清空两项，随确定落库） -->
    <div class="flex items-center h-6 mt-3 text-xs text-gray-secondary">
      <template v-if="preferences.followsDefault">{{ $t('shell.appearance.followDefault') }}</template>
      <el-button v-else link type="primary" size="small" @click="() => preferences.preview({ sidebarTheme: null, themeColor: null })">{{ $t('shell.appearance.reset') }}</el-button>
    </div>
    <template v-if="sections.length > 0">
      <div class="mt-5 text-sm text-gray-regular">{{ $t('shell.appearance.home') }}</div>
      <div v-for="section in sections" :key="section.id" class="flex items-center h-8 mt-1">
        <el-checkbox :model-value="isSectionShown(section.id)" @change="(checked: any) => toggleSection(section.id, !!checked)">{{ $t(section.titleKey) }}</el-checkbox>
        <span class="ml-3 text-xs text-gray-secondary">{{ $t(section.descKey) }}</span>
      </div>
    </template>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="handleSubmit">{{ $t('ok') }}</el-button>
    </template>
  </el-dialog>
</template>

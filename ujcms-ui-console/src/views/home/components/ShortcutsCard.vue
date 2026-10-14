<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { DEFAULT_SHORTCUTS, resolveShortcuts } from '../menuLeaves';
import HomeCard from './HomeCard.vue';
import ShortcutPickerDialog from './ShortcutPickerDialog.vue';

/** 快捷入口：偏好里的常用功能（页头 ☆ 与命令面板共用同一列表）；未设则取默认列表，均按可见性过滤。整行一卡：xl 下八格一行、其余四格一行，默认的八项在两档都排满 */
defineOptions({
  name: 'ShortcutsCard',
});
const router = useRouter();
const preferences = usePreferencesStore();
const pickerVisible = ref<boolean>(false);
const items = computed(() => {
  const own = resolveShortcuts(preferences.home.shortcuts);
  return own.length > 0 ? own : resolveShortcuts(DEFAULT_SHORTCUTS);
});
</script>

<template>
  <home-card :title="$t('home.shortcuts')">
    <template #action>
      <el-button link type="primary" size="small" @click="() => (pickerVisible = true)">{{ $t('edit') }}</el-button>
    </template>
    <div v-if="items.length > 0" class="grid grid-cols-4 xl:grid-cols-8 gap-2">
      <button
        v-for="item in items"
        :key="item.name"
        type="button"
        class="flex flex-col items-center justify-center py-3 rounded-md border border-gray-200 text-gray-primary hover:border-primary hover:text-primary"
        @click="() => router.push({ name: item.name })"
      >
        <el-icon v-if="item.icon" :size="22"><component :is="item.icon" /></el-icon>
        <span class="mt-1.5 text-sm">{{ $t(item.titleKey) }}</span>
        <span class="text-xs text-gray-secondary">{{ $t(item.groupTitleKey) }}</span>
      </button>
    </div>
    <div v-else class="py-3 text-sm text-gray-secondary">{{ $t('home.shortcuts.empty') }}</div>
    <shortcut-picker-dialog v-model="pickerVisible" :selected="items.map((item) => item.name)" />
  </home-card>
</template>

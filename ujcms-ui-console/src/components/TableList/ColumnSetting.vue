<script setup lang="ts">
import { toRefs, computed, ref } from 'vue';
import { Setting } from '@element-plus/icons-vue';
import { useColumnSettingsStore, mergeColumns, ColumnState } from '@/stores/columnSettingsStore';

const props = defineProps({ name: { type: String, required: true } });
const { name } = toRefs(props);
const settingsStore = useColumnSettingsStore();
const settings = computed<ColumnState[]>(() => settingsStore.getCurrentSettings(name.value));
const origins = computed<ColumnState[]>(() => settingsStore.getOriginSettings(name.value));
const merges = computed<ColumnState[]>(() => mergeColumns(settings.value, origins.value));
const visible = ref<boolean>(false);
const resetColumns = () => {
  settingsStore.setCurrentSettings(name.value, mergeColumns([], origins.value));
};
</script>

<template>
  <div class="inline-flex align-middle">
    <el-tooltip :content="$t('table.columnsSetting')" placement="top">
      <el-icon class="text-lg cursor-pointer text-gray-regular" @click="() => (visible = true)"><Setting /></el-icon>
    </el-tooltip>
    <el-drawer v-model="visible" :title="$t('table.columnsSetting')" :size="375">
      <el-button class="mb-1" round @click="resetColumns">{{ $t('reset') }}</el-button>
      <ul>
        <li v-for="(column, index) in merges" :key="column.title" :divided="index === 0">
          <el-checkbox v-model="column.display">{{ column.title }}</el-checkbox>
        </li>
      </ul>
    </el-drawer>
  </div>
</template>

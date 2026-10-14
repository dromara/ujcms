<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { Rank, Close } from '@element-plus/icons-vue';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { listMenuLeaves, MAX_SHORTCUTS, type MenuGroup, type MenuLeaf } from '../menuLeaves';

/** 快捷入口挑选：左侧按侧栏分组勾选可见叶子（最多 MAX_SHORTCUTS 项），右侧拖拽排序；确定即落库 */
defineOptions({
  name: 'ShortcutPickerDialog',
});
const props = defineProps<{ modelValue: boolean; selected: string[] }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const { t } = useI18n();
const preferences = usePreferencesStore();
const groups = ref<MenuGroup[]>([]);
// 拖拽改写的是本地副本，不直接改偏好
const picked = ref<string[]>([]);
const buttonLoading = ref<boolean>(false);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      groups.value = listMenuLeaves();
      picked.value = [...props.selected];
    }
  },
);
const leafOf = (name: string): MenuLeaf | undefined => groups.value.flatMap((group) => group.leaves).find((leaf) => leaf.name === name);
const toggle = (name: string, checked: boolean) => {
  if (checked) {
    if (!picked.value.includes(name) && picked.value.length < MAX_SHORTCUTS) picked.value.push(name);
  } else {
    picked.value = picked.value.filter((it) => it !== name);
  }
};
const handleSubmit = async () => {
  buttonLoading.value = true;
  try {
    await preferences.save({ home: { shortcuts: picked.value } });
    ElMessage.success(t('success'));
    emit('update:modelValue', false);
  } finally {
    buttonLoading.value = false;
  }
};
</script>

<template>
  <el-dialog :model-value="modelValue" :title="$t('home.shortcuts.edit')" width="880px" top="5vh" @update:model-value="(value: boolean) => $emit('update:modelValue', value)">
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="max-h-96 overflow-y-auto pr-2">
          <div v-for="group in groups" :key="group.titleKey" class="mb-3">
            <div class="mb-1 text-xs text-gray-placeholder">{{ $t(group.titleKey) }}</div>
            <div class="grid grid-cols-2 gap-x-3">
              <el-checkbox
                v-for="leaf in group.leaves"
                :key="leaf.name"
                :model-value="picked.includes(leaf.name)"
                :disabled="!picked.includes(leaf.name) && picked.length >= MAX_SHORTCUTS"
                @change="(checked: any) => toggle(leaf.name, !!checked)"
              >
                {{ $t(leaf.titleKey) }}
              </el-checkbox>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="mb-2 text-xs text-gray-secondary">{{ $t('home.shortcuts.selected', { count: picked.length, max: MAX_SHORTCUTS }) }}</div>
        <draggable v-model="picked" :item-key="(name: string) => name" handle=".drag-handle" ghost-class="opacity-50" :animation="200">
          <!-- item 插槽须恰为一个原生元素（vuedraggable 在其 el 上登记拖拽上下文） -->
          <template #item="{ element }">
            <div class="flex items-center h-9 px-2 mb-1 rounded-md border border-gray-200 text-sm text-gray-primary">
              <el-icon class="drag-handle cursor-move mr-2 text-gray-placeholder"><Rank /></el-icon>
              <el-icon v-if="leafOf(element)?.icon" class="mr-1.5 text-gray-secondary"><component :is="leafOf(element)?.icon" /></el-icon>
              <span class="flex-1 truncate">{{ $t(leafOf(element)?.titleKey ?? element) }}</span>
              <button type="button" class="p-0.5 text-gray-placeholder hover:text-danger" :title="$t('delete')" @click="() => toggle(element, false)">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </template>
        </draggable>
        <div v-if="picked.length === 0" class="py-3 text-sm text-gray-secondary">{{ $t('home.shortcuts.empty') }}</div>
      </el-col>
    </el-row>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="buttonLoading" @click="handleSubmit">{{ $t('ok') }}</el-button>
    </template>
  </el-dialog>
</template>

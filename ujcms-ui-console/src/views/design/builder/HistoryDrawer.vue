<script setup lang="ts">
import { computed, ref, watch, type PropType } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { useI18n } from 'vue-i18n';
import { User } from '@element-plus/icons-vue';
import { dayjsLocale } from '@/i18n';
import { perm } from '@/stores/useCurrentUser';
import { queryRegionHistory, restoreRegionSnapshot } from '@/api/composition';
import { regionLabelOf } from './builderLabels';
import type { Drafts } from './types';

/**
 * 区域发布历史抽屉（region-history §9）：按区域列出发布快照（新→旧，时间轴：来源/相对时间/操作人/
 * 规模/备注），单条可恢复——恢复 = 一次新发布（历史只增不减），存在未发布草稿时确认对话框说明将放弃
 * 草稿并以 discardDraft: true 原子提交（§6.2）。恢复成功后由 BuilderPage 整体重载（面板与ID整批更换）。
 */
defineOptions({
  name: 'HistoryDrawer',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  page: { type: String, required: true },
  scope: { type: String, required: true },
  regionOrder: { type: Array as PropType<string[]>, default: () => [] },
  drafts: { type: Object as PropType<Drafts>, default: () => ({}) },
  // 打开时默认选中的区域（构建器当前选区所在区域；空则取首个区域）
  initialRegion: { type: String as PropType<string | null>, default: null },
});
const emit = defineEmits<{ 'update:modelValue': [val: boolean]; restored: [] }>();

dayjs.extend(relativeTime);

const { t, locale } = useI18n();
const region = ref<string>('');
const entries = ref<any[]>([]);
const loading = ref<boolean>(false);
const restoring = ref<boolean>(false);

const visible = computed<boolean>({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
});

// 历史坐标取自该区域的草稿实例（共享区域未分叉时是宿主页坐标，page-regions §15）；无实例时回退路由页面/范围
const coordinateOf = (regionName: string): { page: string; scope: string } => {
  const d = props.drafts[regionName];
  return { page: d?.page ?? props.page, scope: d?.scope ?? props.scope };
};

const loadEntries = async (): Promise<void> => {
  if (region.value === '') return;
  loading.value = true;
  try {
    const coord = coordinateOf(region.value);
    entries.value = await queryRegionHistory(coord.page, region.value, coord.scope);
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    region.value = props.initialRegion != null && props.regionOrder.includes(props.initialRegion) ? props.initialRegion : (props.regionOrder[0] ?? '');
    await loadEntries();
  },
);
watch(region, loadEntries);

const sourceTagType = (source: string): 'primary' | 'success' | 'info' | 'danger' => {
  if (source === 'restore') return 'success';
  if (source === 'delete') return 'danger';
  if (source === 'seed-reset') return 'danger';
  if (source === 'backfill') return 'info';
  return 'primary';
};
const sourceDotClass = (source: string): string =>
  ({ success: 'bg-success', danger: 'bg-danger', info: 'bg-secondary', primary: 'bg-primary' })[sourceTagType(source)] ?? 'bg-primary';

// 相对时间随控制台语言，绝对时间挂 title（ThemeGallery 同一习语）
const relativeOf = (created: string): string => dayjs(created).locale(dayjsLocale(locale.value)).fromNow();
const absoluteOf = (created: string): string => dayjs(created).format('YYYY-MM-DD HH:mm:ss');

const handleRestore = async (entry: any): Promise<void> => {
  // drafts 经响应式代理传入：组合式返回的 ref/computed（hasDraft/version）已自动解包
  const draft = props.drafts[region.value];
  const hasDraft = draft?.hasDraft === true;
  try {
    await ElMessageBox.confirm(hasDraft ? t('region.history.restoreConfirmDraft') : t('region.history.restoreConfirm'), t('region.confirm.title'), {
      type: 'warning',
    });
  } catch {
    return; // 取消
  }
  restoring.value = true;
  try {
    const coord = coordinateOf(region.value);
    await restoreRegionSnapshot({
      page: coord.page,
      region: region.value,
      scope: coord.scope,
      snapshotId: entry.id,
      version: draft?.version ?? 0,
      discardDraft: hasDraft,
    });
    ElMessage.success(t('region.history.restored'));
    emit('restored');
    await loadEntries();
  } catch {
    // 全局错误处理已提示（发布门 400 指名面板与字段；409 → 构建器刷新后可重试）
  } finally {
    restoring.value = false;
  }
};
</script>

<template>
  <el-drawer v-model="visible" :title="$t('region.history.title')" size="480px" append-to-body>
    <div class="mb-4 flex items-center gap-2">
      <span class="shrink-0 text-sm text-gray-secondary">{{ $t('region.history.region') }}</span>
      <el-select v-model="region" class="flex-1">
        <el-option v-for="name in regionOrder" :key="name" :label="regionLabelOf(drafts[name], name)" :value="name">
          <div class="flex items-center justify-between gap-4">
            <span class="truncate">{{ regionLabelOf(drafts[name], name) }}</span>
            <span class="shrink-0 font-mono text-xs text-gray-placeholder">{{ name }}</span>
          </div>
        </el-option>
      </el-select>
    </div>
    <div v-loading="loading">
      <el-empty v-if="entries.length <= 0" :description="$t('region.history.empty')" />
      <ol v-else class="relative ml-2 space-y-5 border-l border-gray-200 pl-5">
        <li v-for="(entry, index) in entries" :key="entry.id" class="relative">
          <span class="history-dot absolute top-1 h-3 w-3 rounded-full border-2 border-white ring-1 ring-gray-200" :class="sourceDotClass(entry.source)"></span>
          <div class="flex items-center gap-2">
            <el-tag size="small" :type="sourceTagType(entry.source)">{{ $t(`region.history.source.${entry.source}`) }}</el-tag>
            <el-tag v-if="index === 0" size="small" type="success" effect="plain">{{ $t('builder.history.latest') }}</el-tag>
            <span class="text-sm text-gray-primary" :title="absoluteOf(entry.created)">{{ relativeOf(entry.created) }}</span>
            <el-button class="ml-auto" type="primary" size="small" link :disabled="restoring || perm('pageRegion:update')" @click="() => handleRestore(entry)">
              {{ $t('region.history.restore') }}
            </el-button>
          </div>
          <div class="mt-1 flex flex-wrap items-center gap-x-3 text-xs text-gray-secondary">
            <span>{{ $t('region.history.counts', { rows: entry.rowsCount, panels: entry.panelsCount }) }}</span>
            <span v-if="entry.actor != null" class="inline-flex items-center gap-1"
              ><el-icon><User /></el-icon>{{ entry.actor }}</span
            >
            <span class="text-gray-placeholder">{{ absoluteOf(entry.created) }}</span>
          </div>
          <div v-if="entry.label != null && entry.label !== ''" class="mt-1 truncate text-xs text-gray-regular" :title="entry.label">{{ entry.label }}</div>
        </li>
      </ol>
    </div>
  </el-drawer>
</template>

<style scoped>
.history-dot {
  left: -1.6875rem;
}
</style>

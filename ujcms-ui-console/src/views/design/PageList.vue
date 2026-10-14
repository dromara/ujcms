<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Refresh, Grid } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { localizedLabel } from '@/i18n';
import { perm } from '@/stores/useCurrentUser';
import { rememberPageLabel } from '@/views/design/builder/builderLabels';
import { queryCompositionPageList, queryPanelsByOrigin, rescanComposition, queryRegionList } from '@/api/composition';

defineOptions({
  name: 'PageList',
});

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const loading = ref<boolean>(false);
const data = ref<Array<any>>([]);
// 来源筛选片（theme-marketplace §7.2）：?origin=theme:<name> 深链——只看含该来源面板的页面
const originChip = ref<string | undefined>(route.query.origin ? String(route.query.origin) : undefined);
// 来源面板按页面名计数（筛选片激活时装载）
const originPanelCounts = ref<Record<string, number>>({});
// 当前站点已声明的区域（page-regions §9）：按页面名归并，驱动「编辑布局」入口。
// 构建器一次装载整页全部区域（region 参数仅作检查器预选）。
const regions = ref<Array<any>>([]);
const pagesWithRegions = computed<Set<string>>(() => new Set(regions.value.map((item) => item.page)));

const pageLabel = (row: any): string => localizedLabel(row.label, row.name);

const fetchData = async () => {
  loading.value = true;
  try {
    const [pages, regionList] = await Promise.all([queryCompositionPageList(), queryRegionList()]);
    data.value = pages;
    regions.value = regionList;
    if (originChip.value) {
      const panels = await queryPanelsByOrigin(originChip.value);
      const counts: Record<string, number> = {};
      (panels ?? []).forEach((item: any) => {
        counts[item.pageRef] = (counts[item.pageRef] ?? 0) + 1;
      });
      originPanelCounts.value = counts;
    }
  } finally {
    loading.value = false;
  }
};

// 筛选片激活时只展示含该来源面板的页面
const visibleData = computed<Array<any>>(() =>
  originChip.value ? data.value.filter((row) => (originPanelCounts.value[row.name] ?? 0) > 0) : data.value,
);

const clearOriginChip = () => {
  originChip.value = undefined;
  originPanelCounts.value = {};
};

// 编辑布局：进入可视化构建器（visual-builder §10，页面级作用域）
// 进入构建器前记入页面显示名缓存：构建器顶栏在 page-detail 返回前即显示中文名（不闪现原始页面名）
const handleEditLayout = (row: any) => {
  rememberPageLabel(row.name, row.label);
  router.push({ name: 'Builder', query: { page: row.name } });
};

const handleRescan = async () => {
  const result = await rescanComposition();
  ElMessage.success(t('composition.rescan.success', { shapes: result.shapeCount, manifests: result.manifestCount }));
  fetchData();
};

onMounted(fetchData);
</script>

<template>
  <div>
    <div>
      <el-button :disabled="perm('composition:rescan')" :icon="Refresh" @click="() => handleRescan()">{{ $t('composition.op.rescan') }}</el-button>
      <!-- 来源筛选片：只看含该来源面板的页面（主题详情深链） -->
      <el-tag v-if="originChip" closable type="warning" class="ml-2" @close="clearOriginChip">
        {{ $t('page.origin') }}: {{ originChip }}
      </el-tag>
    </div>

    <div class="mt-3 app-block">
      <el-table v-loading="loading" :data="visibleData">
        <el-table-column property="name" :label="$t('page.name')" width="160" show-overflow-tooltip></el-table-column>
        <el-table-column :label="$t('page.label')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ pageLabel(row) }}</template>
        </el-table-column>
        <el-table-column :label="$t('page.templates')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="item in row.templates" :key="item" type="info" size="small" class="mr-1">{{ item }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('page.themes')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="item in row.themes" :key="item" size="small" class="mr-1">{{ item }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('page.regionCount')" width="100">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.regionCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="originChip" :label="$t('page.originPanels')" width="110">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ originPanelCounts[row.name] ?? 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('table.action')" width="200">
          <template #default="{ row }">
            <el-button
              v-if="pagesWithRegions.has(row.name)"
              type="primary"
              size="small"
              link
              :icon="Grid"
              :disabled="perm('pageRegion:show')"
              @click="() => handleEditLayout(row)"
            >
              {{ $t('region.op.edit') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="py-6 text-gray-400">{{ $t('page.empty') }}</div>
        </template>
      </el-table>
    </div>
  </div>
</template>

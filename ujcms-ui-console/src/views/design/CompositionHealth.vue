<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';
import { CircleCheckFilled, Refresh, WarningFilled } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import { perm } from '@/stores/useCurrentUser';
import { deleteAllArchivedPanels, deleteArchivedPanel, pruneDanglingRefs, queryCompositionHealth, repairSeedTokens, rescanComposition, restorePanel } from '@/api/composition';
import DiagnosticsSection from './health/DiagnosticsSection.vue';

defineOptions({
  name: 'CompositionHealth',
});

const { t } = useI18n();
const report = ref<any>();
const loading = ref<boolean>(false);

const fetchData = async () => {
  loading.value = true;
  try {
    report.value = await queryCompositionHealth();
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

const handleRescan = async () => {
  const result = await rescanComposition();
  ElMessage.success(t('composition.rescan.success', { shapes: result.shapeCount, manifests: result.manifestCount }));
  await fetchData();
};

const handleRestorePanel = async (id: string) => {
  await restorePanel(id);
  ElMessage.success(t('success'));
  await fetchData();
};

// 删除归档面板（主题切换孤立的组件：处置决定移到诊断页做，切换对话框不再询问）
const handleDeleteArchivedPanel = async (row: any) => {
  await ElMessageBox.confirm(t('health.archivedDelete.confirm', { component: placementComponentLabel(row) }), t('health.archivedDelete'), { type: 'warning' });
  await deleteArchivedPanel(row.id);
  ElMessage.success(t('success'));
  await fetchData();
};

// 清空归档（D21）：「确定不回旧主题了」的一次性收尾
const handleDeleteAllArchived = async () => {
  await ElMessageBox.confirm(t('health.archivedDeleteAll.confirm', { count: archived.value.length }), t('health.archivedDeleteAll'), { type: 'warning' });
  const result = await deleteAllArchivedPanels();
  ElMessage.success(t('health.archivedDeleteAll.done', { count: result.deleted }));
  await fetchData();
};

// 清理悬空引用（显式修复）：剔除结构树中指向已删除组件的引用（早期带外删除的残留）
const danglingCount = computed(() => (report.value?.danglingPanelRefs ?? []).length);
const handlePruneDangling = async () => {
  await ElMessageBox.confirm(t('health.pruneDangling.confirm', { count: danglingCount.value }), t('health.pruneDangling'), { type: 'warning' });
  const result = await pruneDanglingRefs();
  ElMessage.success(t('health.pruneDangling.done', { count: result.pruned }));
  await fetchData();
};

// 修复种子令牌（theme-seed-export R1）：显式操作员动作，按导入约定就地展开遗留令牌字面量
const repairing = ref<boolean>(false);
const handleRepairTokens = async () => {
  await ElMessageBox.confirm(t('health.repairSeedTokens.confirm'), t('health.repairSeedTokens'), { type: 'warning' });
  repairing.value = true;
  try {
    const result = await repairSeedTokens();
    if (result.tokensSkipped > 0) {
      ElMessage.warning(t('health.repairSeedTokens.partial', { panels: result.panelsRepaired, expanded: result.tokensExpanded, skipped: result.tokensSkipped }));
    } else {
      ElMessage.success(t('health.repairSeedTokens.done', { panels: result.panelsRepaired, expanded: result.tokensExpanded }));
    }
    await fetchData();
  } finally {
    repairing.value = false;
  }
};

// 失效引用：栏目/区块/投票/文章引用一张表（kind 由后端按 SettingsResolver.staleRefs 口径给出，D24）
const staleRefs = computed(() => report.value?.stalePanelRefs ?? []);
// 来源列：主题种子面板（theme:<name>）未改动者可经 导入演示数据/更新未修改项 自动修复；操作员面板须在构建器修正
const originLabel = (origin: string | null | undefined): string =>
  origin?.startsWith('theme:') ? t('health.origin.seed', { theme: origin.slice('theme:'.length) }) : t('health.origin.operator');
// 结构树残留：孤立面板 + 悬空引用同为带外删除残留，合并为一张表
const structureResidue = computed(() => [
  ...(report.value?.orphanPanels ?? []).map((row: any) => ({
    kind: 'orphanPanel',
    pageRef: row.pageRef,
    region: row.region,
    panelId: row.id,
  })),
  ...(report.value?.danglingPanelRefs ?? []).map((row: any) => ({ kind: 'danglingRef', ...row })),
]);
const unresolvable = computed(() => report.value?.unresolvableComponents ?? []);
const orphaned = computed(() => report.value?.orphanedPlacements ?? []);
const archived = computed(() => report.value?.archivedPlacements ?? []);
// 孤立/归档投放的组件列：标题（操作员辨识，如「热点新闻」）+ 组件名
const placementComponentLabel = (row: { component?: string | null; title?: string | null }): string =>
  row.title && row.component ? `${row.title}（${row.component}）` : (row.title ?? row.component ?? '');
const emptyRegions = computed(() => report.value?.emptyRequiredRegions ?? []);
// 未展开种子令牌（theme-seed-export R1）：D9 之前导入的遗留字面量，可一键修复
const literalTokens = computed(() => report.value?.literalSeedTokens ?? []);
// 必填设置为空（design-review P1-4）：已发布面板漏网路径（发布门上线前存量 / 主题更新新增必填字段）
const requiredMissing = computed(() => report.value?.requiredSettingsMissing ?? []);

// 六项检查（横幅上的结果条）：key 同时作为小节锚点 id 后缀；归档投放与空的区域为信息性，不在其列
const checks = computed(() => [
  { key: 'staleRefs', count: staleRefs.value.length },
  { key: 'literalSeedTokens', count: literalTokens.value.length },
  { key: 'unresolvableComponents', count: unresolvable.value.length },
  { key: 'orphanedPlacements', count: orphaned.value.length },
  { key: 'structureResidue', count: structureResidue.value.length },
  { key: 'requiredSettingsMissing', count: requiredMissing.value.length },
]);
const problemCount = computed(() => checks.value.reduce((sum, check) => sum + check.count, 0));
// 点击异常项跳到对应小节（干净项无小节可跳）
const handleCheckClick = (check: { key: string; count: number }) => {
  if (check.count > 0) {
    document.getElementById(`health-${check.key}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
};

// 加载记录：后端已限近期 20 条（CompositionHealthService.RECENT_RELOADS），页面打开即全部平铺，不再折叠
const reloads = computed(() => report.value?.registry?.recentReloads ?? []);
const reloadTagType = (result: string): 'success' | 'danger' => (result === 'success' ? 'success' : 'danger');
// 来源/结果为后端枚举值，映射为中文展示；未知值原样显示（扁平键 te() 恒为 false，用 t(key)!==key 判断）
const reloadEnumLabel = (kind: 'source' | 'result', value: string | null | undefined): string => {
  if (value == null) {
    return '';
  }
  const key = `health.reload.${kind}.${value}`;
  const label = t(key);
  return label !== key ? label : value;
};
// 详情列：失败显示错误；成功显示差异摘要（{"shapes":{"added":[…]…},"pages":…,"themes":…}，
// "{}"=有对比但无变更，null=首次加载无基线）
const reloadDetailLabel = (row: any): string => {
  if (row.errorDetail) {
    return row.errorDetail;
  }
  if (row.diffSummary == null || row.diffSummary === '') {
    return '';
  }
  let diff: any;
  try {
    diff = JSON.parse(row.diffSummary);
  } catch {
    return row.diffSummary;
  }
  const parts: string[] = [];
  for (const category of ['shapes', 'pages', 'themes']) {
    for (const op of ['added', 'removed', 'changed']) {
      const names = diff?.[category]?.[op];
      if (Array.isArray(names) && names.length > 0) {
        parts.push(`${t(`health.reload.diff.${category}.${op}`)}${t('punct.colon')}${names.join(t('punct.enum'))}`);
      }
    }
  }
  if (parts.length === 0) {
    return t('health.reload.diff.none');
  }
  return parts.join('；') + (diff.truncated ? t('health.reload.diff.truncated') : '');
};
</script>

<template>
  <div v-loading="loading" class="space-y-3">
    <template v-if="report != null">
      <!-- 结论横幅：结论 + 六项检查结果条（干净=中性底打勾，异常=警示色计数，点击跳到对应小节） -->
      <div class="app-block flex flex-wrap items-center gap-x-6 gap-y-4 px-6 py-5">
        <div class="flex min-w-0 flex-1 items-start gap-4">
          <el-icon :size="40" class="shrink-0" :class="problemCount > 0 ? 'text-warning' : 'text-success'">
            <WarningFilled v-if="problemCount > 0" />
            <CircleCheckFilled v-else />
          </el-icon>
          <div class="min-w-0">
            <h1 class="text-xl font-semibold tracking-tight text-gray-primary">
              {{ problemCount > 0 ? $t('health.problems', { count: problemCount }) : $t('health.ok') }}
            </h1>
            <p class="mt-0.5 text-sm text-gray-secondary">{{ problemCount > 0 ? $t('health.problems.desc') : $t('health.ok.desc') }}</p>
            <div class="mt-3 flex flex-wrap gap-1.5">
              <span
                v-for="check in checks"
                :key="check.key"
                class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs leading-none"
                :class="check.count > 0 ? 'cursor-pointer bg-warning-lighter text-warning hover:bg-warning-light/60' : 'bg-gray-100 text-gray-regular'"
                @click="() => handleCheckClick(check)"
              >
                <el-icon :size="12" :class="check.count > 0 ? 'text-warning' : 'text-success'">
                  <WarningFilled v-if="check.count > 0" />
                  <CircleCheckFilled v-else />
                </el-icon>
                <span>{{ $t(`health.check.${check.key}`) }}</span>
                <span v-if="check.count > 0" class="font-semibold tabular-nums">{{ check.count }}</span>
              </span>
            </div>
          </div>
        </div>
        <div class="flex shrink-0 items-center">
          <el-button :icon="Refresh" @click="() => fetchData()">{{ $t('refresh') }}</el-button>
          <el-button type="primary" :disabled="perm('composition:rescan')" @click="() => handleRescan()">{{ $t('composition.op.rescan') }}</el-button>
        </div>
      </div>

      <!-- 失效引用（面板设置 channelRef/blockRef 目标已删除） -->
      <DiagnosticsSection v-if="staleRefs.length > 0" id="health-staleRefs" :title="$t('health.staleRefs')" :count="staleRefs.length" :tip="$t('health.stalePanelRefs.tip')">
        <el-table :data="staleRefs" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="region" :label="$t('health.region')" width="140"></el-table-column>
          <el-table-column property="component" :label="$t('health.component')" width="160"></el-table-column>
          <el-table-column property="setting" :label="$t('health.setting')" width="140"></el-table-column>
          <el-table-column :label="$t('health.kind')" width="80">
            <template #default="{ row }">{{ $t(`health.kind.${row.kind}`) }}</template>
          </el-table-column>
          <el-table-column property="targetRef" :label="$t('health.targetRef')" min-width="140"></el-table-column>
          <el-table-column :label="$t('health.origin')" width="180">
            <template #default="{ row }">{{ originLabel(row.origin) }}</template>
          </el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 未展开种子令牌（D9 之前导入的遗留；一键修复，theme-seed-export R1） -->
      <DiagnosticsSection
        v-if="literalTokens.length > 0"
        id="health-literalSeedTokens"
        :title="$t('health.literalSeedTokens')"
        :count="literalTokens.length"
        :tip="$t('health.literalSeedTokens.tip')"
      >
        <template #actions>
          <el-button type="primary" size="small" :disabled="perm('pageRegion:update')" :loading="repairing" @click="() => handleRepairTokens()">
            {{ $t('health.repairSeedTokens') }}
          </el-button>
        </template>
        <el-table :data="literalTokens" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="region" :label="$t('health.region')" width="140"></el-table-column>
          <el-table-column property="component" :label="$t('health.component')" width="160"></el-table-column>
          <el-table-column property="setting" :label="$t('health.setting')" width="160" show-overflow-tooltip></el-table-column>
          <el-table-column property="token" :label="$t('health.token')" min-width="180" show-overflow-tooltip></el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 组件不可解析的面板（渲染为空占位） -->
      <DiagnosticsSection
        v-if="unresolvable.length > 0"
        id="health-unresolvableComponents"
        :title="$t('health.unresolvableComponents')"
        :count="unresolvable.length"
        :tip="$t('health.unresolvableComponents.tip')"
      >
        <el-table :data="unresolvable" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="region" :label="$t('health.region')" width="140"></el-table-column>
          <el-table-column property="component" :label="$t('health.component')" min-width="160"></el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 孤立面板投放（页面/区域不再被声明） -->
      <DiagnosticsSection v-if="orphaned.length > 0" id="health-orphanedPlacements" :title="$t('health.orphanedPlacements')" :count="orphaned.length">
        <el-table :data="orphaned" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="slotName" :label="$t('health.region')" width="160"></el-table-column>
          <el-table-column :label="$t('health.component')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ placementComponentLabel(row) }}</template>
          </el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 结构树残留：孤立面板 / 悬空引用（带外删除，page-regions §11） -->
      <DiagnosticsSection
        v-if="structureResidue.length > 0"
        id="health-structureResidue"
        :title="$t('health.structureResidue')"
        :count="structureResidue.length"
        :tip="$t('health.structureResidue.tip')"
      >
        <template v-if="danglingCount > 0" #actions>
          <el-button type="primary" size="small" :disabled="perm('pageRegion:update')" @click="() => handlePruneDangling()">
            {{ $t('health.pruneDangling') }}
          </el-button>
        </template>
        <el-table :data="structureResidue" size="small">
          <el-table-column :label="$t('health.kind')" width="100">
            <template #default="{ row }">{{ $t(`health.residue.${row.kind}`) }}</template>
          </el-table-column>
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="region" :label="$t('health.region')" min-width="140"></el-table-column>
          <el-table-column property="panelId" :label="$t('health.panelId')" min-width="180" show-overflow-tooltip></el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 必填设置为空（design-review P1-4）：已发布树内启用面板的必填设置为空（发布门拦截草稿；
           此处为门上线前存量与主题更新新增必填字段的漏网路径） -->
      <DiagnosticsSection
        v-if="requiredMissing.length > 0"
        id="health-requiredSettingsMissing"
        :title="$t('health.requiredSettingsMissing')"
        :count="requiredMissing.length"
        :tip="$t('health.requiredSettingsMissing.tip')"
      >
        <el-table :data="requiredMissing" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="region" :label="$t('health.region')" width="140"></el-table-column>
          <el-table-column property="scope" :label="$t('health.scope')" width="140" show-overflow-tooltip></el-table-column>
          <el-table-column property="component" :label="$t('health.component')" width="160"></el-table-column>
          <el-table-column property="setting" :label="$t('health.setting')" min-width="140"></el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 归档组件（主题切换孤立，切回自动恢复；非问题） -->
      <DiagnosticsSection v-if="archived.length > 0" :title="$t('health.archivedPlacements')" :count="archived.length" :tip="$t('health.archivedPlacements.tip')" tone="info">
        <template #actions>
          <el-button type="danger" size="small" plain :disabled="perm('pageRegion:update')" @click="() => handleDeleteAllArchived()">
            {{ $t('health.archivedDeleteAll') }}
          </el-button>
        </template>
        <el-table :data="archived" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="slotName" :label="$t('health.region')" width="160"></el-table-column>
          <el-table-column :label="$t('health.component')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ placementComponentLabel(row) }}</template>
          </el-table-column>
          <el-table-column :label="$t('table.action')" width="120">
            <template #default="{ row }">
              <el-button type="primary" :disabled="perm('pageRegion:update')" size="small" link @click="() => handleRestorePanel(row.id)">{{ $t('health.restore') }}</el-button>
              <el-button type="danger" :disabled="perm('pageRegion:update')" size="small" link @click="() => handleDeleteArchivedPanel(row)">{{
                $t('health.archivedDelete')
              }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 空的区域（声明但无已发布行；信息性，非问题） -->
      <DiagnosticsSection
        v-if="emptyRegions.length > 0"
        :title="$t('health.emptyRequiredRegions')"
        :count="emptyRegions.length"
        :tip="$t('health.emptyRequiredRegions.tip')"
        tone="info"
      >
        <el-table :data="emptyRegions" size="small">
          <el-table-column property="pageRef" :label="$t('page.name')" width="140"></el-table-column>
          <el-table-column property="region" :label="$t('health.region')" width="160"></el-table-column>
          <el-table-column :label="$t('page.themes')" min-width="200">
            <template #default="{ row }">{{ (row.themes ?? []).join(', ') }}</template>
          </el-table-column>
        </el-table>
      </DiagnosticsSection>

      <!-- 组件加载状态（诊断信息，置于问题之后）：四格概要 + 加载记录，打开即全部显示 -->
      <section class="app-block px-6 py-5">
        <h2 class="text-base font-semibold text-gray-primary">{{ $t('health.registry') }}</h2>
        <div class="mt-4 grid grid-cols-2 gap-3 md:grid-cols-4">
          <div class="rounded-lg bg-gray-50 px-4 py-3">
            <div class="text-xs text-gray-secondary">{{ $t('health.registry.loadedAt') }}</div>
            <div class="mt-1 text-lg font-semibold tabular-nums text-gray-primary">{{ dayjs(report.registry.loadedAt).format('YYYY-MM-DD HH:mm:ss') }}</div>
          </div>
          <div class="rounded-lg bg-gray-50 px-4 py-3">
            <div class="text-xs text-gray-secondary">{{ $t('health.registry.shapeCount') }}</div>
            <div class="mt-1 flex items-baseline gap-2">
              <span class="text-lg font-semibold tabular-nums text-gray-primary">{{ report.registry.activeShapeCount }}</span>
              <span v-if="report.registry.shapeCount !== report.registry.activeShapeCount" class="text-xs text-gray-secondary">
                {{ $t('health.registry.allThemes', { count: report.registry.shapeCount }) }}
              </span>
            </div>
          </div>
          <div class="rounded-lg bg-gray-50 px-4 py-3">
            <div class="text-xs text-gray-secondary">{{ $t('health.registry.manifestCount') }}</div>
            <div class="mt-1 flex items-baseline gap-2">
              <span class="text-lg font-semibold tabular-nums text-gray-primary">{{ report.registry.activeManifestCount }}</span>
              <span v-if="report.registry.manifestCount !== report.registry.activeManifestCount" class="text-xs text-gray-secondary">
                {{ $t('health.registry.allThemes', { count: report.registry.manifestCount }) }}
              </span>
            </div>
          </div>
          <div class="rounded-lg bg-gray-50 px-4 py-3">
            <div class="text-xs text-gray-secondary">{{ $t('health.registry.fingerprint') }}</div>
            <div class="mt-1 truncate font-mono text-sm leading-7 text-gray-regular" :title="report.registry.snapshotVersion">
              {{ report.registry.snapshotVersion?.slice(0, 16) }}
            </div>
          </div>
        </div>

        <h3 class="mb-2 mt-6 text-sm font-semibold text-gray-primary">{{ $t('health.registry.reloads') }}</h3>
        <el-table :data="reloads" size="small">
          <el-table-column :label="$t('health.reload.source')" width="110">
            <template #default="{ row }">{{ reloadEnumLabel('source', row.source) }}</template>
          </el-table-column>
          <el-table-column :label="$t('health.reload.result')" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="reloadTagType(row.result)">{{ reloadEnumLabel('result', row.result) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('health.reload.triggeredBy')" width="110">
            <template #default="{ row }">
              <span v-if="row.triggeredBy">{{ row.triggeredBy }}</span>
              <span v-else class="text-gray-placeholder">—</span>
            </template>
          </el-table-column>
          <el-table-column property="shapesCount" :label="$t('health.reload.shapes')" width="80" align="right"></el-table-column>
          <el-table-column property="manifestsCount" :label="$t('health.reload.manifests')" width="90" align="right"></el-table-column>
          <el-table-column
            property="loadedAt"
            :label="$t('health.reload.loadedAt')"
            width="170"
            :formatter="(row: any) => dayjs(row.loadedAt).format('YYYY-MM-DD HH:mm:ss')"
          ></el-table-column>
          <el-table-column :label="$t('health.reload.detail')" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ reloadDetailLabel(row) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </div>
</template>

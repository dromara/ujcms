<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { queryShapeList, queryShape, queryShapeUsage } from '@/api/composition';
import { localizedLabel } from '@/i18n';

defineOptions({
  name: 'ShapeCatalog',
});

const data = ref<Array<any>>([]);
const loading = ref<boolean>(false);
const detailVisible = ref<boolean>(false);
const detail = ref<any>();
// 使用情况报告（§11.3）：allowedShapes 引用该组件的区域 + 面板数
const usage = ref<any>();

// 来源/标签筛选（§11.3）：目录为全量内存列表，前端过滤即可
const sourceFilter = ref<string>('');
const tagFilter = ref<string[]>([]);
const sourceOptions = computed<string[]>(() => [...new Set<string>(data.value.map((row) => row.source))]);
const tagOptions = computed<string[]>(() => [...new Set<string>(data.value.flatMap((row) => row.tags ?? []))]);
const filteredData = computed<any[]>(() =>
  data.value.filter(
    (row) =>
      (sourceFilter.value === '' || row.source === sourceFilter.value) &&
      (tagFilter.value.length === 0 || (row.tags ?? []).some((tag: string) => tagFilter.value.includes(tag))),
  ),
);

// 来源展示：去掉内部 theme: 前缀，只留主题键（siteId/主题名）——Shape 只有主题来源（theme-owned-shapes D15）
const sourceLabel = (source: string): string => source.replace(/^theme:/, '');

const fetchData = async () => {
  loading.value = true;
  try {
    data.value = await queryShapeList();
  } finally {
    loading.value = false;
  }
};
onMounted(fetchData);

const handleShow = async (name: string) => {
  detail.value = await queryShape(name);
  usage.value = undefined;
  detailVisible.value = true;
  usage.value = await queryShapeUsage(name);
};

// 字段约束摘要（max/min/pattern/values/palette 等参数的紧凑展示）
const constraintSummary = (def: any): string => {
  const entries = Object.entries(def ?? {}).filter(([key]) => !['name', 'type', 'required', 'label', 'description', 'fields', 'of'].includes(key));
  return entries.map(([key, value]) => `${key}: ${JSON.stringify(value)}`).join(', ');
};
const fieldRows = (fields: any): Array<any> => Object.entries(fields ?? {}).map(([name, def]: [string, any]) => ({ name, ...def }));
</script>

<template>
  <div>
    <div class="mb-3">
      <el-alert :title="$t('shape.readonly.tooltip')" type="info" :closable="false" show-icon></el-alert>
    </div>
    <!-- 来源/标签筛选（§11.3） -->
    <div class="mb-3 flex items-center">
      <el-select v-model="sourceFilter" clearable :placeholder="$t('shape.source')" class="w-48 mr-2">
        <el-option v-for="item in sourceOptions" :key="item" :label="sourceLabel(item)" :value="item"></el-option>
      </el-select>
      <el-select v-model="tagFilter" multiple clearable :placeholder="$t('shape.tags')" class="w-64">
        <el-option v-for="item in tagOptions" :key="item" :label="item" :value="item"></el-option>
      </el-select>
    </div>
    <div class="app-block">
      <el-table v-loading="loading" :data="filteredData" @row-dblclick="(row: any) => handleShow(row.name)">
        <el-table-column property="name" :label="$t('shape.name')" min-width="140" show-overflow-tooltip></el-table-column>
        <el-table-column property="label" :label="$t('shape.label')" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ localizedLabel(row.label, '') }}</template>
        </el-table-column>
        <el-table-column property="version" :label="$t('shape.version')" width="100"></el-table-column>
        <el-table-column property="source" :label="$t('shape.source')" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="tags" :label="$t('shape.tags')" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags ?? []" :key="tag" size="small" class="mr-1">{{ tag }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="panelCount" :label="$t('shape.panelCount')" width="100" align="right"></el-table-column>
        <el-table-column :label="$t('table.action')" width="100">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="() => handleShow(row.name)">{{ $t('view') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" :title="detail?.name" size="50%">
      <template v-if="detail != null">
        <el-descriptions :column="2" border class="mb-3">
          <el-descriptions-item :label="$t('shape.name')">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item :label="$t('shape.version')">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item :label="$t('shape.label')">{{ localizedLabel(detail.label, '') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('shape.panelCount')">{{ detail.panelCount }}</el-descriptions-item>
          <el-descriptions-item :label="$t('shape.source')">{{ sourceLabel(detail.source) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.description" :label="$t('shape.description')" :span="2">{{ detail.description }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="mb-2 font-bold">{{ $t('shape.fields') }}</h4>
        <el-table :data="fieldRows(detail.definition?.item)" size="small" class="mb-3">
          <el-table-column property="name" :label="$t('shape.field.name')" width="130"></el-table-column>
          <el-table-column property="type" :label="$t('shape.field.type')" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column property="required" :label="$t('shape.field.required')" width="70">
            <template #default="{ row }">{{ $t(row.required ? 'yes' : 'no') }}</template>
          </el-table-column>
          <el-table-column :label="$t('shape.field.constraints')" show-overflow-tooltip>
            <template #default="{ row }">{{ constraintSummary(row) }}</template>
          </el-table-column>
        </el-table>

        <!-- 使用情况（§11.3）：allowedShapes 引用该组件的区域及当前站点面板数 -->
        <h4 class="mb-2 font-bold">{{ $t('shape.usage') }}</h4>
        <el-table v-if="usage != null" :data="usage.slots" size="small" class="mb-3">
          <el-table-column property="namespace" :label="$t('shape.usage.namespace')" min-width="140" show-overflow-tooltip></el-table-column>
          <el-table-column property="page" :label="$t('shape.usage.page')" width="110" show-overflow-tooltip></el-table-column>
          <el-table-column property="slot" :label="$t('shape.usage.region')" width="110" show-overflow-tooltip></el-table-column>
          <el-table-column property="placementCount" :label="$t('shape.usage.placementCount')" width="80" align="right"></el-table-column>
          <template #empty>
            <div class="py-3 text-gray-400">{{ $t('shape.usage.empty') }}</div>
          </template>
        </el-table>

      </template>
    </el-drawer>
  </div>
</template>

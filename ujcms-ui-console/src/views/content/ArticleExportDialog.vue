<script setup lang="ts">
import { computed, ref, toRefs, watch, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { saveAs } from 'file-saver';
import { handleBlobError } from '@/utils/request';
import { exportArticles, type ArticleExportColumn } from '@/api/content';
import { articleStatuses } from './components/articleActions';

// 导出文章列表为 .xlsx（docs/spec/article-management/plan.md D6）。范围就是当前列表：同一组查询条件、同一个栏目、
// 同一份数据权限；这里只选“全部还是勾选的”和“导出哪些列”。表头与枚举值的文字在这里按当前语言给出，随请求送去
defineOptions({
  name: 'ArticleExportDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 列表的查询参数（查询键、去重、所选栏目）
  params: { type: Object as PropType<Record<string, any>>, required: true },
  // 当前筛选的总篇数
  total: { type: Number, required: true },
  // 勾选的文章ID
  selectedIds: { type: Array as PropType<string[]>, default: () => [] },
  // 所选栏目模型的自定义字段：[{ code, name, type }]
  fields: { type: Array as PropType<any[]>, default: () => [] },
});
const emit = defineEmits({ 'update:modelValue': null });

const MAX_ROWS = 50000;
const STORAGE_KEY = 'ujcms.articleExport.columns';
// 可导出的系统列，按表格里读起来的顺序
const SYSTEM_COLUMNS: { key: string; labelKey: string }[] = [
  { key: 'id', labelKey: 'ID' },
  { key: 'title', labelKey: 'article.title' },
  { key: 'subtitle', labelKey: 'article.subtitle' },
  { key: 'fullTitle', labelKey: 'article.fullTitle' },
  { key: 'slug', labelKey: 'article.slug' },
  { key: 'channel', labelKey: 'article.channel' },
  { key: 'status', labelKey: 'article.status' },
  { key: 'type', labelKey: 'article.type' },
  { key: 'sticky', labelKey: 'article.sticky' },
  { key: 'publishDate', labelKey: 'article.publishDate' },
  { key: 'onlineDate', labelKey: 'article.onlineDate' },
  { key: 'offlineDate', labelKey: 'article.offlineDate' },
  { key: 'author', labelKey: 'article.author' },
  { key: 'editor', labelKey: 'article.editor' },
  { key: 'source', labelKey: 'article.source' },
  { key: 'description', labelKey: 'article.seoDescription' },
  { key: 'seoKeywords', labelKey: 'article.seoKeywords' },
  { key: 'tags', labelKey: 'article.tags' },
  { key: 'linkUrl', labelKey: 'article.linkUrl' },
  { key: 'url', labelKey: 'article.export.url' },
  { key: 'org', labelKey: 'article.org' },
  { key: 'user', labelKey: 'article.user' },
  { key: 'created', labelKey: 'article.created' },
  { key: 'modifiedUser', labelKey: 'article.modifiedUser' },
  { key: 'modified', labelKey: 'article.modified' },
  { key: 'views', labelKey: 'article.views' },
  { key: 'comments', labelKey: 'article.comments' },
  { key: 'ups', labelKey: 'article.ups' },
  { key: 'downs', labelKey: 'article.downs' },
];
const DEFAULT_COLUMNS = ['id', 'title', 'channel', 'status', 'publishDate', 'author', 'source', 'tags', 'views', 'url'];
// 文件上传、富文本这类字段没有适合放进单元格的值
const EXPORTABLE_FIELD_TYPES = ['text', 'textarea', 'number', 'date', 'color', 'slider', 'switch', 'radio', 'checkbox', 'select', 'multipleSelect'];

const { modelValue: visible, total, selectedIds, fields } = toRefs(props);
const { t } = useI18n();
const scope = ref<'filter' | 'selected'>('filter');
const checked = ref<string[]>([...DEFAULT_COLUMNS]);
const loading = ref<boolean>(false);

const systemColumns = computed<ArticleExportColumn[]>(() => SYSTEM_COLUMNS.map((item) => ({ key: item.key, label: item.key === 'id' ? 'ID' : t(item.labelKey) })));
// “大字段”存在另一个 JSON 列里，导出不读它：不列出来，免得导出一列空白
const customColumns = computed<ArticleExportColumn[]>(() =>
  fields.value
    .filter((field: any) => EXPORTABLE_FIELD_TYPES.includes(field.type) && !field.clob && /^[a-zA-Z0-9]+$/.test(field.code))
    .map((field: any) => ({ key: `custom.${field.code}`, label: field.name })),
);
const allColumns = computed<ArticleExportColumn[]>(() => [...systemColumns.value, ...customColumns.value]);
const count = computed<number>(() => (scope.value === 'selected' ? selectedIds.value.length : total.value));
const tooMany = computed<boolean>(() => scope.value === 'filter' && total.value > MAX_ROWS);

// 上次选的列记在本机：纯属个人习惯，不必进服务端的偏好设置
const readStored = (): string[] | undefined => {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    return Array.isArray(stored) ? stored.filter((key) => typeof key === 'string') : undefined;
  } catch {
    return undefined;
  }
};
const store = (keys: string[]) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(keys));
  } catch {
    // 存不下就不记
  }
};

watch(visible, () => {
  if (!visible.value) {
    return;
  }
  scope.value = selectedIds.value.length > 0 ? 'selected' : 'filter';
  const known = allColumns.value.map((column) => column.key);
  // 自定义字段随所选栏目而变：记下的列里不属于当前栏目的不带过来；在列表里显示的自定义字段默认带上
  const stored = readStored()?.filter((key) => known.includes(key));
  const shown = fields.value.filter((field: any) => field.showInList).map((field: any) => `custom.${field.code}`);
  checked.value = stored != null && stored.length > 0 ? stored : [...DEFAULT_COLUMNS, ...shown.filter((key: string) => known.includes(key))];
});

const handleExport = async () => {
  // 按界面上的顺序导出，不按勾选的先后
  const columns = allColumns.value.filter((column) => checked.value.includes(column.key));
  if (columns.length === 0) {
    ElMessage.warning(t('article.export.columnRequired'));
    return;
  }
  if (count.value <= 0) {
    ElMessage.warning(t('article.export.empty'));
    return;
  }
  const labels: Record<string, string> = { true: t('yes'), false: t('no') };
  articleStatuses.forEach((status) => (labels[`status.${status}`] = t(`article.status.${status}`)));
  [0, 1, 2, 3].forEach((type) => (labels[`type.${type}`] = t(`article.type.${type}`)));
  loading.value = true;
  try {
    const { blob, filename } = await exportArticles(props.params, {
      ids: scope.value === 'selected' ? selectedIds.value : undefined,
      columns,
      labels,
    });
    saveAs(blob, filename);
    store(columns.map((column) => column.key));
    emit('update:modelValue', false);
  } catch (e) {
    await handleBlobError(e);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-dialog :title="$t('article.op.export')" :model-value="modelValue" :width="720" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <div class="mb-4 text-xs leading-5 text-gray-secondary">{{ $t('article.export.tip') }}</div>
    <el-form label-position="top" @submit.prevent>
      <el-form-item :label="$t('article.export.scope')">
        <el-radio-group v-model="scope">
          <el-radio value="filter">{{ $t('article.export.scope.filter', { count: total }) }}</el-radio>
          <el-radio value="selected" :disabled="selectedIds.length <= 0">{{ $t('article.export.scope.selected', { count: selectedIds.length }) }}</el-radio>
        </el-radio-group>
        <el-alert v-if="tooMany" type="warning" :closable="false" show-icon class="mt-2" :title="$t('article.export.tooMany', { count: total, max: MAX_ROWS })" />
      </el-form-item>
      <el-form-item>
        <template #label>
          {{ $t('article.export.columns') }}
          <el-button type="primary" link size="small" class="ml-3" @click="() => (checked = allColumns.map((column) => column.key))">{{ $t('article.export.selectAll') }}</el-button>
          <el-button type="primary" link size="small" @click="() => (checked = [...DEFAULT_COLUMNS])">{{ $t('article.export.selectDefault') }}</el-button>
        </template>
        <el-checkbox-group v-model="checked" class="w-full">
          <div class="grid grid-cols-4 gap-x-3">
            <el-checkbox v-for="column in systemColumns" :key="column.key" :value="column.key">{{ column.label }}</el-checkbox>
          </div>
          <template v-if="customColumns.length > 0">
            <div class="mt-2 mb-1 text-xs text-gray-secondary">{{ $t('article.export.customFields') }}</div>
            <div class="grid grid-cols-4 gap-x-3">
              <el-checkbox v-for="column in customColumns" :key="column.key" :value="column.key">{{ column.label }}</el-checkbox>
            </div>
          </template>
        </el-checkbox-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="tooMany || count <= 0" @click="() => handleExport()">{{ $t('article.export.do') }}</el-button>
    </template>
  </el-dialog>
</template>

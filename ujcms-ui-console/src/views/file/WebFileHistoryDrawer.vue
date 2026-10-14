<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { Codemirror } from 'vue-codemirror';
import { html } from '@codemirror/lang-html';
import { editorTheme } from '@/views/design/template/editorTheme';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { perm } from '@/stores/useCurrentUser';
import { queryWebFileTemplateHistory, queryWebFileTemplateHistoryDetail, restoreWebFileTemplateHistory } from '@/api/file';

// 模板编辑历史抽屉（template-history / design-review P3-6）：镜像构建器历史抽屉的形态。
// 单文件模式（fileId）挂在文件编辑器；目录模式（dir）挂在文件管理器工具栏——已删除文件的发现面，
// 恢复即重建。仅模板文件管理器使用（历史只对模板存储捕获），故 API 直接引入而非属性下钻。
defineOptions({
  name: 'WebFileHistoryDrawer',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  fileId: { type: String, default: null },
  dir: { type: String, default: null },
  // 对比入口（template-manager P3）：为 true 时每行提供「对比」动作，由挂载方（编辑工作台）
  // 以并排差异呈现该版本与当前内容；查看/恢复流程不变
  compare: { type: Boolean, default: false },
});
const emit = defineEmits({ 'update:modelValue': null, restored: null, compare: null });
const { t } = useI18n();

const loading = ref<boolean>(false);
const data = ref<any[]>([]);
const detailId = ref<string>();
const detailContent = ref<string>('');

const title = computed(() => (props.fileId != null ? `${t('webFile.history')}: ${props.fileId}` : `${t('webFile.history')}: ${props.dir}`));

const fetchData = async () => {
  loading.value = true;
  detailId.value = undefined;
  detailContent.value = '';
  try {
    data.value = await queryWebFileTemplateHistory(props.fileId != null ? { id: props.fileId } : { dir: props.dir ?? '/' });
  } finally {
    loading.value = false;
  }
};
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      fetchData();
    }
  },
);

const handleView = async (row: any) => {
  if (detailId.value === row.id) {
    detailId.value = undefined;
    detailContent.value = '';
    return;
  }
  const detail = await queryWebFileTemplateHistoryDetail(row.id);
  detailId.value = row.id;
  detailContent.value = detail.content;
};
const handleRestore = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('webFile.history.confirmRestore', { name: row.path }), t('webFile.history.restore'), {
      confirmButtonText: t('submit'),
      cancelButtonText: t('cancel'),
      type: 'warning',
    });
  } catch {
    return;
  }
  await restoreWebFileTemplateHistory(row.id);
  ElMessage.success(t('webFile.history.restored'));
  emit('restored', row);
  fetchData();
};
const formatSize = (length: number) => (length < 1024 ? `${length} B` : `${(length / 1024).toFixed(1)} KB`);
const sourceTagType = (source: string) => {
  switch (source) {
    case 'delete':
      return 'danger';
    case 'restore':
      return 'success';
    case 'backfill':
      return 'info';
    default:
      return 'primary';
  }
};
</script>

<template>
  <el-drawer :model-value="modelValue" :title="title" size="50%" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <el-alert v-if="dir != null" type="info" :title="$t('webFile.history.dirTip')" :closable="false" class="mb-2" />
    <el-table v-loading="loading" :data="data" :empty-text="$t('webFile.history.empty')">
      <el-table-column v-if="dir != null" property="path" :label="$t('webFile.history.file')" show-overflow-tooltip min-width="180" />
      <el-table-column property="created" :label="$t('webFile.history.time')" min-width="140" :formatter="(row: any) => dayjs(row.created).format('YYYY-MM-DD HH:mm:ss')" />
      <el-table-column property="source" :label="$t('webFile.history.source')" width="80">
        <template #default="{ row }">
          <el-tag :type="sourceTagType(row.source)" size="small">{{ $t(`webFile.history.source.${row.source}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column property="length" :label="$t('webFile.history.size')" width="90" :formatter="(row: any) => formatSize(row.length)" />
      <el-table-column property="actor" :label="$t('webFile.history.actor')" width="100" show-overflow-tooltip />
      <el-table-column :label="$t('table.action')" :width="compare ? 170 : 120">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="() => handleView(row)">{{ $t('webFile.history.view') }}</el-button>
          <el-button v-if="compare" type="primary" size="small" link @click="() => $emit('compare', row)">{{ $t('webFile.history.compare') }}</el-button>
          <el-button type="primary" size="small" link :disabled="perm('webFileTemplate:update')" @click="() => handleRestore(row)">
            {{ $t('webFile.history.restore') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="detailId != null" class="mt-3">
      <codemirror :model-value="detailContent" disabled :extensions="[html(), ...editorTheme]" :style="{ maxHeight: '400px', width: '100%', border: '1px solid #dcdfe6' }" />
    </div>
  </el-drawer>
</template>

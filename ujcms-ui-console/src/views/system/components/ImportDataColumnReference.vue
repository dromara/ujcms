<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

// 数据迁移的字段说明（docs/spec/data-migration/plan.md）：列的清单来自后端 meta，这里只负责文案与“填入示例 SQL”
defineOptions({
  name: 'ImportDataColumnReference',
});
const props = defineProps({
  kind: { type: String, required: true },
  // 后端 meta 里这一类认得的列：[{ name, required }]
  columns: { type: Array as () => { name: string; required: boolean }[], required: true },
});
const emit = defineEmits({ fill: (sql: string) => typeof sql === 'string' });
const { t } = useI18n();

// 含义先找这一类专属的文案（id_ 对栏目和文章的说明不同），再找共用的
const meaning = (name: string): string => {
  const specific = `importData.column.${props.kind}.${name}`;
  const specificText = t(specific);
  if (specificText !== specific) {
    return specificText;
  }
  const shared = `importData.column.${name}`;
  const sharedText = t(shared);
  return sharedText !== shared ? sharedText : '';
};
const rows = computed(() => props.columns.map((column) => ({ ...column, meaning: meaning(column.name) })));
// 示例 SQL：源表的列名按“去掉末尾下划线”猜，操作员改成自己的列名即可
const sample = computed<string>(() => {
  const lines = props.columns.map((column) => `  ${column.name.replace(/_$/, '')} as ${column.name}`);
  return `select\n${lines.join(',\n')}\nfrom ${props.kind === 'channel' ? 'legacy_channel' : 'legacy_article'}\norder by id`;
});
</script>

<template>
  <el-collapse class="import-data-columns">
    <el-collapse-item name="columns">
      <template #title>
        <span class="text-sm">{{ $t('importData.columns') }}</span>
        <el-button type="primary" link size="small" class="ml-3" @click.stop="() => emit('fill', sample)">{{ $t('importData.op.fillSample') }}</el-button>
      </template>
      <el-table :data="rows" size="small" max-height="320" border>
        <el-table-column property="name" :label="$t('importData.columns.name')" width="180">
          <template #default="{ row }">
            <code>{{ row.name }}</code>
            <el-tag v-if="row.required" type="danger" size="small" disable-transitions class="ml-2">{{ $t('importData.columns.required') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column property="meaning" :label="$t('importData.columns.meaning')" min-width="300" show-overflow-tooltip />
      </el-table>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { computed, onMounted, watch, type PropType } from 'vue';
import { useLayoutVariantStore } from '@/stores/layoutVariantStore';

defineOptions({
  name: 'TemplateSelect',
});
const props = defineProps({
  modelValue: { type: String as PropType<string | null>, default: null },
  surface: { type: String as () => 'channel' | 'article' | 'topic', required: true },
  templates: { type: Array as () => string[], required: true },
  clearable: { type: Boolean, default: false },
  // “未存储模板”是一等状态（docs/spec/channel-management/plan.md D9、D10）：栏目、专题的模板列为空时按主题的基础模板
  // 渲染。开启后下拉首项为“主题默认”，代表空值——选它即清空该列，不把基础模板的文件名写进数据
  defaultOption: { type: Boolean, default: false },
});
const emit = defineEmits<{ (e: 'update:modelValue', value: string | null): void }>();

/** “主题默认”选项在下拉内部的取值：空字符串。对外仍是 null */
const DEFAULT_VALUE = '';

const store = useLayoutVariantStore();
// 模板轨道下拉以变体标签标注模板选项（template-track-variants Q3）：绑定了启用主题变体的模板
// 以变体标签为主显示（选中态同），原始文件名保留为选项右侧灰字（进阶逃生口）；未绑定的模板
// 维持原始文件名。绑定映射来自主题目录（by-surface?track=template），与构建器切换器同源。
const labelByTemplate = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {};
  store.optionsFor(props.surface).forEach((row) => {
    if (row.template && row.label && map[row.template] == null) {
      map[row.template] = row.label;
    }
  });
  return map;
});
const selected = computed(() => (props.defaultOption ? (props.modelValue ?? DEFAULT_VALUE) : props.modelValue));
// ElementPlus 默认把空字符串当作“未选择”而显示占位符：有“主题默认”选项时只把 undefined、null 视为空
const emptyValues = computed(() => (props.defaultOption ? [undefined, null] : undefined));
const handleChange = (value: any) => emit('update:modelValue', value == null || value === DEFAULT_VALUE ? null : value);

const load = () => store.fetchBySurface(props.surface);
onMounted(load);
watch(() => props.surface, load);
</script>

<template>
  <el-select :model-value="selected" :clearable="clearable" :empty-values="emptyValues" class="w-full" @update:model-value="handleChange">
    <el-option v-if="defaultOption" :label="$t('template.default')" :value="DEFAULT_VALUE">
      <div class="flex items-center justify-between">
        <span>{{ $t('template.default') }}</span>
        <span class="ml-4 text-xs text-gray-400">{{ surface + '.html' }}</span>
      </div>
    </el-option>
    <el-option v-for="item in templates" :key="item" :label="labelByTemplate[item] ?? item + '.html'" :value="item">
      <template v-if="labelByTemplate[item]">
        <div class="flex items-center justify-between">
          <span>{{ labelByTemplate[item] }}</span>
          <span class="ml-4 text-xs text-gray-400">{{ item + '.html' }}</span>
        </div>
      </template>
    </el-option>
  </el-select>
</template>

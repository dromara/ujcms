<script setup lang="ts">
import { computed, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';

// AI审核角标（docs/spec/ai/ugc-moderation.md §6）。仅装饰：绿色建议通过、彩色类别+置信度、
// 灰色待检测、红色检测失败（可重试）；提示为判定理由与词库命中。无判定行时不渲染
defineOptions({
  name: 'AiModerationTag',
});
const props = defineProps({
  verdict: { type: Object as PropType<any>, default: null },
  retryable: { type: Boolean, default: false },
});
defineEmits({ retry: null });
const { t } = useI18n();

const dictHits = computed<string[]>(() => {
  try {
    const hits = JSON.parse(props.verdict?.dictHits ?? '[]');
    return Array.isArray(hits) ? hits : [];
  } catch {
    return [];
  }
});
const tooltip = computed(() => {
  const lines: string[] = [];
  if (props.verdict?.reason) {
    lines.push(props.verdict.reason);
  }
  if (dictHits.value.length > 0) {
    lines.push(`${t('aiModeration.dictHits')}: ${dictHits.value.join(t('punct.enum'))}`);
  }
  return lines.join('\n');
});
</script>

<template>
  <span v-if="verdict != null">
    <el-tooltip :content="tooltip" :disabled="tooltip === ''" placement="top">
      <span>
        <el-tag v-if="verdict.status === 0" type="info" size="small" disable-transitions>{{ $t('aiModeration.status.0') }}</el-tag>
        <el-tag v-else-if="verdict.status === 2" type="danger" size="small" disable-transitions>{{ $t('aiModeration.failed') }}</el-tag>
        <el-tag v-else-if="verdict.category === 1" type="success" size="small" disable-transitions>{{ $t('aiModeration.suggestPass') }}</el-tag>
        <el-tag v-else type="warning" size="small" disable-transitions>{{ $t(`aiModeration.category.${verdict.category}`) }} {{ verdict.confidence }}</el-tag>
      </span>
    </el-tooltip>
    <el-button v-if="verdict.status === 2 && retryable" type="primary" size="small" link class="ml-1" @click="() => $emit('retry')">
      {{ $t('aiModeration.op.retry') }}
    </el-button>
  </span>
</template>

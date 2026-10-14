<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { queryAiTranslateProviders } from '@/api/ai';

/**
 * 单篇预翻译的服务商选择（AI 预翻译对话框与新增译文对话框共用，docs/spec/ai/article-translate.md §6）。
 * 按目标站点加载可选项：全部大模型服务商 + 支持该语言对的机器翻译引擎（与批量预览同一口径），
 * 默认选中全局“翻译服务商”。不可用（AI 未开启/无服务商）时显示原因并把 v-model 置空，
 * 调用方据此禁用“开始翻译”，而不是在创建草稿之后才报错。
 * targetSiteId 置空即清空（对话框关闭时传 undefined，再次打开重新加载）
 */
defineOptions({
  name: 'TranslateProviderSelect',
});
const props = defineProps<{ targetSiteId?: string; modelValue?: string }>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: string | undefined): void;
  (e: 'loaded', available: boolean): void;
}>();

const options = ref<any[]>([]);
const reason = ref<string>();
const loading = ref<boolean>(false);
const llmOptions = computed(() => options.value.filter((item: any) => !item.mt));
const mtOptions = computed(() => options.value.filter((item: any) => item.mt));

const load = async () => {
  options.value = [];
  reason.value = undefined;
  if (props.targetSiteId == null) {
    emit('update:modelValue', undefined);
    return;
  }
  loading.value = true;
  try {
    const result = await queryAiTranslateProviders(props.targetSiteId);
    options.value = result.providers ?? [];
    reason.value = result.reason ?? undefined;
    // 仍可用的当前选择保留，否则回落到默认服务商
    const current = props.modelValue;
    const next = current != null && options.value.some((item: any) => item.code === current) ? current : (result.defaultProvider ?? undefined);
    emit('update:modelValue', next);
    emit('loaded', next != null);
  } finally {
    loading.value = false;
  }
};
watch(() => props.targetSiteId, load, { immediate: true });
</script>

<template>
  <div>
    <el-select
      :model-value="modelValue"
      :loading="loading"
      :disabled="options.length === 0"
      class="w-full"
      @update:model-value="(value: string) => emit('update:modelValue', value)"
    >
      <el-option-group v-if="llmOptions.length > 0" :label="$t('article.i18n.batchTranslate.provider.llm')">
        <el-option v-for="item in llmOptions" :key="item.code" :label="item.name" :value="item.code" />
      </el-option-group>
      <el-option-group v-if="mtOptions.length > 0" :label="$t('article.i18n.batchTranslate.provider.mt')">
        <el-option v-for="item in mtOptions" :key="item.code" :label="item.name" :value="item.code" />
      </el-option-group>
    </el-select>
    <div class="mt-1 text-xs">
      <el-text v-if="reason" type="warning" size="small">
        {{ $t(`article.i18n.aiTranslate.provider.${reason === 'DISABLED' ? 'disabled' : 'notConfigured'}`) }}
      </el-text>
      <span v-else class="text-gray-500">{{ $t('article.i18n.aiTranslate.provider.hint') }}</span>
    </div>
  </div>
</template>

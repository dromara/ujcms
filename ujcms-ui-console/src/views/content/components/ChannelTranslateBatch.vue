<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { previewChannelTranslateBatch, executeChannelTranslateBatch } from '@/api/content';
import { useLangGroupBatch } from '@/composables/useLangGroupBatch';
import LangGroupScope from './LangGroupScope.vue';

/**
 * 栏目批量新增译文（商业版 T2，docs/spec/ai/article-translate.md §7.1；2026-09-04 取代在目标站点就地翻译的“翻译本站栏目”）。
 * 在源语言站点执行，与文章管理的“批量新增译文”同方向：选择目标站点（可多选，默认全选同组站点；可限栏目子树）
 * → 逐站点预览计数（可新建 / 待翻译含正文篇数 / 已翻译或已修改；无法新建的原因仅在非零时显示）与可选服务商（各站点交集）
 * → 逐站点顺序同步执行（每站点一次请求，各自受超时约束）：目标站点缺少的同别名栏目按建站复制新建，
 * 随后把新建栏目与仍同本站完全相同的名称、SEO 字段、正文翻译为该站点语言（可仅新建不翻译）；结果汇总提示。
 * 已手工修改的不覆盖，别名与定制字段值不动，本站栏目不变
 */
defineOptions({
  name: 'ChannelTranslateBatch',
});
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();

const {
  groupSites,
  targetSiteIds,
  channelId,
  previewing,
  provider,
  targetSites,
  previewRows,
  llmOptions,
  mtOptions,
  providerName,
  translating,
  currentLang,
  targetLangs,
  siteNames,
  siteLine,
  reset,
} = useLangGroupBatch((targetSiteId, scopeChannelId) => previewChannelTranslateBatch({ targetSiteId, channelId: scopeChannelId }));
const executing = ref(false);
// 顺序执行中的站点（名称与序号），供进度提示
const executingSite = ref<{ name: string; index: number; count: number }>();

// 有栏目可新建即可执行；只有待翻译栏目时须选了服务商
const hasWork = (preview: any) => preview.toCreate > 0 || (preview.untranslated > 0 && translating.value);
const workRows = computed(() => previewRows.value.filter((row) => hasWork(row.preview)));
const startable = computed(() => workRows.value.length > 0);
const selectable = computed(() => previewRows.value.some((row) => row.preview.toCreate > 0 || row.preview.untranslated > 0));
const previewText = (value: any) => {
  if (value.total === 0) {
    return t('channel.translateBatch.preview.empty');
  }
  // 三个零不易读出“无事可做”（服务商下拉与开始按钮因此不出现）：明说没有可新建或待翻译的栏目，且已不同的值不会被覆盖
  let text =
    value.toCreate === 0 && value.untranslated === 0
      ? t('channel.translateBatch.preview.nothing', [value.translated])
      : t('channel.translateBatch.preview', [value.total, value.toCreate, value.untranslated, value.bodies, value.translated]);
  // 无法新建是少数情形（目标站点模型被改名、限定子树时上级缺失），仅在命中时提及
  if (value.noModel > 0) {
    text += t('channel.translateBatch.preview.noModel', [value.noModel]);
  }
  if (value.noParent > 0) {
    text += t('channel.translateBatch.preview.noParent', [value.noParent]);
  }
  return text;
};

// 原因代码的操作者提示：DISABLED（AI 总开关未开）与 NOT_CONFIGURED（无支持该语言对的引擎凭证）分别指向各自的修复位置，
// TOO_LONG（正文超长）提示手工翻译；其余代码（配额、引擎错误等）原样带出。locale 为扁平键，用 t(key)!==key 判断存在
const reasonText = (reason: string) => {
  const key = `channel.translateBatch.reason.${reason}`;
  const text = t(key);
  return text !== key ? text : reason;
};
const reasonMessage = (reason: string, fallbackKey: string) => {
  const key = `channel.translateBatch.reason.${reason}`;
  const text = t(key);
  return text !== key ? text : t(fallbackKey, [reason]);
};
// 翻译不可用的原因按站点归并：所有所选站点同一原因（如 AI 总开关未开）时不加站点前缀
const reasonRows = computed(() => {
  const groups = new Map<string, any[]>();
  previewRows.value.forEach((row) => {
    if (row.preview.reason != null) {
      groups.set(row.preview.reason, [...(groups.get(row.preview.reason) ?? []), row.site]);
    }
  });
  return [...groups.entries()].map(([reason, sites]) => {
    const message = reasonMessage(reason, 'channel.translateBatch.noEngine');
    return sites.length === previewRows.value.length ? message : t('article.i18n.scope.reasonSites', [siteNames(sites), message]);
  });
});
const withSite = (site: any, text: string) => t('article.i18n.scope.reasonSites', [site.name, text]);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      reset();
      executingSite.value = undefined;
    }
  },
);

const execute = async () => {
  const rows = workRows.value;
  if (rows.length === 0) {
    return;
  }
  // 确认文案按各站点预览合计：目标站点 + 新建（仅命中时） + 翻译或仅新建，各为完整句子
  const sum = (key: string) => rows.reduce((total, row) => total + (row.preview[key] ?? 0), 0);
  const toCreate = sum('toCreate');
  const sentences: string[] = [t('channel.translateBatch.confirm.sites', [siteNames(rows.map((row) => row.site))])];
  if (toCreate > 0) {
    sentences.push(t('channel.translateBatch.confirm.create', [toCreate]));
  }
  if (translating.value) {
    sentences.push(t('channel.translateBatch.confirm.translate', [toCreate + sum('untranslated'), sum('bodies'), providerName.value]));
  } else {
    sentences.push(t('channel.translateBatch.confirm.createOnly'));
  }
  sentences.push(t('channel.translateBatch.confirm.tail'));
  await ElMessageBox.confirm(sentences.join(' '), t('channel.op.translateBatch'), { type: 'warning' });
  executing.value = true;
  let created = 0;
  let translatedCount = 0;
  let translatedBodies = 0;
  let translatedAny = false;
  const warnings: string[] = [];
  const bodyFailures: string[] = [];
  try {
    // 逐站点顺序执行：每站点一次同步请求（各自受超时约束），某站点失败不影响其余站点，重跑可补
    for (let index = 0; index < rows.length; index++) {
      const row = rows[index];
      if (row == null) continue;
      const { site } = row;
      executingSite.value = { name: site.name, index: index + 1, count: rows.length };
      let result: any;
      try {
        result = await executeChannelTranslateBatch({ targetSiteId: site.id, channelId: channelId.value, provider: provider.value || undefined });
      } catch {
        // 全局拦截器已弹出错误详情，这里只在汇总里记下站点
        warnings.push(withSite(site, t('channel.translateBatch.failed')));
        continue;
      }
      created += result.created;
      const translation = result.translation;
      if (translation == null) {
        continue;
      }
      if (translation.translated) {
        translatedAny = true;
        translatedCount += translation.count;
        translatedBodies += translation.bodies;
      } else if (translation.reason) {
        // 新建已落库、翻译整批未过门槛或字段批失败：重跑时新建的栏目就是“仍相同”的同别名栏目，会被补译
        warnings.push(withSite(site, reasonMessage(translation.reason, 'channel.translateBatch.skipped')));
      }
      // 单篇正文失败不影响其余写入：单独列出，重跑即可补译（未写入的正文仍与本站相同，仍在范围内）
      (translation.bodyFailures ?? []).forEach((item: any) => {
        bodyFailures.push(withSite(site, t('channel.translateBatch.bodyFailure', [item.name, reasonText(item.reason)])));
      });
    }
  } finally {
    executing.value = false;
    executingSite.value = undefined;
  }
  if (translatedAny) {
    ElMessage.success(t('channel.translateBatch.success', [created, translatedCount, translatedBodies, providerName.value]));
  } else if (created > 0) {
    ElMessage.success(t('channel.translateBatch.created', [created]));
  }
  warnings.forEach((message) => ElMessage.warning(message));
  if (bodyFailures.length > 0) {
    ElMessage.warning({
      message: t('channel.translateBatch.bodyFailures', [bodyFailures.join(t('channel.translateBatch.bodyFailures.join'))]),
      duration: 0,
      showClose: true,
    });
  }
  emit('finished');
  emit('update:modelValue', false);
};
</script>

<template>
  <el-dialog :title="$t('channel.op.translateBatch')" :model-value="modelValue" width="680px" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <lang-group-scope
      v-model:target-site-ids="targetSiteIds"
      v-model:channel-id="channelId"
      :active="modelValue"
      :channel-placeholder="$t('channel.translateBatch.channel')"
      channel-clearable
      @ready="(sites: any[]) => (groupSites = sites)"
    />
    <div v-if="targetSites.length > 0" class="mt-2 text-sm">{{ $t('channel.translateBatch.direction', [currentLang, targetLangs]) }}</div>
    <template v-if="previewRows.length > 0">
      <!-- 逐站点一行：各站点状态可能不同（一个已全部翻译、另一个刚建站全部待翻译） -->
      <el-alert
        v-for="row in previewRows"
        :key="row.site.id"
        :title="siteLine(row.site, previewText(row.preview))"
        :type="hasWork(row.preview) ? 'success' : 'info'"
        :closable="false"
        class="mt-3"
      />
      <!-- 翻译不可用时仍可仅新建：提示修复位置，服务商下拉只剩“仅新建” -->
      <el-alert v-for="(text, index) in reasonRows" :key="index" :title="text" type="warning" :closable="false" class="mt-3" />
      <div v-if="selectable" class="mt-3">
        <!-- 服务商按次选择：与文章批量新增译文同一服务商空间（大模型 + 已配置的机器翻译引擎），多站点时取交集，默认全局“翻译服务商” -->
        <el-select v-model="provider" class="w-72">
          <el-option :label="$t('channel.translateBatch.provider.none')" value="" />
          <el-option-group v-if="llmOptions.length > 0" :label="$t('article.i18n.batchTranslate.provider.llm')">
            <el-option v-for="item in llmOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-option-group>
          <el-option-group v-if="mtOptions.length > 0" :label="$t('article.i18n.batchTranslate.provider.mt')">
            <el-option v-for="item in mtOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-option-group>
        </el-select>
        <div class="mt-2 text-xs text-gray-500">
          {{ $t('channel.translateBatch.provider.hint') }}
          <template v-if="targetSites.length > 1">{{ $t('article.i18n.scope.providerIntersection') }}</template>
        </div>
      </div>
      <div class="mt-2 text-xs text-gray-500">{{ $t('channel.translateBatch.hint') }}</div>
      <div v-if="executingSite != null" class="mt-3 text-sm">{{ $t('channel.translateBatch.executing', [executingSite.name, executingSite.index, executingSite.count]) }}</div>
    </template>
    <template #footer>
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
      <el-button v-if="groupSites.length > 0" type="primary" :loading="executing || previewing" :disabled="!startable" @click="() => execute()">
        {{ $t('article.i18n.batchTranslate.start') }}
      </el-button>
    </template>
  </el-dialog>
</template>

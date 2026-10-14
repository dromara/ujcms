<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { previewArticleTranslateBatch, startArticleTranslateBatch, queryArticleTranslateBatchTask, stopArticleTranslateBatchTask } from '@/api/content';
import { useLangGroupBatch } from '@/composables/useLangGroupBatch';
import LangGroupScope from './LangGroupScope.vue';

/**
 * 批量新增译文（商业版 T2，docs/spec/ai/article-translate.md §7；2026-09-04 前称“批量翻译”，更名并显示方向以免被读作译入本站）。
 * 在源语言站点执行：选择目标站点（可多选，默认全选同组站点；可限栏目子树）→ 逐站点预览计数（可新建 / 已有 / 无栏目，
 * 可关联已有文章 / 未翻译草稿 / 歧义仅在非零时显示）与可用服务商（各站点交集）
 * → 逐站点提交后台任务（目标栏目内有同别名/同标题的未入组文章则直接关联，其余新建草稿，可仅创建草稿；
 * 选了服务商时补译从未编辑过的未翻译草稿），服务端翻译执行器单线程，多站点任务按提交顺序排队执行；
 * 弹窗内逐任务轮询进度、可停止（停止全部未完成任务）；关闭弹窗不影响任务执行（任务列表可查看）。
 * 翻译仅填充草稿，须切换到目标站点校对后发布（与单篇 AI 预翻译同姿态）
 */
defineOptions({
  name: 'ArticleTranslateBatch',
});
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits({ 'update:modelValue': null, finished: null });
const { t } = useI18n();

const POLL_INTERVAL = 3000;

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
  translating,
  currentLang,
  targetLangs,
  siteNames,
  siteLine,
  reset,
} = useLangGroupBatch((targetSiteId, scopeChannelId) => previewArticleTranslateBatch({ targetSiteId, channelId: scopeChannelId }));
const starting = ref(false);
// 已提交的任务（每目标站点一个），按提交顺序；为空表示仍在表单阶段
const tasks = ref<any[]>();
// 范围内源文章总数（各目标站点相同），进度分母
const scopeTotal = ref(0);
let timer: number | undefined;

const running = computed(() => tasks.value != null && tasks.value.some((item) => item.status <= 1));
// 补译已有的未翻译草稿只在选了服务商时发生
const hasWork = (preview: any) => preview.toCreate > 0 || preview.toLink > 0 || (preview.untranslatedDrafts > 0 && translating.value);
const workRows = computed(() => previewRows.value.filter((row) => hasWork(row.preview)));
const startable = computed(() => workRows.value.length > 0);
const selectable = computed(() => previewRows.value.some((row) => row.preview.toCreate > 0 || row.preview.untranslatedDrafts > 0));
const previewText = (value: any) => {
  let text = t('article.i18n.batchTranslate.preview', [value.total, value.toCreate, value.existing, value.missingChannel]);
  // 关联已有文章是少数情形（别名/标题跨语言几乎不会相同），仅在命中时提及
  if (value.toLink > 0) {
    text += t('article.i18n.batchTranslate.preview.link', [value.toLink]);
  }
  if (value.untranslatedDrafts > 0) {
    text += t('article.i18n.batchTranslate.preview.untranslated', [value.untranslatedDrafts]);
  }
  if (value.ambiguous > 0) {
    text += t('article.i18n.batchTranslate.preview.ambiguous', [value.ambiguous]);
  }
  return text;
};
const taskLine = (item: any) =>
  siteLine(item.site, t('article.i18n.batchTranslate.status', [t(`task.status.${item.status}`), t('article.i18n.batchTranslate.progress', [item.current ?? 0, scopeTotal.value])]));

const stopPolling = () => {
  if (timer != null) {
    window.clearInterval(timer);
    timer = undefined;
  }
};
onBeforeUnmount(stopPolling);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      reset();
      tasks.value = undefined;
    } else {
      // 关闭弹窗停止轮询；任务继续在服务端执行
      stopPolling();
    }
  },
);

const poll = async () => {
  const list = tasks.value;
  if (list == null) {
    return;
  }
  const pending = list.filter((item) => item.status <= 1);
  const updates = await Promise.all(pending.map((item) => queryArticleTranslateBatchTask(item.id)));
  updates.forEach((update, index) => {
    Object.assign(pending[index], update);
  });
  if (list.every((item) => item.status >= 2)) {
    stopPolling();
    emit('finished');
  }
};

const start = async () => {
  const rows = workRows.value;
  if (rows.length === 0) {
    return;
  }
  // 确认文案按各站点预览合计：目标站点 + 关联已有文章（仅命中时） + 新建（翻译/仅建稿） + 补译，各为完整句子
  const sum = (key: string) => rows.reduce((total, row) => total + (row.preview[key] ?? 0), 0);
  const toLink = sum('toLink');
  const toCreate = sum('toCreate');
  const untranslatedDrafts = translating.value ? sum('untranslatedDrafts') : 0;
  const sentences: string[] = [t('article.i18n.batchTranslate.confirm.sites', [siteNames(rows.map((row) => row.site))])];
  if (toLink > 0) {
    sentences.push(t('article.i18n.batchTranslate.confirm.link', [toLink]));
  }
  if (toCreate > 0) {
    const createKey = translating.value ? 'article.i18n.batchTranslate.confirm.translate' : 'article.i18n.batchTranslate.confirm.createOnly';
    sentences.push(t(createKey, [toCreate]));
  }
  if (untranslatedDrafts > 0) {
    sentences.push(t('article.i18n.batchTranslate.confirm.retranslate', [untranslatedDrafts]));
  }
  sentences.push(t('article.i18n.batchTranslate.confirm.tail'));
  await ElMessageBox.confirm(sentences.join(' '), t('article.i18n.batchTranslate'), { type: 'warning' });
  starting.value = true;
  const submitted: any[] = [];
  try {
    // 逐站点提交，各自立即返回任务ID；服务端执行器单线程，任务按提交顺序排队执行。
    // 某站点提交失败（全局拦截器已提示）时，已提交的任务照常进入进度视图
    for (const row of rows) {
      const { taskId } = await startArticleTranslateBatch({ targetSiteId: row.site.id, channelId: channelId.value, provider: provider.value || undefined });
      submitted.push({ id: taskId, site: row.site, status: 0, current: 0, report: null });
    }
  } finally {
    starting.value = false;
    if (submitted.length > 0) {
      scopeTotal.value = rows[0]?.preview.total ?? 0;
      tasks.value = submitted;
      timer = window.setInterval(poll, POLL_INTERVAL);
    }
  }
};

// 停止全部未完成任务：排队中的直接取消，运行中的在下一篇文章边界生效；报告随后由服务端补写，此处继续轮询到状态离开运行中
const stop = async () => {
  const list = tasks.value;
  if (list == null) {
    return;
  }
  await Promise.all(list.filter((item) => item.status <= 1).map((item) => stopArticleTranslateBatchTask(item.id)));
  await poll();
};
</script>

<template>
  <el-dialog :title="$t('article.i18n.batchTranslate')" :model-value="modelValue" width="680px" @update:model-value="(event: boolean) => $emit('update:modelValue', event)">
    <template v-if="tasks == null">
      <lang-group-scope
        v-model:target-site-ids="targetSiteIds"
        v-model:channel-id="channelId"
        :active="modelValue"
        :channel-placeholder="$t('article.i18n.batchTranslate.channel')"
        channel-clearable
        @ready="(sites: any[]) => (groupSites = sites)"
      />
      <div v-if="targetSites.length > 0" class="mt-2 text-sm">{{ $t('article.i18n.batchTranslate.direction', [currentLang, targetLangs]) }}</div>
      <template v-if="previewRows.length > 0">
        <!-- 逐站点一行：各站点状态可能不同（一个已全部有译文、另一个刚建站全部可新建） -->
        <el-alert
          v-for="row in previewRows"
          :key="row.site.id"
          :title="siteLine(row.site, previewText(row.preview))"
          :type="hasWork(row.preview) ? 'success' : 'info'"
          :closable="false"
          class="mt-3"
        />
        <div v-if="selectable" class="mt-3">
          <el-select v-model="provider" class="w-72">
            <el-option :label="$t('article.i18n.batchTranslate.provider.none')" value="" />
            <el-option-group v-if="mtOptions.length > 0" :label="$t('article.i18n.batchTranslate.provider.mt')">
              <el-option v-for="item in mtOptions" :key="item.code" :label="item.name" :value="item.code" />
            </el-option-group>
            <el-option-group v-if="llmOptions.length > 0" :label="$t('article.i18n.batchTranslate.provider.llm')">
              <el-option v-for="item in llmOptions" :key="item.code" :label="item.name" :value="item.code" />
            </el-option-group>
          </el-select>
          <div class="mt-2 text-xs text-gray-500">
            {{ $t('article.i18n.batchTranslate.provider.hint') }}
            <template v-if="targetSites.length > 1">{{ $t('article.i18n.scope.providerIntersection') }}</template>
          </div>
        </div>
      </template>
    </template>
    <template v-else>
      <!-- 每目标站点一个任务：状态、进度与完成报告（停止后报告由服务端在篇边界补写，未及补写时提示稍后在任务列表查看） -->
      <div v-for="(item, index) in tasks" :key="item.id" :class="index > 0 ? 'mt-4' : ''">
        <div class="text-sm">{{ taskLine(item) }}</div>
        <el-progress
          :percentage="Math.min(100, Math.round(((item.current ?? 0) / Math.max(1, scopeTotal)) * 100))"
          :status="item.status === 2 ? 'exception' : item.status === 4 ? 'success' : undefined"
          class="mt-1"
        />
        <pre v-if="item.report" class="mt-2 max-h-40 overflow-auto whitespace-pre-wrap rounded border border-gray-200 p-2 text-xs">{{ item.report }}</pre>
        <el-alert v-else-if="item.status >= 2" :title="$t('article.i18n.batchTranslate.reportPending')" type="info" :closable="false" class="mt-2" />
      </div>
      <div v-if="running" class="mt-3 text-xs text-gray-500">
        {{ $t('article.i18n.batchTranslate.background') }}
        <template v-if="tasks.length > 1">{{ $t('article.i18n.batchTranslate.queued') }}</template>
      </div>
    </template>
    <template #footer>
      <el-button v-if="running" type="danger" @click="() => stop()">{{ $t('article.i18n.batchTranslate.stop') }}</el-button>
      <!-- 任务已提交后关闭弹窗不影响任务：按钮改称“关闭”，避免与“停止”混淆 -->
      <el-button @click="() => $emit('update:modelValue', false)">{{ $t(tasks == null ? 'cancel' : 'close') }}</el-button>
      <el-button v-if="tasks == null && groupSites.length > 0" type="primary" :loading="starting || previewing" :disabled="!startable" @click="() => start()">
        {{ $t('article.i18n.batchTranslate.start') }}
      </el-button>
    </template>
  </el-dialog>
</template>

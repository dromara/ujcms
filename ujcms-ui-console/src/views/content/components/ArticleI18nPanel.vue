<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { perm, currentUser, isShowTier } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { toTree, disableParentTree } from '@/utils/tree';
import { lockedTip } from '@/utils/commercial';
import { queryArticleI18n, updateArticleI18n, queryArticleI18nCandidates, createArticleTranslation, queryChannelList } from '@/api/content';
import { aiTranslateArticle } from '@/api/ai';
import LabelTip from '@/components/LabelTip.vue';
import TranslateProviderSelect from './TranslateProviderSelect.vue';

/**
 * 文章翻译面板（multilingual P5，translation-links.md §6.1；译文草稿与过期状态见 translation-workflow.md）。
 * 语言组内除本站外的每个站点一行：已链接文章（含状态/可能过期标签）或选择器 + 新增译文入口。
 * 当前站点未分组时后端返回空行列表，面板自隐藏。
 * 保存独立于文章主表单（链接不属于文章负载），成功后提示重新生成静态页。
 */
defineOptions({
  name: 'ArticleI18nPanel',
});
const props = defineProps<{ articleId?: string; channelSlug?: string }>();
const { t } = useI18n();

const rows = ref<any[]>([]);
const selfStale = ref<boolean>(false);
const loading = ref(false);
// 每行选择器状态：站点ID → 选中文章ID / 候选列表 / 加载中
const selection = ref<Record<string, string | undefined>>({});
const candidates = ref<Record<string, any[]>>({});
const candidateLoading = ref<Record<string, boolean>>({});
const saving = ref<Record<string, boolean>>({});

const fetchRows = async () => {
  if (props.articleId == null) {
    rows.value = [];
    selfStale.value = false;
    return;
  }
  loading.value = true;
  try {
    const result = await queryArticleI18n(props.articleId);
    rows.value = result.rows;
    selfStale.value = result.selfStale;
    selection.value = {};
    candidates.value = {};
    rows.value.forEach((row) => {
      selection.value[row.siteId] = row.article?.id;
      candidates.value[row.siteId] = row.article ? [{ id: row.article.id, title: row.article.title }] : [];
    });
  } finally {
    loading.value = false;
  }
};
watch(() => props.articleId, fetchRows, { immediate: true });
// 任一行带状态/可能过期标签时才渲染标签列（各行同列对齐；全无标签时不留空列）
const hasTags = computed(() => rows.value.some((row) => row.stale || (row.article != null && row.article.status !== 0 && row.article.status !== 1)));

const searchCandidates = async (row: any, title: string) => {
  candidateLoading.value[row.siteId] = true;
  try {
    const list = await queryArticleI18nCandidates({ siteId: row.siteId, title });
    // 已在其它翻译组内的文章不可选（服务端一站一员/不并组规则会拒绝）；当前已链接文章保留可见
    candidates.value[row.siteId] = list.filter((item: any) => !item.linked || item.id === row.article?.id);
  } finally {
    candidateLoading.value[row.siteId] = false;
  }
};

const save = async (row: any) => {
  if (props.articleId == null) {
    return;
  }
  saving.value[row.siteId] = true;
  try {
    await updateArticleI18n(props.articleId, { [row.siteId]: selection.value[row.siteId] ?? null });
    ElMessage.success(t('article.i18n.saved'));
    await fetchRows();
  } finally {
    saving.value[row.siteId] = false;
  }
};

// AI 预翻译（docs/spec/ai/article-translate.md）：仅对已链接的草稿译文可用；
// 阻塞调用可达数分钟，取消仅停止等待（服务端调用与计费不会中止，对话框文案如实说明）
const currentSiteStore = useCurrentSiteStore();
const aiTranslateAvailable = computed(
  () => currentUser.commercialTier >= 2 && currentSiteStore.currentSite?.ai?.translate === true && !perm('ai:translate'),
);
const translating = ref<Record<string, boolean>>({});
const translateVisible = ref<boolean>(false);
const translateSeconds = ref<number>(0);
let translateAbort: AbortController | undefined;
let translateTimer: number | undefined;

// 执行预翻译并刷新面板。服务端把模型留空的字段原样保留并列在 skippedFields：如实列出，而不是一律提示“翻译完成”
const runAiTranslate = async (siteId: string, targetArticleId: string, provider?: string) => {
  if (props.articleId == null) {
    return;
  }
  translating.value[siteId] = true;
  translateVisible.value = true;
  translateSeconds.value = 0;
  translateTimer = window.setInterval(() => (translateSeconds.value += 1), 1000);
  translateAbort = new AbortController();
  try {
    const result = await aiTranslateArticle({ sourceArticleId: props.articleId, targetArticleId, provider }, translateAbort.signal);
    const skipped: string[] = result?.skippedFields ?? [];
    if (skipped.length > 0) {
      ElMessage({
        type: 'warning',
        message: t('article.i18n.aiTranslate.partial', [skipped.map((field) => t(`article.${field}`)).join(t('punct.enum'))]),
        duration: 8000,
        showClose: true,
      });
    } else {
      ElMessage.success(t('article.i18n.aiTranslate.success'));
    }
    await fetchRows();
  } catch (e: any) {
    // 取消等待：请求中止是用户主动行为，不作为错误提示
    if (e?.code !== 'ERR_CANCELED') throw e;
  } finally {
    window.clearInterval(translateTimer);
    translateVisible.value = false;
    translating.value[siteId] = false;
  }
};

// 预翻译前的确认对话框（取代原 confirm 框）：可按次选择服务商，默认全局“翻译服务商”；无可用服务商时禁用开始
const translateDialogVisible = ref<boolean>(false);
const translateTarget = ref<any>();
const translateProvider = ref<string>();
const translateAvailable = ref<boolean>(false);

const handleAiTranslate = (row: any) => {
  if (row.article == null) {
    return;
  }
  translateTarget.value = row;
  translateProvider.value = undefined;
  translateAvailable.value = false;
  translateDialogVisible.value = true;
};

const startAiTranslate = async () => {
  const row = translateTarget.value;
  if (row?.article == null || translateProvider.value == null) {
    return;
  }
  translateDialogVisible.value = false;
  await runAiTranslate(row.siteId, row.article.id, translateProvider.value);
};

// 新增译文：目标站点栏目选择对话框（缺省预选同别名栏目，服务端也会按别名回退）。
// 预翻译可用时可勾选“创建后立即 AI 预翻译”（默认勾选，与批量新增译文默认翻译一致）：
// 建稿与翻译一步完成，省去再点一次预翻译及其确认框；勾选框本身即是对费用与耗时的确认
const createVisible = ref<boolean>(false);
const createSiteId = ref<string>();
const createChannelId = ref<string>();
const createChannelData = ref<any[]>([]);
const createLoading = ref<boolean>(false);
const createTranslate = ref<boolean>(true);
const createProvider = ref<string>();
const createTranslateAvailable = ref<boolean>(true);

// 目标站点无可用服务商（AI 未开启/无服务商）：勾选框取消并禁用，选择器下方显示原因
const onCreateProvidersLoaded = (available: boolean) => {
  createTranslateAvailable.value = available;
  createTranslate.value = available;
};

const openCreate = async (row: any) => {
  createSiteId.value = row.siteId;
  createChannelId.value = undefined;
  createChannelData.value = [];
  createTranslate.value = true;
  createProvider.value = undefined;
  createVisible.value = true;
  const list = await queryChannelList({ siteId: row.siteId });
  createChannelId.value = props.channelSlug != null ? list.find((item: any) => item.slug === props.channelSlug)?.id : undefined;
  createChannelData.value = disableParentTree(toTree(list));
};

const handleCreate = async () => {
  if (props.articleId == null || createSiteId.value == null) {
    return;
  }
  const siteId = createSiteId.value;
  let created: any;
  createLoading.value = true;
  try {
    created = await createArticleTranslation(props.articleId, { targetSiteId: siteId, targetChannelId: createChannelId.value });
    createVisible.value = false;
    await fetchRows();
  } finally {
    createLoading.value = false;
  }
  if (createTranslate.value && aiTranslateAvailable.value && createProvider.value != null) {
    await runAiTranslate(siteId, String(created.id), createProvider.value);
  } else {
    ElMessage.success(t('article.i18n.createTranslation.success'));
  }
};
</script>

<template>
  <el-form-item v-if="rows.length > 0" label-position="top">
    <template #label>
      <label-tip message="article.i18n" help />
      <el-tooltip v-if="selfStale" :content="$t('article.i18n.selfStale.tooltip')" placement="top">
        <el-tag type="warning" size="small" class="ml-1" disable-transitions>{{ $t('article.i18n.stale') }}</el-tag>
      </el-tooltip>
    </template>
    <!-- 单个网格承载所有行（每行 5 个单元格），列宽按全部行统一计算：语言标签、站点名、状态标签、选择器、操作按钮逐列对齐；
         状态标签列右对齐紧贴选择器（“可能过期”始终在最右），无任何标签时省去该列以免出现双倍列距 -->
    <div class="grid w-full items-center gap-x-2 gap-y-1" :class="hasTags ? 'grid-cols-[auto_auto_auto_minmax(0,1fr)_auto]' : 'grid-cols-[auto_auto_minmax(0,1fr)_auto]'">
      <!-- 行 key 附带已链接文章 id+标题：remote 模式的 el-select 在值不变、仅选项标签变化时（预翻译改写标题）不刷新显示文本，
           整行重建即可刷新（<template v-for> 内的子元素不允许再单独设 key） -->
      <template v-for="row in rows" :key="`${row.siteId}:${row.article?.id ?? ''}:${row.article?.title ?? ''}`">
        <el-tag size="small" disable-transitions>{{ row.lang }}</el-tag>
        <span class="text-sm whitespace-nowrap">{{ row.siteName }}</span>
        <div v-if="hasTags" class="flex items-center justify-end gap-2">
          <el-tag v-if="row.article && row.article.status !== 0 && row.article.status !== 1" type="info" size="small" disable-transitions>
            {{ $t(`article.status.${row.article.status}`) }}
          </el-tag>
          <el-tooltip v-if="row.stale" :content="$t('article.i18n.stale.tooltip')" placement="top">
            <el-tag type="warning" size="small" disable-transitions>{{ $t('article.i18n.stale') }}</el-tag>
          </el-tooltip>
        </div>
        <el-select
          v-model="selection[row.siteId]"
          :placeholder="$t('article.i18n.pick')"
          :loading="candidateLoading[row.siteId]"
          :remote-method="(query: string) => searchCandidates(row, query)"
          remote
          filterable
          clearable
          size="small"
          class="w-full"
        >
          <el-option v-for="item in candidates[row.siteId]" :key="item.id" :label="`${item.title} (ID: ${item.id})`" :value="item.id"></el-option>
        </el-select>
        <div class="flex items-center">
          <el-button
            type="primary"
            size="small"
            :loading="saving[row.siteId]"
            :disabled="perm('article:update') || (selection[row.siteId] ?? null) === (row.article?.id ?? null)"
            @click="() => save(row)"
          >
            {{ $t('save') }}
          </el-button>
          <el-button
            v-if="isShowTier(2) && row.article == null"
            size="small"
            :disabled="perm('article:createTranslation') || currentUser.commercialTier < 2"
            :title="lockedTip(2)"
            @click="() => openCreate(row)"
          >
            {{ $t('article.op.createTranslation') }}
          </el-button>
          <el-button
            v-if="aiTranslateAvailable && row.article != null && row.article.status === 10"
            size="small"
            :loading="translating[row.siteId]"
            @click="() => handleAiTranslate(row)"
          >
            {{ $t('article.i18n.aiTranslate') }}
          </el-button>
        </div>
      </template>
    </div>
    <el-dialog v-model="translateDialogVisible" :title="$t('article.i18n.aiTranslate')" width="480px" append-to-body>
      <div class="text-sm">{{ $t('article.i18n.aiTranslate.target', [translateTarget?.siteName, translateTarget?.lang]) }}</div>
      <div class="mt-2 text-sm">{{ $t('article.i18n.aiTranslate.confirm') }}</div>
      <el-form label-position="top" class="mt-3" @submit.prevent>
        <el-form-item :label="$t('article.i18n.aiTranslate.provider')">
          <!-- 对话框关闭时传 undefined，再次打开按目标站点重新加载 -->
          <translate-provider-select
            v-model="translateProvider"
            :target-site-id="translateDialogVisible ? translateTarget?.siteId : undefined"
            class="w-full"
            @loaded="(available: boolean) => (translateAvailable = available)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="() => (translateDialogVisible = false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :disabled="!translateAvailable || translateProvider == null" @click="() => startAiTranslate()">
          {{ $t('article.i18n.aiTranslate.start') }}
        </el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="translateVisible"
      :title="$t('article.i18n.aiTranslate')"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
      :show-close="false"
    >
      <div class="text-sm">{{ $t('article.i18n.aiTranslate.waiting', [translateSeconds]) }}</div>
      <template #footer>
        <el-button @click="() => translateAbort?.abort()">{{ $t('article.i18n.aiTranslate.cancelWait') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="createVisible" :title="$t('article.op.createTranslation')" width="480px" append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="$t('article.i18n.createTranslation.channel')">
          <el-tree-select
            v-model="createChannelId"
            :data="createChannelData"
            node-key="id"
            :props="{ label: 'name', disabled: 'disabled' }"
            :render-after-expand="false"
            :placeholder="$t('article.i18n.createTranslation.channelHint')"
            check-strictly
            clearable
            class="w-full"
          />
        </el-form-item>
        <!-- el-checkbox 标签不换行，说明文字另起一行（与批量新增译文的服务商提示同款样式） -->
        <el-form-item v-if="aiTranslateAvailable">
          <el-checkbox v-model="createTranslate" :disabled="!createTranslateAvailable">{{ $t('article.i18n.createTranslation.aiTranslate') }}</el-checkbox>
          <div class="w-full text-xs text-gray-500">{{ $t('article.i18n.createTranslation.aiTranslate.hint') }}</div>
          <translate-provider-select
            v-model="createProvider"
            :target-site-id="createVisible ? createSiteId : undefined"
            class="mt-2 w-full"
            @loaded="onCreateProvidersLoaded"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="() => (createVisible = false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" :loading="createLoading" @click="() => handleCreate()">{{ $t('ok') }}</el-button>
      </template>
    </el-dialog>
  </el-form-item>
</template>

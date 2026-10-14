<script lang="ts">
import { ref as moduleRef } from 'vue';
/** 本次控制台会话内 AI 推荐已确认未开启/未配置（DISABLED / NOT_CONFIGURED）：后续打开抽屉直接走启发式候选，不再请求也不再提示 */
const aiOffForSession = moduleRef(false);

export interface RelatedRow {
  id: string;
  mutual: boolean;
  article?: any;
}
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { Rank, Close, Plus, MagicStick, Right, Switch } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { perm, currentUser } from '@/stores/useCurrentUser';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { queryArticleRelatedCandidates } from '@/api/content';
import { aiRelatedArticles, updateAiLogAccepted } from '@/api/ai';
import ArticlePickerDialog from '@/components/ArticlePicker/ArticlePickerDialog.vue';
import { articleStatusTagType } from './articleUtils';

/**
 * 相关文章面板（docs/spec/article-related/plan.md §5.2）。
 * v-model 为 [{ id, mutual, article }]：id 与 mutual 提交，article 只用于回显。
 * 行：拖拽排序 · 标题 · 栏目 · 日期 · 状态（非正常状态高亮，行保留由编辑决定）· 方向（→ 单向 / ↔ 互相关联）· 移除。
 * 「被关联」列出把本文列为相关文章的文章，可一键加入（即成互相关联）。
 * 「智能推荐」：社区版显示启发式候选（标题相似 / 共同标签 / 同栏目提示），T2 且站点开启时改为 AI 重排并给出理由。
 */
defineOptions({
  name: 'ArticleRelatedPanel',
});
const props = defineProps<{
  modelValue: RelatedRow[];
  /** 被关联列表（后端详情返回） */
  incoming: any[];
  /** 当前文章 ID（新文章为空） */
  articleId?: string;
  /** 候选种子：表单当前值 */
  seed: { title?: string; channelId?: string; tagNames?: string[]; text?: string };
  disabled?: boolean;
  max?: number;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: RelatedRow[]] }>();
const { t } = useI18n();
const currentSiteStore = useCurrentSiteStore();

const MAX = computed(() => props.max ?? 20);
// vuedraggable 就地改写 :list，须持有数组本身：以 props.modelValue 为准，变更后整体 emit
const rows = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});
const selectedIds = computed(() => rows.value.map((r) => r.id));
const incomingIds = computed(() => new Set((props.incoming ?? []).map((a) => a.id)));
const full = computed(() => rows.value.length >= MAX.value);
const excludeIds = computed(() => (props.articleId ? [props.articleId] : []));

const aiAvailable = computed(() => currentUser.commercialTier >= 2 && currentSiteStore.currentSite?.ai?.relatedAssist === true && !perm('ai:assist'));

// 同一时机加入多篇须合并成一次 emit：父级的 v-model 在下一次渲染才回流，逐篇 emit 会互相覆盖，只剩最后一篇
const addAll = (articles: any[]) => {
  const next = [...rows.value];
  for (const article of articles) {
    if (next.length >= MAX.value || next.some((r) => r.id === article.id) || article.id === props.articleId) continue;
    // 对方已把本文列为相关文章时，新行默认互相关联，保存不会删掉对方的行
    next.push({ id: article.id, mutual: incomingIds.value.has(article.id), article });
  }
  if (next.length !== rows.value.length) rows.value = next;
};
const add = (article: any) => addAll([article]);
const remove = (index: number) => {
  rows.value = rows.value.filter((_, i) => i !== index);
};
const toggleMutual = (row: RelatedRow) => {
  rows.value = rows.value.map((r) => (r === row ? { ...r, mutual: !r.mutual } : r));
};
const onDragEnd = () => {
  // draggable 已就地改写数组，通知父级以标记未保存
  rows.value = [...rows.value];
};

const pickerVisible = ref(false);

// 智能推荐抽屉：启发式候选或 AI 重排结果，勾选后批量加入
const suggestVisible = ref(false);
const suggestLoading = ref(false);
const suggestions = ref<any[]>([]);
const checked = ref<string[]>([]);
const suggestByAi = ref(false);
/** 抽屉副标题的退回说明：aiOff = 全局未开启/未配置（会话内记住），fallback = 本次调用失败（额度、服务商异常等） */
const suggestNotice = ref<'' | 'aiOff' | 'fallback'>('');
let aiLogId: string | null = null;
let aiAcceptFired = false;

const fetchSuggestions = async () => {
  suggestLoading.value = true;
  try {
    const exclude = [...selectedIds.value, ...suggestions.value.map((s) => s.id)];
    let aiDone = false;
    suggestNotice.value = aiAvailable.value && aiOffForSession.value ? 'aiOff' : '';
    if (aiAvailable.value && !aiOffForSession.value) {
      // 站点开关只反映本站意愿；全局 AI 未开启、服务商不可用或额度耗尽时静默退回启发式候选，按钮不因此失效。
      // 退回原因只写在抽屉副标题里，不弹提示；未开启/未配置在会话内记住，避免每次打开都请求一次
      try {
        const data = await aiRelatedArticles(
          { id: props.articleId, title: props.seed.title, text: props.seed.text, channelId: props.seed.channelId, tagNames: props.seed.tagNames ?? [], excludeIds: exclude },
          true,
        );
        suggestions.value = data.items ?? [];
        aiLogId = data.logId ?? null;
        aiAcceptFired = false;
        suggestByAi.value = true;
        aiDone = true;
      } catch (e: any) {
        const code = e?.response?.data?.error;
        if (code === 'DISABLED' || code === 'NOT_CONFIGURED') aiOffForSession.value = true;
        suggestNotice.value = aiOffForSession.value ? 'aiOff' : 'fallback';
      }
    }
    if (!aiDone) {
      suggestions.value = await queryArticleRelatedCandidates({
        id: props.articleId,
        title: props.seed.title,
        channelId: props.seed.channelId,
        tagNames: (props.seed.tagNames ?? []).join(','),
        excludeIds: exclude.join(','),
        limit: 12,
      });
      suggestByAi.value = false;
    }
    checked.value = [];
  } finally {
    suggestLoading.value = false;
  }
};
const openSuggest = async () => {
  suggestions.value = [];
  suggestVisible.value = true;
  await fetchSuggestions();
};
const addChecked = () => {
  const picked = suggestions.value.filter((s) => checked.value.includes(s.id));
  addAll(picked.map((s) => ({ id: s.id, title: s.title, url: s.url, image: s.image, publishDate: s.publishDate, status: s.status, channel: { id: s.channelId, name: s.channelName } })));
  if (picked.length > 0 && suggestByAi.value && aiLogId != null && !aiAcceptFired) {
    aiAcceptFired = true;
    updateAiLogAccepted(aiLogId).catch(() => undefined);
  }
  suggestVisible.value = false;
};
const hintLabel = (s: any, hint: string) => (hint === 'sharedTags' ? `${t('article.related.hint.sharedTags')}${t('punct.colon')}${(s.sharedTags ?? []).join(t('punct.enum'))}` : t(`article.related.hint.${hint}`));
const isHidden = (status: number) => status !== 0 && status !== 1;
</script>

<template>
  <div class="w-full">
    <div class="rounded border border-gray-200">
      <div class="flex items-center border-b border-gray-200 px-2 py-1">
        <span class="text-xs text-gray-secondary">{{ rows.length }} / {{ MAX }}</span>
        <div class="flex-1"></div>
        <el-button size="small" :icon="Plus" :disabled="disabled || full || perm('article:list') || perm('channel:list')" @click="() => (pickerVisible = true)">{{ $t('article.related.pick') }}</el-button>
        <el-tooltip :content="aiAvailable && !aiOffForSession ? $t('article.related.suggest.aiTip') : $t('article.related.suggest.tip')" placement="top">
          <el-button size="small" :icon="MagicStick" :disabled="disabled || full || perm('article:list')" :loading="suggestLoading" @click="openSuggest">{{ $t('article.related.suggest') }}</el-button>
        </el-tooltip>
      </div>
      <div v-if="rows.length === 0" class="px-2 py-3 text-xs text-gray-secondary">{{ $t('article.related.empty') }}</div>
      <draggable v-else :list="rows" item-key="id" handle=".drag-handle" :disabled="disabled" class="divide-y divide-gray-200" @end="onDragEnd">
        <template #item="{ element, index }">
          <div class="flex items-center px-2 py-1 text-sm">
            <el-icon class="drag-handle mr-2 cursor-move text-gray-secondary"><Rank /></el-icon>
            <span class="mr-2 w-5 text-xs text-gray-secondary">{{ index + 1 }}</span>
            <img v-if="element.article?.image" :src="element.article.image" alt="" class="mr-2 h-8 w-12 rounded object-cover" />
            <span class="flex-1 truncate" :title="element.article?.title">{{ element.article?.title ?? element.id }}</span>
            <span class="ml-2 hidden w-28 truncate text-xs text-gray-secondary md:inline" :title="element.article?.channel?.name">{{ element.article?.channel?.name }}</span>
            <span v-if="element.article?.publishDate" class="ml-2 hidden w-20 text-xs text-gray-secondary md:inline">{{ dayjs(element.article.publishDate).format('YYYY-MM-DD') }}</span>
            <el-tooltip v-if="element.article?.status != null && isHidden(element.article.status)" :content="$t('article.related.status.hidden')" placement="top">
              <el-tag :type="articleStatusTagType(element.article.status)" size="small" class="ml-2" disable-transitions>{{ $t(`article.status.${element.article.status}`) }}</el-tag>
            </el-tooltip>
            <el-tooltip :content="element.mutual ? $t('article.related.mutual') : $t('article.related.oneWay')" placement="top">
              <el-button link :type="element.mutual ? 'primary' : 'default'" :icon="element.mutual ? Switch : Right" class="ml-2" :disabled="disabled" @click="() => toggleMutual(element)" />
            </el-tooltip>
            <el-button link :icon="Close" class="ml-1" :disabled="disabled" @click="() => remove(index)" />
          </div>
        </template>
      </draggable>
    </div>
    <el-collapse v-if="incoming && incoming.length > 0" class="mt-2 border-0">
      <el-collapse-item :title="`${$t('article.related.incoming')}（${incoming.length}）`" name="incoming">
        <div v-for="item in incoming" :key="item.id" class="flex items-center py-1 text-sm">
          <span class="flex-1 truncate" :title="item.title">{{ item.title }}</span>
          <span class="ml-2 hidden w-28 truncate text-xs text-gray-secondary md:inline">{{ item.channel?.name }}</span>
          <el-tag v-if="item.status != null && isHidden(item.status)" :type="articleStatusTagType(item.status)" size="small" class="ml-2" disable-transitions>{{ $t(`article.status.${item.status}`) }}</el-tag>
          <el-button v-if="selectedIds.includes(item.id)" link type="info" size="small" class="ml-2" disabled :icon="Switch">{{ $t('article.related.mutual') }}</el-button>
          <el-button v-else link type="primary" size="small" class="ml-2" :disabled="disabled || full" @click="() => add(item)">{{ $t('article.related.adopt') }}</el-button>
        </div>
      </el-collapse-item>
    </el-collapse>

    <article-picker-dialog v-model="pickerVisible" :selected-ids="selectedIds" :exclude-ids="excludeIds" :max-select="MAX" :default-channel-id="seed.channelId" :statuses="[0, 1]" :title="$t('article.related.pick')" @pick="addAll" />

    <el-drawer v-model="suggestVisible" :title="$t('article.related.suggest')" :size="480" append-to-body>
      <div class="mb-2 text-xs text-gray-secondary">
        {{ suggestNotice ? $t(`article.related.suggest.${suggestNotice}`) : suggestByAi ? $t('article.related.suggest.aiTip') : $t('article.related.suggest.tip') }}
      </div>
      <el-empty v-if="!suggestLoading && suggestions.length === 0" :description="$t('article.related.suggest.empty')" />
      <el-checkbox-group v-model="checked" v-loading="suggestLoading" class="block">
        <div v-for="s in suggestions" :key="s.id" class="flex items-start border-b py-2">
          <el-checkbox :value="s.id" class="mr-2 !h-auto" />
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm" :title="s.title">{{ s.title }}</div>
            <div class="mt-0.5 text-xs text-gray-secondary">
              <span>{{ s.channelName }}</span>
              <span class="ml-2">{{ dayjs(s.publishDate).format('YYYY-MM-DD') }}</span>
            </div>
            <div v-if="s.reason" class="mt-1 text-xs text-gray-500">{{ s.reason }}</div>
            <div v-else class="mt-1 flex flex-wrap gap-1">
              <el-tag v-for="hint in s.hints" :key="hint" size="small" type="info" effect="plain" disable-transitions>{{ hintLabel(s, hint) }}</el-tag>
            </div>
          </div>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button :loading="suggestLoading" @click="fetchSuggestions">{{ $t('article.related.suggest.more') }}</el-button>
        <el-button type="primary" :disabled="checked.length === 0" @click="addChecked">{{ $t('article.related.suggest.addChecked') }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

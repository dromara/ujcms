<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Check, Search } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import { queryArticlePage, queryChannelList } from '@/api/content';
import { pageLayout, pageSizes } from '@/utils/common';
import { toTree } from '@/utils/tree';
import { articleStatusTagType } from '@/views/content/components/articleUtils';

/**
 * 共享的文章选择对话框（docs/spec/article-related/plan.md §5.1）。
 * 搜索栏一行：关键词（范围下拉：标题 / 标题+正文）、栏目子树、状态、发布日期；栏目/状态/日期改变即查询，
 * 关键词按回车或搜索图标查询。已选行置灰并标"已选"；多选时对话框保持打开逐条添加，单选时点击即返回并关闭。
 * 最近一次选用的栏目记在 localStorage（仅本机便利，不入状态）。
 */
defineOptions({
  name: 'ArticlePickerDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  /** 是否多选：多选逐条 pick 且不关闭；单选 pick 一条即关闭 */
  multiple: { type: Boolean, default: true },
  /** 已选文章 ID：列表中显示"已选"并禁用 */
  selectedIds: { type: Array as () => string[], default: () => [] },
  /** 不显示的文章 ID（如当前编辑的文章自身） */
  excludeIds: { type: Array as () => string[], default: () => [] },
  /** 多选上限（含已选）；达到后添加按钮禁用 */
  maxSelect: { type: Number, default: undefined },
  /** 打开时预选的栏目 */
  defaultChannelId: { type: String, default: undefined },
  /** 可选状态集合；默认只查已发布。只有一种状态时不显示状态筛选 */
  statuses: { type: Array as () => number[], default: () => [0] },
  title: { type: String, default: undefined },
});
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; pick: [rows: any[]] }>();

const CHANNEL_STORAGE_KEY = 'ujcms_article_picker_channel';

type Scope = 'title' | 'titleText';
const keyword = ref<string>('');
const scope = ref<Scope>('title');
const channelId = ref<string | undefined>();
const channelList = ref<any[]>([]);
const status = ref<number | undefined>();
const dateRange = ref<[Date, Date] | undefined>();
const data = ref<any[]>([]);
const loading = ref<boolean>(false);
const page = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);

const selected = computed(() => new Set(props.selectedIds));
const full = computed(() => props.maxSelect != null && props.selectedIds.length >= props.maxSelect);
const rows = computed(() => data.value.filter((row) => !props.excludeIds.includes(row.id)));

const readStoredChannel = (): string | undefined => {
  try {
    return localStorage.getItem(CHANNEL_STORAGE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
};
const storeChannel = (id?: string) => {
  try {
    if (id) localStorage.setItem(CHANNEL_STORAGE_KEY, id);
    else localStorage.removeItem(CHANNEL_STORAGE_KEY);
  } catch {
    // 私密窗口等无存储时忽略
  }
};

const fetchChannelList = async () => {
  if (channelList.value.length > 0) return;
  channelList.value = toTree(await queryChannelList({ isArticlePermission: true, isReal: true }));
};

const fetchData = async () => {
  loading.value = true;
  try {
    const params: Record<string, any> = { page: page.value, pageSize: pageSize.value };
    const statusList = status.value != null ? [status.value] : props.statuses;
    params.Q_In_status_Short = statusList.join(',');
    const q = keyword.value.trim();
    if (q !== '') {
      if (scope.value === 'titleText') {
        // 同组无子组 = OR：标题或正文含关键词（正文为 LIKE，大表较慢）
        params['Q_Contains_1_@articleExt-title'] = q;
        params['Q_Contains_1_@articleExt-text'] = q;
      } else {
        params['Q_Contains_@articleExt-title'] = q;
      }
    }
    if (channelId.value) params.subChannelId = channelId.value;
    if (dateRange.value) {
      params.Q_GE_publishDate_DateTime = dateRange.value[0];
      params.Q_LE_publishDate_DateTime = dateRange.value[1];
    }
    const {
      content,
      page: { totalElements },
    } = await queryArticlePage(params);
    data.value = content ?? [];
    total.value = Number(totalElements);
  } finally {
    loading.value = false;
  }
};
const handleSearch = () => {
  page.value = 1;
  storeChannel(channelId.value);
  fetchData();
};

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return;
    keyword.value = '';
    scope.value = 'title';
    status.value = undefined;
    dateRange.value = undefined;
    page.value = 1;
    channelId.value = props.defaultChannelId ?? readStoredChannel();
    await fetchChannelList();
    // 存下来的栏目可能已删除或无权限：树里找不到则回退为全部
    if (channelId.value && !findInTree(channelList.value, channelId.value)) channelId.value = undefined;
    await fetchData();
  },
);
const findInTree = (nodes: any[], id: string): boolean => nodes.some((n) => n.id === id || (n.children && findInTree(n.children, id)));

const close = () => emit('update:modelValue', false);
const handlePick = (row: any) => {
  if (selected.value.has(row.id) || full.value) return;
  emit('pick', [row]);
  if (!props.multiple) close();
};
const rowClassName = ({ row }: { row: any }) => (selected.value.has(row.id) ? 'article-picker-row--selected' : '');
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title ?? $t('articlePicker.title')" width="min(1000px, 94vw)" append-to-body class="article-picker-dialog" @update:model-value="(v: boolean) => $emit('update:modelValue', v)">
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <el-input v-model="keyword" :placeholder="$t('articlePicker.keyword')" clearable class="article-picker-search flex-1" @keyup.enter="handleSearch" @clear="handleSearch">
        <template #prepend>
          <el-select v-model="scope" class="w-32">
            <el-option value="title" :label="$t('articlePicker.scope.title')" />
            <el-option value="titleText" :label="$t('articlePicker.scope.titleText')" />
          </el-select>
        </template>
        <template #append>
          <el-button :icon="Search" :title="$t('search')" @click="handleSearch" />
        </template>
      </el-input>
      <el-tree-select
        v-model="channelId"
        :data="channelList"
        node-key="id"
        :props="{ label: 'name' }"
        :render-after-expand="false"
        check-strictly
        clearable
        :placeholder="$t('article.allChannel')"
        class="w-44"
        @change="handleSearch"
      />
      <el-select v-if="statuses.length > 1" v-model="status" clearable :placeholder="$t('article.status')" class="w-28" @change="handleSearch">
        <el-option v-for="s in statuses" :key="s" :value="s" :label="$t(`article.status.${s}`)" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        unlink-panels
        :start-placeholder="$t('articlePicker.dateFrom')"
        :end-placeholder="$t('articlePicker.dateTo')"
        class="w-60 flex-none"
        @change="handleSearch"
      />
    </div>
    <el-table v-loading="loading" :data="rows" max-height="440" :row-class-name="rowClassName" @row-dblclick="(row: any) => handlePick(row)">
      <el-table-column property="title" :label="$t('article.title')" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <a :href="row.dynamicFullUrl ?? row.url" target="_blank" rel="noopener" class="hover:underline" @click.stop>{{ row.title }}</a>
        </template>
      </el-table-column>
      <el-table-column property="channel.name" :label="$t('article.channel')" width="150" show-overflow-tooltip />
      <el-table-column :label="$t('article.publishDate')" width="120">
        <template #default="{ row }">{{ dayjs(row.publishDate).format('YYYY-MM-DD') }}</template>
      </el-table-column>
      <el-table-column v-if="statuses.length > 1" :label="$t('article.status')" width="90">
        <template #default="{ row }">
          <el-tag :type="articleStatusTagType(row.status)" size="small" disable-transitions>{{ $t(`article.status.${row.status}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('table.action')" width="90" align="center">
        <template #default="{ row }">
          <span v-if="selected.has(row.id)" class="inline-flex items-center gap-1 text-success text-sm">
            <el-icon><Check /></el-icon>
            <span>{{ $t('articlePicker.selected') }}</span>
          </span>
          <el-button v-else type="primary" link :disabled="full" @click="() => handlePick(row)">{{ multiple ? $t('articlePicker.add') : $t('select') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="pageSizes"
          :layout="pageLayout"
          size="small"
          background
          @size-change="() => fetchData()"
          @current-change="() => fetchData()"
        />
        <div class="flex items-center gap-3">
          <span v-if="multiple" class="text-sm" :class="full ? 'text-warning' : 'text-gray-secondary'">
            {{ maxSelect != null ? $t('articlePicker.selectedCountMax', { count: selectedIds.length, max: maxSelect }) : $t('articlePicker.selectedCount', { count: selectedIds.length }) }}
          </span>
          <el-button @click="close">{{ multiple ? $t('close') : $t('cancel') }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
// 关键词框在一行里占满剩余宽度，但不小于范围下拉 + 输入 + 图标的可读宽度
.article-picker-search {
  min-width: 360px;
}
// 已选行置灰：仍可看到标题与栏目，但不再是候选
:deep(.article-picker-row--selected) > td {
  color: var(--el-text-color-placeholder);
}
:deep(.article-picker-row--selected):hover > td {
  background-color: transparent;
}
</style>

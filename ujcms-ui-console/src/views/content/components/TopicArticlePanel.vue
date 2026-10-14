<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Delete, Grid, Plus } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import { perm } from '@/stores/useCurrentUser';
import { queryArticleTopicList, replaceArticleTopicList } from '@/api/content';
import ArticlePickerDialog from '@/components/ArticlePicker/ArticlePickerDialog.vue';
import { articleStatusTagType } from './articleUtils';

defineOptions({
  name: 'TopicArticlePanel',
});
const props = defineProps<{ topicId: string }>();
const { t } = useI18n();

interface Row {
  articleId: string;
  topicId: string;
  order: number;
  article?: any;
}

const tableEl = ref<any>();
const rows = ref<Row[]>([]);
const loading = ref(false);
const dirty = ref(false);
const addedArticleIds = computed(() => new Set(rows.value.map((r) => r.articleId)));
let sortable: any;

const fetchRows = async () => {
  if (!props.topicId) {
    rows.value = [];
    dirty.value = false;
    return;
  }
  loading.value = true;
  try {
    rows.value = await queryArticleTopicList(props.topicId);
    dirty.value = false;
  } finally {
    loading.value = false;
  }
};

const destroyDrag = () => {
  if (sortable !== undefined) {
    sortable.destroy();
    sortable = undefined;
  }
};
const initDrag = () => {
  destroyDrag();
  const tbody = (tableEl.value?.$el as HTMLElement | undefined)?.querySelector('.el-table__body-wrapper tbody');
  if (!tbody) return;
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 200,
    onEnd: (event: any) => {
      const { oldIndex, newIndex } = event;
      if (oldIndex !== newIndex) {
        const moved = rows.value.splice(oldIndex, 1)[0];
        if (moved) {
          rows.value.splice(newIndex, 0, moved);
          dirty.value = true;
        }
      }
    },
  });
};

watch(
  () => props.topicId,
  async () => {
    await fetchRows();
    nextTick(() => initDrag());
  },
);

onMounted(async () => {
  await fetchRows();
  nextTick(() => initDrag());
});
onBeforeUnmount(destroyDrag);

const handleRemove = (idx: number) => {
  rows.value.splice(idx, 1);
  dirty.value = true;
};

const handleSave = async () => {
  await replaceArticleTopicList(
    props.topicId,
    rows.value.map((r) => r.articleId),
    rows.value.map((_, i) => i * 10000),
  );
  ElMessage.success(t('success'));
  await fetchRows();
};

// 共享文章选择对话框（docs/spec/article-related/plan.md §5.1）：只查已发布，已添加的行禁用
const pickerVisible = ref(false);
const openPicker = () => {
  pickerVisible.value = true;
};
const handlePickerAdd = (article: any) => {
  if (rows.value.find((r) => r.articleId === article.id)) {
    ElMessage.warning(t('error.duplicate'));
    return;
  }
  rows.value.push({ articleId: article.id, topicId: props.topicId, order: rows.value.length * 10000, article });
  dirty.value = true;
};
</script>

<template>
  <div>
    <div class="mb-3">
      <el-button type="primary" :icon="Plus" :disabled="perm('topic:update')" @click="openPicker">{{ $t('add') }}</el-button>
      <el-button type="success" :disabled="!dirty || perm('topic:update')" @click="handleSave">{{ $t('save') }}</el-button>
    </div>
    <el-table ref="tableEl" v-loading="loading" :data="rows" row-key="articleId">
      <el-table-column width="42">
        <template #default>
          <el-icon class="text-lg align-middle cursor-move text-gray-secondary drag-handle">
            <Grid />
          </el-icon>
        </template>
      </el-table-column>
      <el-table-column property="article.title" :label="$t('article.title')" min-width="240" show-overflow-tooltip />
      <el-table-column property="article.channel.name" :label="$t('article.channel')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="$t('article.status')" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.article?.status != null" :type="articleStatusTagType(row.article.status)" size="small">{{ $t(`article.status.${row.article.status}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('table.action')" width="100">
        <template #default="{ $index }">
          <el-button :icon="Delete" type="primary" link size="small" @click="handleRemove($index)">{{ $t('delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <article-picker-dialog v-model="pickerVisible" :selected-ids="[...addedArticleIds]" :statuses="[0]" @pick="(picked) => picked.forEach(handlePickerAdd)" />
  </div>
</template>

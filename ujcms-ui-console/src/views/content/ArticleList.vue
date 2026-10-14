<script setup lang="ts">
import { computed, defineAsyncComponent, watch, ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { Plus, Delete, ArrowDown, MoreFilled, DocumentCopy, Grid, Connection } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import Sortable from 'sortablejs';
import dayjs from 'dayjs';
import { perm, currentUser, hasPermission, isShowTier } from '@/stores/useCurrentUser';
import { pageSizes, pageLayout, toParams, resetParams } from '@/utils/common';
import { lockedTip, showCommercialNotice } from '@/utils/commercial';
import { toTree, findTreeItem, filterTreeNodeByName } from '@/utils/tree';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { useRouteState, stringParam, numberParam } from '@/composables/useRouteState';
import { queryBlockList, queryModelList, queryDictList } from '@/api/config';
import { querySiteList } from '@/api/system';
import {
  deleteArticle,
  submitArticle,
  archiveArticle,
  offlineArticle,
  completelyDeleteArticle,
  restoreArticle,
  stickyArticle,
  updateArticleOrder,
  queryArticlePage,
  queryArticleStatusCounts,
  queryChannelList,
  queryTopicList,
  queryArticleI18nStatus,
  deleteBlockItem,
} from '@/api/content';
import { ColumnList, ColumnSetting } from '@/components/TableList';
import { QueryForm, QueryItem } from '@/components/QueryForm';
import LabelTip from '@/components/LabelTip.vue';
import CollapsibleAside from '@/components/CollapsibleAside.vue';
import ArticleForm from './ArticleForm.vue';
import ArticleTranslateBatch from './components/ArticleTranslateBatch.vue';
import ArticlePushForm from './ArticlePushForm.vue';
import ArticleMoveForm from './ArticleMoveForm.vue';
import ArticleExportDialog from './ArticleExportDialog.vue';
import ArticleImportDialog from './ArticleImportDialog.vue';
import WechatPushForm from './WechatPushForm.vue';
import BlockItemForm from './BlockItemForm.vue';
import { openArticleLink, articleStatusTagType } from './components/articleUtils';
import {
  articleStatuses,
  reviewStatuses,
  articleActionRules,
  articleActionLabels,
  articleActionPerms,
  primaryStatusAction,
  canSubmit,
  canArchive,
  canOffline,
  type ArticleStatusAction,
} from './components/articleActions';

// 流程任务对话框依赖 bpmn-js Viewer（约 200 kB）；异步加载，以免随列表页一并下载。对话框本身仍常驻挂载，行为不变。
const ProcessTaskList = defineAsyncComponent(() => import('@/views/system/ProcessTaskList.vue'));

defineOptions({
  name: 'ArticleList',
});
const { t } = useI18n();
const params = ref<any>({});
const sort = ref<any>();
const currentPage = ref<number>(1);
const pageSize = ref<number>(10);
const total = ref<number>(0);
const table = ref<any>();
const data = ref<any[]>([]);
const selection = ref<any[]>([]);
const tableLoading = ref<boolean>(false);
const formVisible = ref<boolean>(false);
const translateBatchVisible = ref<boolean>(false);
const currentSiteStore = useCurrentSiteStore();
// 工具栏“批量翻译”入口仅在当前站点有语言组同伴时显示。判定需 site:list（弹窗本就依赖站点列表）；
// 无该权限时不判定、保持显示，由弹窗内提示
const inLangGroup = ref<boolean>(true);
const fetchLangGroup = async () => {
  const current = currentSiteStore.currentSite;
  if (perm('site:list') || current == null) {
    inLangGroup.value = true;
    return;
  }
  const masterId = current.langParentId ?? current.id;
  const sites = await querySiteList();
  inLangGroup.value = sites.some((site: any) => (site.langParentId ?? site.id) === masterId && site.id !== current.id);
};
watch(() => currentSiteStore.currentSiteId, fetchLangGroup);
const beanId = ref<string>();
const beanIds = computed(() => data.value.map((row) => row.id));
const isSorted = ref<boolean>(false);
const action = ref<'add' | 'edit' | 'copy'>('edit');
const blockList = ref<any[]>([]);
const topicList = ref<any[]>([]);
// 置顶筛选：是 → sticky_ ≥ 1，否 → sticky_ ≤ 0（0-999，0 为不置顶）。两个键互斥，由同一个下拉控制，
// 键名登记在 query-item 的 name 上，切换/移除筛选项时由 QueryForm 一并清理
const stickyFilter = computed<string | undefined>({
  get: () => (params.value.Q_GE_sticky_Short != null ? 'true' : params.value.Q_LE_sticky_Short != null ? 'false' : undefined),
  set: (value) => {
    delete params.value.Q_GE_sticky_Short;
    delete params.value.Q_LE_sticky_Short;
    if (value === 'true') {
      params.value.Q_GE_sticky_Short = 1;
    } else if (value === 'false') {
      params.value.Q_LE_sticky_Short = 0;
    }
  },
});
// 经一对多表（区块项、专题、TAG）筛选时会产生重复行，交给数据库去重
const distinctKeys = ['Q_In_@BlockItem-blockId_Long', 'Q_In_@ArticleTopic-topicId_Long', 'Q_Contains_@ArticleTag-tag-name'];
const isDistinct = computed<boolean>(() => distinctKeys.some((key) => (params.value[key]?.length ?? 0) > 0));
const recommendVisible = ref<boolean>(false);
const recommendBlockId = ref<string>();
const recommendArticleId = ref<string>();
const recommendTitle = ref<string>();
const recommendDescription = ref<string>();
const recommendVideo = ref<string>();
const recommendImages = ref<string[]>();

const channelTree = ref<any>();
const channelTreeData = ref<any[]>([]);
const channelTreeLoading = ref<boolean>(false);
const channelTreeFilter = ref<string>();
// 导航上下文（选中栏目、状态行）存于路由 query：刷新、页签往返后恢复；status 亦供首页待办等深链（?status=22）
const { channelId, status } = useRouteState({
  channelId: stringParam(),
  status: numberParam(-1, articleStatuses),
});
// 以 ID 为准、节点由栏目树派生：树每次重载后节点对象即更新；树加载前为 undefined
const channel = computed<any>(() => (channelId.value != null ? findTreeItem(channelTreeData.value, (item: any) => item.id === channelId.value) : undefined));
const asideCollapsed = ref<boolean>(false);
watch(channelTreeFilter, (val) => {
  channelTree.value?.filter(val);
});

const instanceId = ref<string>();
const taskListVisible = ref<boolean>(false);


const pushFormVisible = ref<boolean>(false);
const pushType = ref<'internal' | 'external'>('internal');
const wechatPushVisible = ref<boolean>(false);
// 推送公众号的文章ID列表：行操作为单篇，工具栏按钮为勾选的多篇（按表格顺序）
const wechatPushIds = ref<string[]>([]);

const modelList = ref<any[]>([]);
const model = computed(() => modelList.value.find((item: any) => item.id === channel.value?.articleModelId));
const fields = computed(() => JSON.parse(model.value?.customs || '[]'));
const dictListMap = ref<Record<string, any[]>>({});

watch(fields, async () => {
  dictListMap.value = {};
  fields.value
    .filter((field: any) => ['radio', 'select', 'checkbox', 'multipleSelect'].indexOf(field.type) >= 0)
    .map((field: any) => field.dictTypeId)
    .forEach(async (typeId: string) => {
      dictListMap.value[typeId] = (await queryDictList({ typeId })).map((item: any) => ({ label: item.name, value: item.value }));
    });
});

// 勾选的文章按表格显示顺序排列，作为多图文的初始顺序（弹窗内可拖动调整）
const wechatPushSelectionIds = computed(() => {
  return data.value.filter((row: any) => selection.value.some((item: any) => item.id === row.id)).map((row: any) => row.id);
});

// 工具栏“批量”菜单里的状态操作（删除单独成按钮，恢复与彻底删除在菜单末尾）
const batchStatusActions: ArticleStatusAction[] = ['submit', 'archive', 'offline'];
const actionApis: Record<ArticleStatusAction, (ids: string[]) => Promise<any>> = {
  submit: submitArticle,
  archive: archiveArticle,
  offline: offlineArticle,
  delete: deleteArticle,
  restore: restoreArticle,
  completelyDelete: completelyDeleteArticle,
};
const handleExecute = async (action: ArticleStatusAction, ids: string[]) => {
  await actionApis[action](ids);
  refresh();
  ElMessage.success(t('success'));
};
// 先确认、再执行；取消即作罢。彻底删除不可恢复，确认按钮用危险色
const confirmThen = async (message: string, title: string, danger: boolean, run: () => Promise<void>) => {
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning', confirmButtonClass: danger ? 'el-button--danger' : undefined });
  } catch {
    return;
  }
  await run();
};
// 批量操作（docs/spec/article-management/plan.md D3）：每一行都可以勾选；某个操作不适用的行在确认框里计数并略过，
// 而不是换一个操作就有一批行勾不上
const handleBatch = async (action: ArticleStatusAction) => {
  const actionLabel = t(articleActionLabels[action]);
  const total = selection.value.length;
  const ids = selection.value.filter((row: any) => articleActionRules[action](row)).map((row: any) => row.id);
  if (ids.length === 0) {
    ElMessage.warning(t('article.batch.noneEligible', { action: actionLabel }));
    return;
  }
  const skipped = total - ids.length;
  let message = t('article.batch.confirm', { count: ids.length, action: actionLabel });
  if (action === 'completelyDelete') {
    message = t('article.batch.confirmCompletelyDelete', { count: ids.length });
  } else if (skipped > 0) {
    message = t('article.batch.confirmSkipped', { total, skipped, count: ids.length, action: actionLabel });
  }
  await confirmThen(message, actionLabel, action === 'completelyDelete', () => handleExecute(action, ids));
};
// “更多”菜单里的状态操作：下拉项包不了气泡确认框，改用对话框确认
const handleRowAction = async (action: ArticleStatusAction, row: any) => {
  const actionLabel = t(articleActionLabels[action]);
  const message =
    action === 'completelyDelete' ? t('article.confirm.completelyDelete', { title: row.title }) : t('article.confirm.action', { title: row.title, action: actionLabel });
  await confirmThen(message, actionLabel, action === 'completelyDelete', () => handleExecute(action, [row.id]));
};

// 列表与状态计数共用的条件：查询项、去重、选中的栏目。状态、排序、分页只属于列表
const listParams = () => ({
  ...toParams(params.value),
  Q_Distinct: isDistinct.value ? true : undefined,
  subChannelId: channelId.value,
});
// 状态页签上的计数：同样的条件下各状态有几篇。只在条件变了（搜索、重置、换栏目）和文章状态变了（各种操作之后）时
// 重新取；翻页、排序、切换状态页签都不影响它。取不到不影响列表——页签照常，只是没有数字
const statusCounts = ref<Record<number, number>>({});
let statusCountsSeq = 0;
// 计数是按哪一组条件取的。查询表单里改了值、没按“搜索”就翻页或切页签，列表按新值取了，计数也要跟上（见 fetchData）
let countedFilter = '';
const fetchStatusCounts = async () => {
  const seq = ++statusCountsSeq;
  const filter = listParams();
  countedFilter = JSON.stringify(filter);
  try {
    const list = await queryArticleStatusCounts(filter);
    if (seq === statusCountsSeq) {
      statusCounts.value = Object.fromEntries(list.map((item) => [item.status, item.total]));
    }
  } catch {
    if (seq === statusCountsSeq) {
      statusCounts.value = {};
    }
  }
};
// 待审核、审核中、已退回只有审批流程才会产生：有流程引擎（商业版）、或确有文章处于该状态、或正停在该页签（深链）时才显示
const visibleStatuses = computed<number[]>(() =>
  articleStatuses.filter((s) => !reviewStatuses.includes(s) || currentUser.commercialTier >= 1 || (statusCounts.value[s] ?? 0) > 0 || status.value === s),
);
const refresh = () => {
  fetchData();
  fetchStatusCounts();
};
// 导入（商业版 T1）：社区版里入口照常显示，点开是功能介绍
const importVisible = ref<boolean>(false);
const handleImport = () => {
  if (currentUser.commercialTier < 1) {
    showCommercialNotice(1, t('commercial.feature.articleImport'));
    return;
  }
  importVisible.value = true;
};
// 导出：范围就是列表现在显示的——连同状态页签与排序。用列表上一次真正发出的查询，不用查询表单里还没按“搜索”的值：
// 对话框里写的篇数来自那次查询，文件也应当是那次查询的结果
const exportVisible = ref<boolean>(false);
const appliedQuery = ref<Record<string, any>>({});

const fetchData = async () => {
  tableLoading.value = true;
  const filter = listParams();
  const query = { ...filter, Q_EQ_status_Short: status.value !== -1 ? status.value : undefined, Q_OrderBy: sort.value };
  try {
    const {
      content,
      page: { totalElements },
    } = await queryArticlePage({ ...query, page: currentPage.value, pageSize: pageSize.value });
    appliedQuery.value = query;
    isSorted.value = sort.value !== undefined;
    data.value = content;
    total.value = Number(totalElements);
    fetchI18nStatus();
    if (JSON.stringify(filter) !== countedFilter) {
      fetchStatusCounts();
    }
  } finally {
    tableLoading.value = false;
  }
};
// 翻译状态徽标（translation-workflow.md）：不做站点组的客户端预判（querySiteList 需 site:list 权限，
// 普通编辑可能没有）；status 接口按 article:list 放行（与列表同权）且未分组时服务端短路，空结果即不渲染
const i18nStatus = ref<Record<string, any>>({});
const fetchI18nStatus = async () => {
  if (data.value.length === 0) {
    i18nStatus.value = {};
    return;
  }
  const list = await queryArticleI18nStatus(data.value.map((row) => row.id));
  const map: Record<string, any> = {};
  list.forEach((item: any) => {
    if (item.linked) {
      map[item.id] = item;
    }
  });
  i18nStatus.value = map;
};
const fetchChannel = async () => {
  channelTreeLoading.value = true;
  try {
    channelTreeData.value = toTree(await queryChannelList({ isArticlePermission: true, isReal: true }));
    // 恢复出的栏目可能已删除、属于其它站点或已无权限（栏目树按文章权限过滤）：回落到全部栏目
    if (channelId.value != null && channel.value == null) {
      channelId.value = undefined;
    }
    nextTick(() => {
      if (channelTreeFilter.value != null) {
        channelTree.value?.filter(channelTreeFilter.value);
      }
      // setCurrentKey 会展开选中节点的各级父节点，无需另存展开状态
      channelTree.value?.setCurrentKey(channelId.value ?? null);
    });
  } finally {
    channelTreeLoading.value = false;
  }
};
const fetchBlockList = async () => {
  blockList.value = await queryBlockList();
};
// 专题筛选需 topic:list（专题管理组授予，文章管理组包亦带）；无权限时不请求、不显示筛选项，避免 403 弹窗。
// 专题为 T2 功能（2026-09-15）：perm() 对 * 恒有权、不是级别门禁，低于 T2 接口 404，同样不请求、不显示
const fetchTopicList = async () => {
  if (perm('topic:list') || currentUser.commercialTier < 2) {
    topicList.value = [];
    return;
  }
  topicList.value = await queryTopicList();
};
const fetchModelList = async () => {
  modelList.value = await queryModelList({ type: 'article' });
};
let sortable;
const initDragTable = () => {
  const tbody = document.querySelector('#dataTable .el-table__body-wrapper tbody');
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 200,
    chosenClass: 'sortable-chosen',
    onEnd: async function (event: any) {
      const { oldIndex, newIndex, item, from } = event;
      if (oldIndex === newIndex) {
        return;
      }
      try {
        await updateArticleOrder(data.value[oldIndex].id, data.value[newIndex].id);
      } catch {
        // 排序没有保存。Sortable 直接改了 DOM，Vue 并不知道：把拖动的行放回原位，再按服务端现状重载
        from.insertBefore(item, from.children[oldIndex < newIndex ? oldIndex : oldIndex + 1] ?? null);
        fetchData();
        return;
      }
      data.value.splice(newIndex, 0, data.value.splice(oldIndex, 1)[0]);
      ElMessage.success(t('success'));
    },
  });
};
onMounted(async () => {
  // 拖拽初始化须在任何 await 之前：它查询本视图的 DOM
  initDragTable();
  fetchBlockList();
  fetchTopicList();
  fetchModelList();
  fetchLangGroup();
  if (channelId.value != null) {
    // 有恢复出的栏目：先加载栏目树完成校验，再查列表，避免带着失效的栏目 ID 发请求
    try {
      await fetchChannel();
    } catch {
      // 请求拦截器已提示错误；列表照常加载
    }
  } else {
    fetchChannel();
  }
  refresh();
});
onBeforeUnmount(() => {
  if (sortable !== undefined) {
    sortable.destroy();
  }
});

const handleSort = ({ column, prop, order }: { column: any; prop: string | null; order: string | null }) => {
  if (prop && order) {
    sort.value = (column.sortBy ?? prop) + (order === 'descending' ? '_desc' : '');
  } else {
    sort.value = undefined;
  }
  fetchData();
};
const handleSearch = () => {
  currentPage.value = 1;
  refresh();
};
// 切换状态页签：条件没变，计数不必重取
const handleStatusChange = () => {
  currentPage.value = 1;
  fetchData();
};
const handleReset = () => {
  table.value.clearSort();
  resetParams(params.value);
  sort.value = undefined;
  currentPage.value = 1;
  refresh();
};
const treeRootClick = () => {
  channelTree.value.setCurrentKey(null);
  channelId.value = undefined;
  handleSearch();
};

const handleAdd = () => {
  action.value = 'add';
  beanId.value = undefined;
  formVisible.value = true;
};
const handleCopy = (id: string) => {
  action.value = 'copy';
  beanId.value = id;
  formVisible.value = true;
};
const handleEdit = (id: string) => {
  if (perm('article:update')) {
    return;
  }
  action.value = 'edit';
  beanId.value = id;
  formVisible.value = true;
};

// 仅被模板中注释保留的“站内推送”菜单项引用（特定客户需要时开启），故显式豁免 unused 检查
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const handleInternalPush = (id: string) => {
  beanId.value = id;
  pushType.value = 'internal';
  pushFormVisible.value = true;
};
const handleExternalPush = (id: string) => {
  beanId.value = id;
  pushType.value = 'external';
  pushFormVisible.value = true;
};
const handleWechatPush = (ids: string[]) => {
  wechatPushIds.value = ids;
  wechatPushVisible.value = true;
};

const recommendTo = (blockId: string, article: any) => {
  recommendVisible.value = true;
  recommendBlockId.value = blockId;
  recommendArticleId.value = article.id;
  recommendTitle.value = article.title;
  recommendDescription.value = article.description;
  recommendVideo.value = article.video;
  // 可选的图片：图片集与标题图。列表行不带正文，正文里的图片不在此列
  recommendImages.value = [...(article.imageList?.map((img: any) => img.url) ?? [])];
  if (article.image) {
    recommendImages.value.push(article.image);
  }
};
// 标签上的 × 一点就生效，太容易误触：先问一句
const handleBlockItemDelete = (row: any, item: any) =>
  confirmThen(t('article.confirm.removeBlock', { title: row.title, block: item.block.name }), t('article.block'), false, async () => {
    await deleteBlockItem([item.id]);
    fetchData();
    ElMessage.success(t('success'));
  });
const handleTask = (id: string) => {
  instanceId.value = id;
  taskListVisible.value = true;
};
const stickyVisible = ref<boolean>(false);
const stickyForm = ref<any>();
const stickyValues = ref<{ ids: string[]; sticky: number; stickyDate?: Date | null }>({ ids: [], sticky: 0, stickyDate: null });

// 单篇：沿用它现在的置顶值；批量：各篇的值不一，从 1 起填
const handleSticky = (ids: string[], sticky = 0, stickyDate?: Date | null) => {
  stickyVisible.value = true;
  stickyValues.value = { ids, sticky: sticky < 1 ? 1 : sticky, stickyDate };
};
const submitSticky = async () => {
  stickyForm.value.validate(async (valid: boolean) => {
    if (!valid) return;
    await stickyArticle(stickyValues.value.ids, stickyValues.value.sticky, stickyValues.value.stickyDate ?? undefined);
    fetchData();
    stickyVisible.value = false;
    ElMessage.success(t('success'));
  });
};
const cancelSticky = (row: any) =>
  confirmThen(t('article.confirm.cancelSticky', { title: row.title }), t('article.op.sticky'), false, async () => {
    await stickyArticle([row.id], 0, undefined);
    fetchData();
    ElMessage.success(t('success'));
  });

const moveVisible = ref<boolean>(false);
const moveIds = ref<string[]>([]);
const handleMove = (ids: string[]) => {
  moveIds.value = ids;
  moveVisible.value = true;
};
const selectedIds = computed<string[]>(() => selection.value.map((row: any) => row.id));
</script>

<template>
  <el-container>
    <collapsible-aside
      v-model:collapsed="asideCollapsed"
      v-model:keyword="channelTreeFilter"
      :search-placeholder="$t('channel.filter')"
      :root-label="$t('article.allChannel')"
      :root-active="channelId == null"
      @root="treeRootClick"
    >
      <el-tree
        ref="channelTree"
        v-loading="channelTreeLoading"
        :data="channelTreeData"
        :props="{ label: 'name' }"
        :expand-on-click-node="false"
        node-key="id"
        highlight-current
        :filter-node-method="filterTreeNodeByName"
        @node-click="
          (node: any) => {
            channelId = node.id;
            handleSearch();
          }
        "
      ></el-tree>
    </collapsible-aside>
    <el-main class="p-0">
      <div class="mb-3">
        <query-form :params="params" @search="handleSearch" @reset="handleReset">
          <query-item :label="$t('article.title')" name="Q_Contains_@articleExt-title"></query-item>
          <query-item label="ID" name="Q_EQ_id_Long" type="id"></query-item>
          <query-item :label="$t('article.slug')" name="Q_Contains_slug"></query-item>
          <query-item :label="$t('article.text')" name="Q_Contains_@articleExt-text"></query-item>
          <template v-for="field in fields.filter((item) => ['imageUpload', 'videoUpload', 'audioUpload', 'fileUpload'].indexOf(item.type) === -1)" :key="field.code">
            <query-item
              v-if="['number', 'slider'].indexOf(field.type) >= 0"
              :label="field.name"
              :name="`Q_GE_@articleExt-mainsJson$${field.code}_Int,Q_LE_@articleExt-mainsJson$${field.code}_Int`"
              type="number"
            ></query-item>
            <query-item
              v-else-if="field.type === 'date' && field.dateType === 'datetime'"
              :label="field.name"
              :name="`Q_GE_@articleExt-mainsJson$${field.code}_DateTime,Q_LE_@articleExt-mainsJson$${field.code}_DateTime`"
              type="datetime"
            ></query-item>
            <query-item
              v-else-if="field.type === 'date'"
              :label="field.name"
              :name="`Q_GE_@articleExt-mainsJson$${field.code}_Date,Q_LE_@articleExt-mainsJson$${field.code}_Date`"
              type="date"
            ></query-item>
            <query-item
              v-else-if="field.type === 'switch'"
              :label="field.name"
              :name="`Q_EQ_@articleExt-mainsJson$${field.code}_Boolean`"
              :options="[
                { label: $t('yes'), value: 'true' },
                { label: $t('no'), value: 'false' },
              ]"
              :multiple="false"
            ></query-item>
            <query-item
              v-else-if="['radio', 'select'].indexOf(field.type) >= 0"
              :label="field.name"
              :name="`Q_In_@articleExt-mainsJson$${field.code}Key${field.dataType === 'number' ? '_Int' : ''}`"
              :options="dictListMap[field.dictTypeId]"
            ></query-item>
            <query-item
              v-else-if="['checkbox', 'multipleSelect'].indexOf(field.type) >= 0"
              :label="field.name"
              :name="`Q_ArrayIn_@articleExt-mainsJson$${field.code}Key${field.dataType === 'number' ? '_Int' : ''}`"
              :options="dictListMap[field.dictTypeId]"
            ></query-item>
            <query-item v-else-if="field.clob" :label="field.name" :name="`Q_Contains_@articleExt-clobsJson$${field.code}`"></query-item>
            <query-item v-else :label="field.name" :name="`Q_Contains_@articleExt-mainsJson$${field.code}`"></query-item>
          </template>
          <query-item :label="$t('article.channel')" name="Q_Contains_channel-name"></query-item>
          <query-item
            v-if="currentUser.commercialTier >= 2 && !perm('topic:list')"
            :label="$t('article.topics')"
            name="Q_In_@ArticleTopic-topicId_Long"
            :options="topicList.map((item) => ({ label: item.name, value: item.id }))"
          ></query-item>
          <query-item :label="$t('article.tags')" name="Q_Contains_@ArticleTag-tag-name"></query-item>
          <query-item :label="$t('article.publishDate')" name="Q_GE_publishDate_DateTime,Q_LE_publishDate_DateTime" type="datetime"></query-item>
          <query-item :label="$t('article.sticky')" name="Q_GE_sticky_Short,Q_LE_sticky_Short">
            <el-select v-model="stickyFilter" clearable class="w-96">
              <el-option :label="$t('yes')" value="true"></el-option>
              <el-option :label="$t('no')" value="false"></el-option>
            </el-select>
          </query-item>
          <query-item
            :label="$t('article.block')"
            name="Q_In_@BlockItem-blockId_Long"
            :options="blockList.filter((item) => item.recommendable).map((item) => ({ label: item.name, value: item.id }))"
          ></query-item>
          <query-item :label="$t('article.type')" name="Q_In_type_Short" :options="[0, 1, 2, 3].map((item) => ({ label: $t(`article.type.${item}`), value: item }))"></query-item>
          <query-item
            :label="$t('article.withImage')"
            name="Q_EQ_withImage_Boolean"
            :options="[
              { label: $t('yes'), value: 'true' },
              { label: $t('no'), value: 'false' },
            ]"
            :multiple="false"
          ></query-item>
          <query-item :label="$t('article.linkUrl')" name="Q_Contains_@articleExt-linkUrl"></query-item>
          <query-item :label="$t('article.org')" name="Q_Contains_org-name"></query-item>
          <query-item :label="$t('article.user')" name="Q_Contains_user-username"></query-item>
          <query-item :label="$t('article.created')" name="Q_GE_created_DateTime,Q_LE_created_DateTime" type="datetime"></query-item>
          <query-item :label="$t('article.modifiedUser')" name="Q_Contains_modifiedUser@user-username"></query-item>
          <query-item :label="$t('article.modified')" name="Q_GE_modified_DateTime,Q_LE_modified_DateTime" type="datetime"></query-item>
          <query-item :label="$t('article.onlineDate')" name="Q_GE_onlineDate_DateTime,Q_LE_onlineDate_DateTime" type="datetime"></query-item>
          <query-item :label="$t('article.offlineDate')" name="Q_GE_offlineDate_DateTime,Q_LE_offlineDate_DateTime" type="datetime"></query-item>
          <query-item :label="$t('article.views')" name="Q_GE_@articleExt-views_Long,Q_LE_@articleExt-views_Long" type="number"></query-item>
          <query-item :label="$t('article.author')" name="Q_Contains_@articleExt-author"></query-item>
          <query-item :label="$t('article.editor')" name="Q_Contains_@articleExt-editor"></query-item>
          <query-item :label="$t('article.source')" name="Q_Contains_@articleExt-source"></query-item>
          <query-item :label="$t('article.subtitle')" name="Q_Contains_@articleExt-subtitle"></query-item>
          <query-item :label="$t('article.fullTitle')" name="Q_Contains_@articleExt-fullTitle"></query-item>
          <query-item :label="$t('article.seoKeywords')" name="Q_Contains_@articleExt-seoKeywords"></query-item>
          <query-item :label="$t('article.seoDescription')" name="Q_Contains_@articleExt-seoDescription"></query-item>
        </query-form>
      </div>
      <div>
        <el-button type="primary" :disabled="perm('article:create')" :icon="Plus" @click="() => handleAdd()">{{ $t('add') }}</el-button>
        <el-button :disabled="selection.length <= 0 || perm('article:delete')" :icon="Delete" @click="() => handleBatch('delete')">{{ $t('delete') }}</el-button>
        <el-dropdown class="ml-3" :disabled="selection.length <= 0">
          <el-button :disabled="selection.length <= 0">
            {{ $t('article.op.batch') }}<span v-if="selection.length > 0" class="ml-1">({{ selection.length }})</span><el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in batchStatusActions" :key="item" :disabled="perm(articleActionPerms[item])" @click="() => handleBatch(item)">
                {{ $t(articleActionLabels[item]) }}
              </el-dropdown-item>
              <el-dropdown-item :disabled="perm('article:sticky')" divided @click="() => handleSticky(selectedIds)">{{ $t('article.op.sticky') }}</el-dropdown-item>
              <el-dropdown-item :disabled="perm('article:update')" @click="() => handleMove(selectedIds)">{{ $t('article.op.move') }}</el-dropdown-item>
              <el-dropdown-item :disabled="perm('article:delete')" divided @click="() => handleBatch('restore')">{{ $t('article.op.restore') }}</el-dropdown-item>
              <el-dropdown-item :disabled="perm('article:completelyDelete')" @click="() => handleBatch('completelyDelete')">
                <span class="text-danger">{{ $t('article.op.completelyDelete') }}</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown class="ml-3">
          <el-button>
            {{ $t('article.op.importExport') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :disabled="perm('article:export')" @click="() => (exportVisible = true)">{{ $t('article.op.export') }}</el-dropdown-item>
              <el-dropdown-item v-if="isShowTier(1)" :disabled="currentUser.commercialTier >= 1 && perm('article:import')" @click="() => handleImport()">
                {{ $t('article.op.import') }}<commercial-badge v-if="currentUser.commercialTier < 1" :tier="1" :size="12" class="ml-1" />
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button
          v-if="inLangGroup && isShowTier(2)"
          :disabled="perm('article:translateBatch') || currentUser.commercialTier < 2"
          :title="lockedTip(2)"
          class="ml-3"
          @click="() => (translateBatchVisible = true)"
        >
          {{ $t('article.i18n.batchTranslate') }}<commercial-badge v-if="currentUser.commercialTier < 2" :tier="2" class="ml-1" />
        </el-button>
        <el-button
          v-if="isShowTier(3)"
          :disabled="perm('wechatArticle:push') || currentUser.commercialTier < 3 || wechatPushSelectionIds.length <= 0"
          :title="lockedTip(3) ?? $t('article.op.wechatPush.batchTip')"
          class="ml-3"
          @click="() => handleWechatPush(wechatPushSelectionIds)"
        >
          {{ $t('article.op.wechatPush') }}<commercial-badge v-if="currentUser.commercialTier < 3" :tier="3" class="ml-1" />
        </el-button>
        <column-setting name="article" class="ml-3" />
      </div>
      <div class="mt-3 app-block">
        <el-tabs v-model="status" class="block-tabs" @tab-change="() => handleStatusChange()">
          <el-tab-pane :name="-1" :label="$t('all')"></el-tab-pane>
          <el-tab-pane v-for="s in visibleStatuses" :key="s" :name="s">
            <template #label>
              {{ $t(`article.status.${s}`) }}<span v-if="statusCounts[s]" class="ml-1 text-xs text-gray-secondary">{{ statusCounts[s] }}</span>
            </template>
          </el-tab-pane>
        </el-tabs>
        <el-table
          id="dataTable"
          ref="table"
          v-loading="tableLoading"
          row-key="id"
          :data="data"
          @selection-change="(rows: any) => (selection = rows)"
          @row-dblclick="(row: any) => handleEdit(row.id)"
          @sort-change="handleSort"
        >
          <column-list name="article">
            <el-table-column type="selection" width="38"></el-table-column>
            <el-table-column width="42">
              <el-icon
                class="text-lg align-middle"
                :class="isSorted || perm('article:updateOrder') ? ['cursor-not-allowed', 'text-gray-disabled'] : ['cursor-move', 'text-gray-secondary', 'drag-handle']"
              >
                <Grid />
              </el-icon>
            </el-table-column>
            <el-table-column property="id" label="ID" width="200" sortable="custom" display="none"></el-table-column>
            <el-table-column property="title" :label="$t('article.title')" min-width="280" sort-by="@articleExt-title" sortable="custom">
              <template #default="{ row }">
                <!-- <el-button type="primary" link @click="() => openArticleLink(row.status, row.fullUrl, row.dynamicFullUrl)">{{ row.title }}</el-button> -->
                <el-link href="javascript:" underline="never" @click="() => openArticleLink(row.status, row.fullUrl, row.dynamicFullUrl)">{{ row.title }}</el-link>
                <el-tag
                  v-for="item in row.blockItemList"
                  :key="item.id"
                  :type="item.enabled ? undefined : 'info'"
                  size="small"
                  class="ml-1"
                  :closable="hasPermission('blockItem:delete')"
                  @close="() => handleBlockItemDelete(row, item)"
                >
                  {{ item.block.name }}
                </el-tag>
                <el-tooltip v-if="row.sticky > 0" placement="top">
                  <template #content>
                    {{ $t('article.sticky') }}
                    <span v-if="row.stickyDate != null">{{ dayjs(row.stickyDate).format(' YYYY-MM-DD HH:mm:ss') }}</span>
                  </template>
                  <el-tag type="danger" class="mx-1" effect="light" size="small" round :closable="!perm('article:sticky')" @close="() => cancelSticky(row)">
                    {{ row.sticky }}
                  </el-tag>
                </el-tooltip>
                <el-tooltip v-if="row.srcId != null" placement="top">
                  <template #content>
                    {{ t(`article.type.${row.type}`) }}{{ $t('punct.colon') }}
                    <span v-if="row.siteId !== row.src.siteId">[{{ row.src.site.name }}]</span>
                    {{ row.src.channel.paths.map((it: any) => it.name).join(' / ') }}
                  </template>
                  <el-icon class="ml-1 align-middle"><DocumentCopy /></el-icon>
                </el-tooltip>
                <el-tooltip v-if="i18nStatus[row.id]?.stale" :content="$t('article.i18n.stale.tooltip')" placement="top">
                  <el-tag type="warning" size="small" class="ml-1" disable-transitions>{{ $t('article.i18n.stale') }}</el-tag>
                </el-tooltip>
                <el-tooltip v-else-if="i18nStatus[row.id]?.linked" :content="$t('article.i18n.linked')" placement="top">
                  <el-icon class="ml-1 align-middle"><Connection /></el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column
              v-for="field in fields.filter((item) =>
                ['text', 'textarea', 'number', 'date', 'color', 'slider', 'switch', 'radio', 'checkbox', 'select', 'multipleSelect'].includes(item.type),
              )"
              :key="field.code"
              :property="`customs.${field.code}`"
              :label="field.name"
              :sort-by="`@articleExt-mainsJson$${field.code}${field.dataType === 'number' ? '_Int' : ''}`"
              :sortable="['checkbox', 'multipleSelect'].indexOf(field.type) === -1 ? 'custom' : undefined"
              :display="field.showInList ? undefined : 'none'"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <template v-if="field.type === 'date' && field.dateType === 'date'">
                  {{ dayjs(row.customs[field.code]).format('YYYY-MM-DD') }}
                </template>
                <template v-else-if="field.type === 'date' && field.dateType === 'datetime'">
                  {{ dayjs(row.customs[field.code]).format('YYYY-MM-DD HH:mm:ss') }}
                </template>
                <template v-else-if="field.type === 'switch'">
                  <el-tag :type="row.customs[field.code] ? 'success' : 'info'" size="small">{{ $t(row.customs[field.code] ? 'yes' : 'no') }}</el-tag>
                </template>
              </template>
            </el-table-column>
            <el-table-column property="channel.name" :label="$t('article.channel')" sort-by="channel-name" sortable="custom" show-overflow-tooltip>
              <template #default="{ row }">
                <span :title="row.channel.paths.map((it: any) => it.name).join(' / ')">{{ row.channel.name }}</span>
              </template>
            </el-table-column>
            <el-table-column property="org.name" :label="$t('article.org')" sort-by="org-name" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="author" :label="$t('article.author')" sort-by="@articleExt-author" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="editor" :label="$t('article.editor')" sort-by="@articleExt-editor" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="source" :label="$t('article.source')" sort-by="@articleExt-source" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="user.username" :label="$t('article.user')" sort-by="user-username" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="created" :label="$t('article.created')" min-width="120" sortable="custom" display="none" show-overflow-tooltip>
              <template #default="{ row }">{{ dayjs(row.created).format('YYYY-MM-DD HH:mm') }}</template>
            </el-table-column>
            <el-table-column
              property="modifiedUser.username"
              :label="$t('article.modifiedUser')"
              sort-by="modifiedUser@user-username"
              sortable="custom"
              display="none"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column property="modified" :label="$t('article.modified')" min-width="120" sortable="custom" display="none" show-overflow-tooltip>
              <template #default="{ row }">{{ dayjs(row.modified).format('YYYY-MM-DD HH:mm') }}</template>
            </el-table-column>
            <el-table-column property="publishDate" :label="$t('article.publishDate')" min-width="120" sortable="custom" show-overflow-tooltip>
              <template #default="{ row }">{{ dayjs(row.publishDate).format('YYYY-MM-DD HH:mm') }}</template>
            </el-table-column>
            <el-table-column property="views" :label="$t('article.views')" sort-by="@articleExt-views" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column
              property="dayViews"
              :label="$t('article.dayViews')"
              sort-by="@articleExt-dayViews"
              sortable="custom"
              display="none"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column
              property="weekViews"
              :label="$t('article.weekViews')"
              sort-by="@articleExt-weekViews"
              sortable="custom"
              display="none"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column
              property="monthViews"
              :label="$t('article.monthViews')"
              sort-by="@articleExt-monthViews"
              sortable="custom"
              display="none"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column
              property="quarterViews"
              :label="$t('article.quarterViews')"
              sort-by="@articleExt-quarterViews"
              sortable="custom"
              display="none"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column
              property="yearViews"
              :label="$t('article.yearViews')"
              sort-by="@articleExt-yearViews"
              sortable="custom"
              display="none"
              show-overflow-tooltip
            ></el-table-column>
            <el-table-column property="ups" :label="$t('article.ups')" sort-by="@articleExt-ups" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="downs" :label="$t('article.downs')" sort-by="@articleExt-downs" sortable="custom" display="none" show-overflow-tooltip></el-table-column>
            <el-table-column property="status" :label="$t('article.status')" sortable="custom" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tooltip v-if="row.status === 22 && row.rejectReason" :content="row.rejectReason" placement="top">
                  <el-tag :type="articleStatusTagType(row.status)" size="small">{{ $t(`article.status.${row.status}`) }}</el-tag>
                </el-tooltip>
                <el-tag v-else :type="articleStatusTagType(row.status)" size="small">{{ $t(`article.status.${row.status}`) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('table.action')" width="230">
              <template #default="{ row }">
                <el-button type="primary" :disabled="perm('article:update')" size="small" link @click="() => handleEdit(row.id)">{{ $t('edit') }}</el-button>
                <!-- 行内只给一个状态操作，随状态而变：回收站里是恢复，没上线的是提交，在线上的是下线。其余收在“更多”里 -->
                <el-button v-if="primaryStatusAction(row) === 'restore'" type="primary" :disabled="perm('article:delete')" size="small" link @click="() => handleExecute('restore', [row.id])">
                  {{ $t('article.op.restore') }}
                </el-button>
                <el-popconfirm v-else-if="primaryStatusAction(row) === 'submit'" :title="$t('confirmExecute')" @confirm="() => handleExecute('submit', [row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('article:submit')" size="small" link>{{ $t('article.op.submit') }}</el-button>
                  </template>
                </el-popconfirm>
                <el-popconfirm v-else-if="primaryStatusAction(row) === 'offline'" :title="$t('confirmExecute')" @confirm="() => handleExecute('offline', [row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('article:offline')" size="small" link>{{ $t('article.op.offline') }}</el-button>
                  </template>
                </el-popconfirm>
                <!-- 彻底删除只在回收站里作为行按钮出现；其它状态下收在“更多”的末尾（D1） -->
                <el-popconfirm v-if="row.status === 20" :title="$t('confirmCompletelyDelete')" @confirm="() => handleExecute('completelyDelete', [row.id])">
                  <template #reference>
                    <el-button type="danger" :disabled="perm('article:completelyDelete')" size="small" link>{{ $t('completelyDelete') }}</el-button>
                  </template>
                </el-popconfirm>
                <el-popconfirm v-else :title="$t('confirmDelete')" @confirm="() => handleExecute('delete', [row.id])">
                  <template #reference>
                    <el-button type="primary" :disabled="perm('article:delete')" size="small" link>{{ $t('delete') }}</el-button>
                  </template>
                </el-popconfirm>
                <el-dropdown class="ml-2 align-middle">
                  <el-button type="primary" size="small" link>
                    <el-icon class="text-primary"><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <div>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="canSubmit(row) && primaryStatusAction(row) !== 'submit'" :disabled="perm('article:submit')" @click="() => handleRowAction('submit', row)">
                          <span class="text-xs">{{ $t('article.op.submit') }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canArchive(row)" :disabled="perm('article:archive')" @click="() => handleRowAction('archive', row)">
                          <span class="text-xs">{{ $t('article.op.archive') }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canOffline(row) && primaryStatusAction(row) !== 'offline'" :disabled="perm('article:offline')" @click="() => handleRowAction('offline', row)">
                          <span class="text-xs">{{ $t('article.op.offline') }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="row.processInstanceId && currentUser.commercialTier >= 1"
                          :disabled="perm('processInstance:task')"
                          @click="() => handleTask(row.processInstanceId)"
                        >
                          <span class="text-xs">{{ $t('processInstance.op.task') }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item :disabled="perm('article:create')" divided @click="() => handleCopy(row.id)">
                          <span class="text-xs">{{ $t('clone') }}</span>
                        </el-dropdown-item>
                        <!-- 该功能会增加复杂性，但保留代码。特定客户需要时，可开启 -->
                        <!--
                        <el-dropdown-item
                          v-if="isShowTier(2)"
                          :disabled="perm('article:internalPush') || row.status !== 0 || row.srcId != null || currentUser.commercialTier < 2"
                          :title="lockedTip(2)"
                          @click="() => handleInternalPush(row.id)"
                        >
                          <span class="text-xs">{{ $t('article.op.internalPush') }}</span>
                        </el-dropdown-item>
                        -->
                        <el-dropdown-item
                          v-if="isShowTier(3)"
                          :disabled="perm('article:externalPush') || row.status !== 0 || row.srcId != null || currentUser.commercialTier < 3"
                          :title="lockedTip(3)"
                          @click="() => handleExternalPush(row.id)"
                        >
                          <span class="text-xs">{{ $t('article.op.externalPush') }}</span>
                          <commercial-badge v-if="currentUser.commercialTier < 3" :tier="3" :size="12" class="ml-1" />
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="isShowTier(3)"
                          :disabled="perm('wechatArticle:push') || row.status !== 0 || currentUser.commercialTier < 3"
                          :title="lockedTip(3)"
                          @click="() => handleWechatPush([row.id])"
                        >
                          <span class="text-xs">{{ $t('article.op.wechatPush') }}</span>
                          <commercial-badge v-if="currentUser.commercialTier < 3" :tier="3" :size="12" class="ml-1" />
                        </el-dropdown-item>
                        <el-dropdown-item :disabled="perm('article:sticky')" divided @click="() => handleSticky([row.id], row.sticky, row.stickyDate)">
                          <span class="text-xs">{{ $t('article.op.sticky') }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item :disabled="perm('article:update')" @click="() => handleMove([row.id])">
                          <span class="text-xs">{{ $t('article.op.move') }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-for="(item, index) in blockList.filter((it) => it.enabled && it.recommendable)"
                          :key="item.id"
                          :disabled="perm('blockItem:create') || row.blocks.findIndex((block: any) => block.id === item.id) >= 0"
                          :divided="index === 0"
                          @click="() => recommendTo(item.id, row)"
                        >
                          <span class="text-xs">{{ $t('article.op.recommendTo') }}{{ $t('punct.colon') }}{{ item.name }}</span>
                        </el-dropdown-item>
                        <el-dropdown-item v-if="row.status !== 20" :disabled="perm('article:completelyDelete')" divided @click="() => handleRowAction('completelyDelete', row)">
                          <span class="text-xs text-danger">{{ $t('article.op.completelyDelete') }}</span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </div>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </column-list>
        </el-table>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="pageSizes"
          :layout="pageLayout"
          size="small"
          background
          class="justify-end px-3 py-2"
          @size-change="() => fetchData()"
          @current-change="() => fetchData()"
        ></el-pagination>
      </div>

      <article-form v-model="formVisible" :action="action" :bean-id="beanId" :bean-ids="beanIds" :channel="channel" @finished="refresh" />
      <article-translate-batch v-model="translateBatchVisible" @finished="refresh" />
      <article-push-form v-model="pushFormVisible" :bean-id="beanId" :type="pushType" @finished="refresh" />
      <article-move-form v-model="moveVisible" :ids="moveIds" @finished="refresh" />
      <article-export-dialog v-model="exportVisible" :params="appliedQuery" :total="total" :selected-ids="selectedIds" :fields="fields" />
      <article-import-dialog v-model="importVisible" :channel-id="channelId" @finished="refresh" />
      <wechat-push-form v-model="wechatPushVisible" :bean-ids="wechatPushIds" @finished="fetchData" />
      <block-item-form
        v-model="recommendVisible"
        :bean-id="undefined"
        :bean-ids="[]"
        :block-id="recommendBlockId"
        :article-id="recommendArticleId"
        :title="recommendTitle"
        :description="recommendDescription"
        :video="recommendVideo"
        :images="recommendImages"
        @finished="fetchData"
      />
      <process-task-list v-model="taskListVisible" :instance-id="instanceId" />
      <el-dialog v-model="stickyVisible" :title="$t('article.sticky')" :width="768">
        <el-form ref="stickyForm" :model="stickyValues" label-width="150px">
          <div v-if="stickyValues.ids.length > 1" class="mb-3 text-sm text-gray-secondary">{{ $t('article.selectedCount', { count: stickyValues.ids.length }) }}</div>
          <el-form-item prop="sticky">
            <template #label><label-tip message="article.sticky" help /></template>
            <el-input-number v-model="stickyValues.sticky" :min="0" :max="999" />
          </el-form-item>
          <el-form-item
            prop="stickyDate"
            :rules="{
              validator: (rule, value, callback) => {
                if (value != null && Date.now() > new Date(value).getTime()) {
                  callback($t('article.error.stickyDate.beforeCurrent'));
                  return;
                }
                callback();
              },
            }"
          >
            <template #label><label-tip message="article.stickyDate" help /></template>
            <el-date-picker v-model="stickyValues.stickyDate" type="datetime" />
          </el-form-item>
          <el-button type="primary" @click="() => submitSticky()">{{ $t('submit') }}</el-button>
        </el-form>
      </el-dialog>
    </el-main>
  </el-container>
</template>
<style>
.sortable-chosen td {
  @apply border-t-2 border-t-warning-light;
}
</style>

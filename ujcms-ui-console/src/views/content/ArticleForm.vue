<script setup lang="ts">
import { computed, defineAsyncComponent, ref, toRefs, watch, onMounted, onBeforeUnmount, type PropType } from 'vue';
import { Finished, Box, DocumentRemove, Delete, CircleCheck, CircleClose, View, Back, SetUp, Compass, RefreshLeft } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import * as htmlparser2 from 'htmlparser2';
import * as domutils from 'domutils';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { perm, currentUser } from '@/stores/useCurrentUser';
import {
  queryArticle,
  queryArticleReview,
  createArticle,
  updateArticle,
  passArticle,
  submitArticle,
  rejectArticles,
  archiveArticle,
  offlineArticle,
  deleteArticle,
  restoreArticle,
  completelyDeleteArticle,
  rejectArticle,
  delegateArticle,
  transferArticle,
  backArticle,
  queryChannelList,
  queryTopicList,
  queryArticleTemplates,
  queryArticleTitleSimilarity,
  articleSlugExist,
  articleSlugSuggest,
  checkArticleWords,
} from '@/api/content';
import { queryModelList, queryDictListByAlias } from '@/api/config';
import { aiScreen, aiArticleMeta, aiArticleTitles, aiArticleSlugs, updateAiLogAccepted } from '@/api/ai';
import { queryTaskFormProperties } from '@/api/system';
import { jodConvertDocUrl, jodConvertLibraryUrl, queryDocImportCapabilities, queryTagList } from '@/api/content';
import type { DocImportCapabilities, DocImportResult } from '@/api/content';
import { queryOrgList, queryOrgPermissions } from '@/api/user';
import { toTree, disableTreeWithPermission } from '@/utils/tree';
import { formatDuration } from '@/utils/common';
import { isMac, tinymceDialogOpen } from '@/utils/keyboard';
import { promptRejectReason } from '@/utils/review';
import { editorDraftKey, removeEditorDraft } from '@/components/Tinymce/draft';
import { showCommercialNotice } from '@/utils/commercial';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import FieldItem from '@/views/config/components/FieldItem.vue';
import DialogForm from '@/components/DialogForm.vue';
import Tinymce from '@/components/Tinymce';
import LabelTip from '@/components/LabelTip.vue';
import TemplateSelect from './TemplateSelect.vue';
import { BaseUpload, ImageUpload, ImageListUpload, FileListUpload } from '@/components/Upload';
import ImageExtractor from './components/ImageExtractor.vue';
import ArticleI18nPanel from './components/ArticleI18nPanel.vue';
import ArticleRelatedPanel from './components/ArticleRelatedPanel.vue';
import { openArticleLink, articleStatusTagType } from './components/articleUtils';
import { canSubmit, canReject, canArchive, canOffline, canRestore } from './components/articleActions';
import ReviewFormProperties from './components/ReviewFormProperties.vue';
import ReviewDelegate from './components/ReviewDelegate.vue';
import ReviewTransfer from './components/ReviewTransfer.vue';
import ReviewBack from './components/ReviewBack.vue';

// Markdown 编辑器（bytemd 及其 remark/rehype 管线，约 650 kB）仅在正文选用 Markdown 编辑器类型时才需要，异步加载以免随表单一并下载。
const MarkdownEditor = defineAsyncComponent(() => import('@/components/MarkdownEditor/MarkdownEditor.vue'));

defineOptions({
  name: 'ArticleForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  action: { type: String as PropType<'add' | 'copy' | 'edit'>, default: 'edit' },
  channel: { type: Object, default: null },
  isReview: { type: Boolean, default: false },
});
defineEmits({ 'update:modelValue': null, finished: null });

const { modelValue: visible, channel } = toRefs(props);
const { t } = useI18n();
const sysConfig = useSysConfigStore();
const currentSiteStore = useCurrentSiteStore();
const dialog = ref<any>();
const showSubtitle = ref<boolean>(false);
const showFullTitle = ref<boolean>(false);
const showLinkUrl = ref<boolean>(false);
const focus = ref<any>();
const values = ref<any>({});
const tinymce = ref<any>();
// 当前安装能导入什么（.docx/.doc/.wps 内置引擎总能读；office 为 LibreOffice 是否可用）
const docImport = ref<DocImportCapabilities>({ docx: false, doc: false, office: false, extensions: [] });
const docImportAccept = computed(() => docImport.value.extensions.map((ext) => `.${ext}`).join(','));
const orgList = ref<any[]>([]);
const orgIds = ref<string[]>([]);
const channelList = ref<any[]>([]);
const flatChannelList = ref<any[]>([]);
const articleModelList = ref<any[]>([]);
const articleTemplates = ref<any[]>([]);
const articleModelId = ref<string>();
const articleModel = computed(() => articleModelList.value.find((item) => item.id === articleModelId.value) ?? articleModelList.value[0]);
const mains = computed(() => arr2obj(mergeModelFields(getModelData().article.mains, articleModel.value?.mains, 'article')));
const asides = computed(() => arr2obj(mergeModelFields(getModelData().article.asides, articleModel.value?.asides, 'article')));
const fields = computed(() => JSON.parse(articleModel.value?.customs || '[]').filter((item: any) => item.type !== 'richEditor'));
const editorFields = computed(() => JSON.parse(articleModel.value?.customs || '[]').filter((item: any) => item.type === 'richEditor'));

const tagLoading = ref<boolean>(false);
const tagList = ref<any[]>([]);
const tagRemoteMethod = async (query?: string) => {
  // 只有“文章审核”权限的审核人没有 TAG 列表的权限：不请求，没有候选项而已
  if (perm('tag:list')) {
    tagList.value = [];
    return;
  }
  tagLoading.value = true;
  tagList.value = await queryTagList({ Q_Contains_name: query, Q_OrderBy: 'refers_desc,id_desc', limit: 20 });
  tagLoading.value = false;
};

onMounted(() => {
  // 提前获取，否则新增文章时 ${mains['text'].editorType} 无法取到值
  fetchArticleModeList();
});

watch(visible, () => {
  if (visible.value) {
    articleModelId.value = channel?.value?.articleModelId;
    fetchOrgList();
    fetchChannelList();
    fetchTopicList();
    fetchArticleTemplates();
    fetchDocImportCapabilities();
  }
});
// 文章模型变了（换栏目、切到另一模型的文章、模型列表迟到）：给新模型的自定义字段补默认值。这不是用户的改动——
// 表单与基线一起补（dialog.patch），否则一打开就显示“未保存”
watch(fields, () => {
  dialog.value?.patch((target: any) => {
    if (target?.customs != null) {
      initCustoms(target.customs);
    }
  });
});
// 复制出来的新文章尚未保存；保存并留下（存为草稿、Ctrl+S）之后它就是一篇已有的文章，按编辑处理
const isCopy = computed(() => props.action === 'copy' && !dialog.value?.isEdit);
const fetchDocImportCapabilities = async () => {
  if (currentUser.commercialTier > 0) {
    docImport.value = await queryDocImportCapabilities();
  }
};
const fetchSourceList = (name: string) => queryDictListByAlias('sys_article_source', name);
const fetchOrgList = async () => {
  // 没有组织列表的权限（只有“文章审核”权限的审核人）：不请求。组织一栏仍显示文章现在的组织（见模板里的 bean.org）
  if (perm('org:list')) {
    orgIds.value = [];
    orgList.value = [];
    return;
  }
  const flatOrgList = await queryOrgList({ current: true });
  orgIds.value = flatOrgList.map((item: any) => item.id);
  orgList.value = toTree(flatOrgList);
  if (currentUser.dataScope !== 1) {
    disableTreeWithPermission(orgList.value, await queryOrgPermissions());
  }
};
const fetchChannelList = async () => {
  flatChannelList.value = await queryChannelList({ isArticlePermission: true, isReal: true });
  channelList.value = toTree(flatChannelList.value);
};
// 复制文章：扩展栏目只带走操作员文章权限之内的。源文章上可能有权限更宽的同事加的扩展栏目，新文章的每个扩展栏目都算新加，
// 原样带过去会被服务端整篇拒绝（docs/spec/article-data-permission/plan.md D3）。栏目列表与文章各自异步到达，两边变化时都检查
watch([flatChannelList, () => values.value?.refersChannelIds], () => {
  if (!isCopy.value || flatChannelList.value.length <= 0) {
    return;
  }
  const refers: string[] = values.value?.refersChannelIds ?? [];
  const allowed = refers.filter((id) => flatChannelList.value.some((item) => item.id === id));
  if (allowed.length !== refers.length) {
    values.value.refersChannelIds = allowed;
  }
});
const topicList = ref<any[]>([]);
// 专题字段需 topic:list；无权限时不请求、不显示字段（topicIds 原样回传，不会剥离已关联专题）。
// 专题为 T2 功能（2026-09-15）：perm() 对 * 恒有权、不是级别门禁，低于 T2 接口 404，同样不请求、不显示
const fetchTopicList = async () => {
  if (perm('topic:list') || currentUser.commercialTier < 2) {
    topicList.value = [];
    return;
  }
  topicList.value = toTree(await queryTopicList());
};
const fetchArticleModeList = async () => {
  articleModelList.value = await queryModelList({ type: 'article' });
};
const fetchArticleTemplates = async () => {
  articleTemplates.value = await queryArticleTemplates();
};
const fieldsOf = (modelId?: string): any[] => {
  const model = articleModelList.value.find((item) => item.id === modelId) ?? articleModelList.value[0];
  return JSON.parse(model?.customs || '[]').filter((item: any) => item.type !== 'richEditor');
};
const initCustoms = (customs: any, fieldList: any[] = fields.value) => {
  fieldList.forEach((field: any) => {
    if (customs[field.code] == null) {
      customs[field.code] = field.defaultValue;
      if (field.defaultValueKey != null) {
        customs[field.code + 'Key'] = field.defaultValueKey;
      }
    }
  });
  return customs;
};

// 保存前的词库检查（docs/spec/word-dictionaries/plan.md Phase 5）：一次列出标题、摘要、正文等字段的全部命中。
// 敏感词须改掉才能保存；易错词可逐处修正、全部修正，也可忽略并保存。结果抽屉与 AI 筛查共用，见下方“筛查”一节
let saveChecking = false;
let saveCheckResolve: ((prevent: boolean) => void) | undefined;
const preventSubmit = async (): Promise<boolean> => {
  if (currentUser.commercialTier < 2) {
    return false;
  }
  // 上一次检查尚未结束（Ctrl+S 连按，或结果抽屉还开着）：不再发起第二次提交
  if (saveChecking) {
    return true;
  }
  saveChecking = true;
  try {
    let findings: any[];
    try {
      findings = (await checkArticleWords(wordCheckPayload())).findings ?? [];
    } catch {
      // 检查接口失败不拦保存：敏感词在服务端保存时仍会拦截
      return false;
    }
    if (findings.length === 0) {
      return false;
    }
    aiScreenFindings.value = findings;
    aiScreenSemanticError.value = null;
    aiScreenMode.value = 'save';
    aiScreenVisible.value = true;
    // 提交在此等待抽屉的结果：返回修改 → 拦下；忽略并保存 / 全部修正完 → 同一次提交继续（哪个保存按钮发起的就继续哪个）
    return await new Promise<boolean>((resolve) => {
      saveCheckResolve = resolve;
    });
  } finally {
    saveChecking = false;
  }
};
const finishSaveCheck = (prevent: boolean) => {
  const resolve = saveCheckResolve;
  saveCheckResolve = undefined;
  aiScreenVisible.value = false;
  resolve?.(prevent);
};
// 抽屉被直接关掉（遮罩、Esc、关闭按钮）等同返回修改
const handleScreenClosed = () => {
  if (saveCheckResolve) {
    finishSaveCheck(true);
  }
};

// 仅由空白构成（空段落 / 换行 / 空格 / &nbsp;）的正文视为无内容，避免对空文章弹出多余确认。
// 含图片、音视频、表格等非文本元素时不算空白，仍需提醒。
const isBlankHtml = (html?: string): boolean => !html || /^(\s|&nbsp;|<p>|<\/p>|<br\s*\/?>)*$/i.test(html);

// 切换编辑器类型。富文本(1) → Markdown(2) 经 Turndown 有损转换，颜色/字号/对齐/自定义样式
// 及嵌入音视频等富文本专有格式会丢失，且 MarkdownEditor 挂载时会立即用转换结果覆盖原始 HTML，
// 故正文非空时先确认；Markdown(2) → 富文本(1) 保留已渲染 HTML、仅丢弃 markdown 源，无需确认。
// 用 :model-value + @change（而非 v-model）控制提交时机：确认前不改 editorType，
// 避免 MarkdownEditor 在用户确认前挂载并触发 onMounted 的有损转换。
const onEditorTypeChange = async (next: any) => {
  const prev = values.value.editorType;
  if (next === prev) {
    return;
  }
  if (prev === 1 && next === 2 && !isBlankHtml(values.value.text)) {
    try {
      await ElMessageBox.confirm(t('article.editorType.switch.confirm.message'), t('article.editorType.switch.confirm.title'), {
        type: 'warning',
        confirmButtonText: t('ok'),
        cancelButtonText: t('cancel'),
      });
    } catch {
      // 用户取消，保持当前编辑器
      return;
    }
  }
  values.value.markdown = '';
  values.value.editorType = next;
};

// 新文章的两个出口，意图各自写死，不看表单里带着的 status（复制来的表单带着源文章的状态）：
// “提交”一定是发布（或进入审核），“存为草稿”一定是草稿（docs/spec/article-management/plan.md D5）
const createAndPublish = (data: any) => createArticle({ ...data, status: 0 });
const createAsDraft = (data: any) => createArticle({ ...data, status: 10 });
// 存为草稿：保存后留在对话框里接着写——新文章就此变成一篇草稿，之后的保存都是“修改”
const handleSaveAsDraft = () => dialog.value.defaultSubmit(true, { create: createAsDraft, message: t('article.success.savedAsDraft') });
// 保存并提交：先保存表单，再提交。保存不成功就不提交
const handleSaveAndSubmit = () => {
  dialog.value.submit(
    async (data: any, { markSaved, loadBean, continuous }: { markSaved: () => void; loadBean: () => Promise<any>; continuous: boolean }) => {
      await updateArticle(data);
      markSaved();
      await submitArticle([data.id]);
      ElMessage.success(t('success'));
      // 连续模式下对话框不关：按新状态重新载入；否则马上就关了，不必再读一次
      if (continuous) {
        loadBean();
      }
    },
  );
};

// DOC导入：正文里已有内容时先问一句——替换，还是追加到末尾。直接关掉确认框则不导入。
// 导入后：文档属性里有标题、文章还没有标题时问一句要不要用；导入器跳过了什么（图表、公式……）提示一行
const handleDocImported = async (res: DocImportResult) => {
  const html = res.html ?? '';
  if (isBlankHtml(values.value.text)) {
    values.value.text = html;
  } else {
    try {
      await ElMessageBox.confirm(t('article.docImport.confirm'), t('article.op.docImport'), {
        type: 'warning',
        distinguishCancelAndClose: true,
        confirmButtonText: t('article.docImport.replace'),
        cancelButtonText: t('article.docImport.append'),
      });
      values.value.text = html;
    } catch (reason) {
      if (reason !== 'cancel') {
        return;
      }
      values.value.text = (values.value.text ?? '') + html;
    }
  }
  if (res.title && !values.value.title) {
    try {
      await ElMessageBox.confirm(t('article.docImport.fillTitle', { title: res.title }), t('article.op.docImport'), {
        type: 'info',
        confirmButtonText: t('ok'),
        cancelButtonText: t('cancel'),
      });
      values.value.title = res.title;
    } catch {
      // 不用文档标题
    }
  }
  if (res.warnings?.length) {
    const items = res.warnings.map((w) => t(`article.docImport.warning.${w.code}`, { count: w.count })).join(t('punct.enum'));
    ElMessage.warning(t('article.docImport.warnings', { items }));
  }
};

const taskId = ref<string>('');
const formProperties = ref<any[]>([]);
const formPropertiesVisible = ref<boolean>(false);
const backVisible = ref<boolean>(false);
const delegateVisible = ref<boolean>(false);
const transferVisible = ref<boolean>(false);

// 保存快捷键 Ctrl+S / ⌘S：只保存，从不发布（D5）。新文章存为草稿并留下；已有文章就地保存。
// 快捷键提示写在按钮上
const saveHint = isMac ? '⌘S' : 'Ctrl+S';
const isSaveShortcut = (event: KeyboardEvent): boolean =>
  typeof event.key === 'string' && event.key.toLowerCase() === 's' && (event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey;
const handleSaveShortcut = (event: KeyboardEvent) => {
  if (!isSaveShortcut(event)) {
    return;
  }
  // 不管保不保存，都拦下浏览器自己的“保存网页”
  event.preventDefault();
  event.stopPropagation();
  // 按住不放的重复按键不算；只读（详情、没有修改权限的审核人）时不保存。请求未返回时再按，由 DialogForm 挡掉
  if (event.repeat || dialog.value == null || dialog.value.disabled) {
    return;
  }
  if (dialog.value.isEdit) {
    if (!perm('article:update')) {
      dialog.value.defaultSubmit(true);
    }
  } else if (!perm('article:create')) {
    handleSaveAsDraft();
  }
};
// 焦点不在正文编辑器里时（标题、摘要、右侧设置）同样有效。富文本在 iframe 里，它的按键由编辑器组件转出来；
// 其余的在这里接。文章对话框之上还开着别的弹层（选择器、筛查抽屉、确认框、编辑器自己的对话框）时不处理
const openOverlays = (): number => [...document.querySelectorAll<HTMLElement>('.el-overlay')].filter((el) => el.style.display !== 'none').length;
const handleDocumentKeydown = (event: KeyboardEvent) => {
  if (!visible.value || !isSaveShortcut(event)) {
    return;
  }
  if (tinymceDialogOpen() || openOverlays() > 1) {
    event.preventDefault();
    return;
  }
  handleSaveShortcut(event);
};
watch(visible, () => {
  if (visible.value) {
    document.addEventListener('keydown', handleDocumentKeydown);
  } else {
    document.removeEventListener('keydown', handleDocumentKeydown);
  }
});
onBeforeUnmount(() => document.removeEventListener('keydown', handleDocumentKeydown));

// 富文本的本地草稿（TinyMCE autosave）：按“用户 + 站点 + 文章”存放，刷新、崩溃之后在同一篇文章里用工具栏的
// “恢复草稿”取回。保存成功、确认放弃修改之后清除
const draftKey = computed(() =>
  editorDraftKey(currentUser.username, currentSiteStore.currentSite?.id, 'article', dialog.value?.isEdit ? values.value?.id : undefined),
);
const clearDraft = () => {
  removeEditorDraft(draftKey.value);
  removeEditorDraft(editorDraftKey(currentUser.username, currentSiteStore.currentSite?.id, 'article', undefined));
};

const handlePass = async (bean: any) => {
  taskId.value = bean.taskId;
  formProperties.value = await queryTaskFormProperties(bean.taskId);
  formPropertiesVisible.value = true;
};
const submitPass = async (params: any) => {
  dialog.value.remove(async () => {
    await passArticle(taskId.value, params.properties, params.comment);
  });
};

const handleDelegate = async (bean: any) => {
  taskId.value = bean.taskId;
  delegateVisible.value = true;
};
const submitDelegate = async (params: any) => {
  dialog.value.remove(async () => {
    await delegateArticle(taskId.value, params.toUserId, params.comment);
  });
};
const handleTransfer = async (bean: any) => {
  taskId.value = bean.taskId;
  transferVisible.value = true;
};
const submitTransfer = async (params: any) => {
  dialog.value.remove(async () => {
    await transferArticle(taskId.value, params.toUserId, params.comment);
  });
};
const handleBack = (bean: any) => {
  taskId.value = bean.taskId;
  backVisible.value = true;
};
const submitBack = (params: any) => {
  dialog.value.remove(async () => {
    await backArticle(taskId.value, params.activityId, params.comment);
  });
};
const handleReject = async (bean: any) => {
  const reason = await promptRejectReason(t);
  if (reason == null) {
    return;
  }
  dialog.value.remove(async () => {
    await rejectArticle([bean.taskId], reason);
  });
};
// 文章管理里的退回：待审核且未进入审批流程的文章（会员投稿，docs/spec/member-contribute/plan.md D6），不经流程任务
const handleRejectDirect = async (bean: any) => {
  const reason = await promptRejectReason(t);
  if (reason == null) {
    return;
  }
  dialog.value.remove(async () => {
    await rejectArticles([bean.id], reason);
  });
};

// 状态操作不保存表单内容：跳过保存前的词库检查与表单校验，否则含词库词、或者填了一半的文章连删除都被拦下
const handleExecute = (action: string, id: string) => {
  const executor = async (values: any, { loadBean, continuous }: { loadBean: () => Promise<any>; continuous: boolean }) => {
    if (action === 'delete') {
      await deleteArticle([id]);
    } else if (action === 'submit') {
      await submitArticle([id]);
    } else if (action === 'archive') {
      await archiveArticle([id]);
    } else if (action === 'offline') {
      await offlineArticle([id]);
    } else if (action === 'restore') {
      await restoreArticle([id]);
    } else {
      throw new Error(`not support action: ${action}`);
    }
    // 连续模式下对话框不关：按新状态重新载入；否则马上就关了，不必再读一次
    if (continuous) {
      loadBean();
    }
  };
  dialog.value.submit(executor, { skipPreventSubmit: true, skipValidate: true });
};

const imageExtractorVisible = ref<boolean>(false);
const imageExtractorUrls = ref<string[]>([]);
const extractImage = () => {
  imageExtractorVisible.value = true;
  const dom = htmlparser2.parseDocument(values.value.text);
  const imgs = domutils.getElementsByTagName('img', dom);
  const urls = imgs.map((img: any) => domutils.getAttributeValue(img, 'src')).filter((src: string | undefined) => src?.startsWith(sysConfig.base.uploadUrlPrefix)) as string[];
  imageExtractorUrls.value = [...(values.value.imageList?.map((img: any) => img.url) ?? []), ...urls];
};

const titleSimilarityVisible = ref<boolean>(false);
const titleSimilarityList = ref<any[]>([]);
const titleSimilarity = async (title: string, excludeId?: string) => {
  titleSimilarityList.value = await queryArticleTitleSimilarity(0.6, title, excludeId);
  if (titleSimilarityList.value.length > 0) {
    titleSimilarityVisible.value = true;
  } else {
    ElMessage.success(t('article.success.titleSimilarity'));
  }
};

// 筛查（docs/spec/ai/screening.md §6、docs/spec/word-dictionaries/plan.md Phase 5）。结果抽屉由两处打开：
// 'ai' = AI 筛查按钮（词库 + 语义，仅正文；词库结果始终返回，语义结果依站点开关与服务商可用性）；
// 'save' = 保存前的词库检查（全部筛查字段），底部多“返回修改 / 忽略并保存”
const aiScreenVisible = ref<boolean>(false);
const aiScreenMode = ref<'ai' | 'save'>('ai');
const aiScreenLoading = ref<boolean>(false);
const aiScreenFindings = ref<any[]>([]);
const aiScreenSemanticError = ref<string | null>(null);
const aiScreenAvailable = computed(() => currentUser.commercialTier >= 2 && currentSiteStore.currentSite?.ai?.screening === true && !perm('ai:assist'));
const aiScreenGroups = computed(() =>
  ['sensitive', 'error', 'semantic'].map((key) => ({ key, findings: aiScreenFindings.value.filter((it: any) => it.type === key) })).filter((group) => group.findings.length > 0),
);
const aiScreenBlockCount = computed(() => aiScreenFindings.value.filter((it: any) => it.severity === 'block').length);

// 编辑器正文的文本节点及其在送检文本中的起始偏移：既用于拼出送检的纯文本，也用于按同一偏移定位，保证两者一致。
// 跨块（段落、单元格、列表项）与 <br> 处补一个换行：词不得跨段落命中——保存时服务端提取的文本同样在块之间留白，
// 不补则这里报出、保存却不拦的“敏感词”会让编辑无从下手
interface EditorText {
  text: string;
  spans: { node: Text; start: number }[];
}
const readEditorText = (editor: any): EditorText => {
  const root: Node = editor.getBody();
  const spans: { node: Text; start: number }[] = [];
  let text = '';
  let lastBlock: Node | null = null;
  let lineBreak = false;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
  for (let node = walker.nextNode(); node != null; node = walker.nextNode()) {
    if (node.nodeType !== Node.TEXT_NODE) {
      lineBreak = lineBreak || node.nodeName === 'BR';
      continue;
    }
    const block: Node = editor.dom.getParent(node, (it: Node) => editor.dom.isBlock(it)) ?? root;
    if (text.length > 0 && (lineBreak || block !== lastBlock)) {
      text += '\n';
    }
    lastBlock = block;
    lineBreak = false;
    spans.push({ node: node as Text, start: text.length });
    text += (node as Text).data;
  }
  return { text, spans };
};

// Markdown 编辑器：从渲染后的 HTML 提取纯文本（块之间同样补换行），筛查可用但不支持定位
const BLOCK_TAGS = new Set(['p', 'div', 'br', 'hr', 'li', 'ul', 'ol', 'dl', 'dt', 'dd', 'table', 'tr', 'td', 'th', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'pre', 'figure', 'figcaption']);
const htmlToText = (html: string): string => {
  let text = '';
  const lineBreak = () => {
    if (text.length > 0 && !text.endsWith('\n')) {
      text += '\n';
    }
  };
  const walk = (nodes: any[]) => {
    for (const node of nodes) {
      if (node.type === 'text') {
        text += node.data;
      } else if (node.children) {
        const block = BLOCK_TAGS.has(node.name);
        if (block) lineBreak();
        walk(node.children);
        if (block) lineBreak();
      }
    }
  };
  walk(htmlparser2.parseDocument(html).children);
  return text;
};

const aiScreenText = (): string => {
  if (values.value.editorType === 1 && tinymce.value?.editor) {
    return readEditorText(tinymce.value.editor).text;
  }
  return htmlToText(values.value.text ?? '');
};

// 保存前检查的送检内容：与服务端保存拦截同样的字段（WordScreeningService.articleFields）
const wordCheckPayload = () => ({
  title: values.value.title,
  subtitle: values.value.subtitle,
  fullTitle: values.value.fullTitle,
  seoDescription: values.value.seoDescription,
  text: aiScreenText(),
});

const handleAiScreen = async () => {
  const text = aiScreenText();
  if (!text.trim()) {
    ElMessage.warning(t('article.ai.screen.emptyText'));
    return;
  }
  aiScreenLoading.value = true;
  try {
    const data = await aiScreen({ text });
    aiScreenFindings.value = data.findings ?? [];
    aiScreenSemanticError.value = data.semanticError ?? null;
    aiScreenMode.value = 'ai';
    aiScreenVisible.value = true;
  } finally {
    aiScreenLoading.value = false;
  }
};

// 命中所在字段。AI 筛查只查正文，其结果不带字段
const findingField = (finding: any): string => finding.field ?? 'text';
const findingFieldLabel = (finding: any): string => mains.value[findingField(finding)]?.name ?? t(`article.${findingField(finding)}`);

const canLocateFinding = (finding: any): boolean => finding.start >= 0 && findingField(finding) === 'text' && values.value.editorType === 1;

// 按筛查偏移选中编辑器中的命中范围；返回是否成功
const selectFinding = (finding: any): boolean => {
  const editor = tinymce.value?.editor;
  if (!canLocateFinding(finding) || !editor) {
    return false;
  }
  const { spans } = readEditorText(editor);
  // 起点取“该偏移处字符所在的节点”，终点取“前一个字符所在的节点”：落在两个节点交界时不把相邻节点卷进选区
  const startSpan = spans.find((span) => finding.start >= span.start && finding.start < span.start + span.node.data.length);
  const endSpan = spans.find((span) => finding.end > span.start && finding.end <= span.start + span.node.data.length);
  if (startSpan == null || endSpan == null) {
    return false;
  }
  const range = editor.dom.createRng();
  range.setStart(startSpan.node, finding.start - startSpan.start);
  range.setEnd(endSpan.node, finding.end - endSpan.start);
  editor.focus();
  editor.selection.setRng(range);
  editor.selection.scrollIntoView();
  return true;
};

const locateFinding = (finding: any) => {
  selectFinding(finding);
};

// 修正：仅易错词有词库修正词。正文须能在富文本中定位（Markdown 正文只列出，不自动改）；标题、摘要等字段直接改字符串
const canFixFinding = (finding: any): boolean => finding.type === 'error' && !!finding.suggestion && (findingField(finding) !== 'text' || canLocateFinding(finding));
const aiScreenFixableCount = computed(() => aiScreenFindings.value.filter(canFixFinding).length);

const applyFix = (finding: any): boolean => {
  const field = findingField(finding);
  if (field !== 'text') {
    const value: string = values.value[field] ?? '';
    values.value[field] = value.slice(0, finding.start) + finding.suggestion + value.slice(finding.end);
    return true;
  }
  const editor = tinymce.value?.editor;
  if (!editor || !selectFinding(finding)) {
    return false;
  }
  // 词库修正词是文本而非 HTML
  editor.selection.setContent(editor.dom.encode(finding.suggestion));
  editor.undoManager.add();
  // 程序改动不经 v-model：保存继续时提交的须是修正后的正文
  values.value.text = editor.getContent();
  return true;
};

// 修正之后：已修正项及与其重叠的项（那段文本已变）移出列表；同一字段中位置靠后的项，偏移随长度变化平移
const settleFixed = (fixed: any[]) => {
  const overlaps = (it: any, fix: any) => findingField(it) === findingField(fix) && it.start < fix.end && it.end > fix.start;
  aiScreenFindings.value = aiScreenFindings.value
    .filter((it: any) => !fixed.some((fix) => it === fix || overlaps(it, fix)))
    .map((it: any) => {
      const shift = fixed
        .filter((fix) => findingField(it) === findingField(fix) && fix.end <= it.start)
        .reduce((sum, fix) => sum + fix.suggestion.length - (fix.end - fix.start), 0);
      return shift !== 0 ? { ...it, start: it.start + shift, end: it.end + shift } : it;
    });
  // 保存前检查：全部处理完即继续保存，不必再点一次
  if (aiScreenMode.value === 'save' && aiScreenFindings.value.length === 0) {
    finishSaveCheck(false);
  }
};

const fixFinding = (finding: any) => {
  if (canFixFinding(finding) && applyFix(finding)) {
    settleFixed([finding]);
  }
};

const fixAllFindings = () => {
  // 重叠的命中取最左、同起点取最长（如“帐号”与“登陆帐号”同时命中，只改后者）
  const sorted = aiScreenFindings.value
    .filter(canFixFinding)
    .sort((a: any, b: any) => findingField(a).localeCompare(findingField(b)) || a.start - b.start || b.end - a.end);
  const picked: any[] = [];
  for (const finding of sorted) {
    const last = picked[picked.length - 1];
    if (last == null || findingField(last) !== findingField(finding) || finding.start >= last.end) {
      picked.push(finding);
    }
  }
  // 各字段从后往前改：前面的偏移不受影响
  settleFixed(picked.reverse().filter((finding) => applyFix(finding)));
};

// AI 生成（docs/spec/ai/editorial-assist.md §6.2）：生成可见的 SEO 元信息目标，
// 空字段直接填充；非空字段列出旧值→新值，逐项勾选确认后覆盖；采纳标记仅在实际应用时发送一次
const aiMetaLoading = ref<boolean>(false);
const aiMetaAvailable = computed(() => currentUser.commercialTier >= 2 && currentSiteStore.currentSite?.ai?.seoAssist === true && !perm('ai:assist'));
const aiMetaTargets = computed(() => {
  const targets: string[] = [];
  if (mains.value['seoKeywords'].show) targets.push('seoKeywords');
  if (mains.value['seoDescription'].show) targets.push('seoDescription');
  if (mains.value['tags'].show) targets.push('tags');
  return targets;
});
// 按钮挂在首个可见目标字段之后（SEO关键词 → SEO描述 → 标签），不再单占一行
const aiMetaAnchor = computed(() => (aiMetaAvailable.value ? aiMetaTargets.value[0] : undefined));
const aiMetaOverwriteVisible = ref<boolean>(false);
const aiMetaOverwriteItems = ref<any[]>([]);
let aiMetaLogId: string | null = null;
let aiMetaAcceptFired = false;
let aiMetaAutoApplied = false;

const fireAiMetaAccept = () => {
  if (aiMetaLogId != null && !aiMetaAcceptFired) {
    aiMetaAcceptFired = true;
    updateAiLogAccepted(aiMetaLogId).catch(() => undefined);
  }
};

const handleAiMeta = async () => {
  const text = aiScreenText();
  if (!text.trim()) {
    ElMessage.warning(t('article.ai.meta.emptyText'));
    return;
  }
  aiMetaLoading.value = true;
  try {
    const data = await aiArticleMeta({ title: values.value.title, text, targets: aiMetaTargets.value });
    aiMetaLogId = data.logId ?? null;
    aiMetaAcceptFired = false;
    aiMetaAutoApplied = false;
    const conflicts: any[] = [];
    const fill = (target: string, label: string, newValue: string) => {
      if (!newValue) {
        return;
      }
      const oldValue = values.value[target];
      if (oldValue == null || String(oldValue).trim() === '') {
        values.value[target] = newValue;
        aiMetaAutoApplied = true;
      } else if (String(oldValue) !== newValue) {
        conflicts.push({ target, label, oldDisplay: String(oldValue), newDisplay: newValue, newValue, apply: true });
      }
    };
    if (aiMetaTargets.value.includes('seoKeywords')) {
      fill('seoKeywords', mains.value['seoKeywords'].name ?? t('article.seoKeywords'), (data.seoKeywords ?? []).join(','));
    }
    if (aiMetaTargets.value.includes('seoDescription')) {
      fill('seoDescription', mains.value['seoDescription'].name ?? t('article.seoDescription'), data.seoDescription ?? '');
    }
    // 标签为建议：与既有标签合并去重展示，不做整体替换
    const suggestedTags: string[] = data.tags ?? [];
    if (suggestedTags.length > 0) {
      const existing: string[] = values.value.tagNames ?? [];
      if (existing.length === 0) {
        values.value.tagNames = suggestedTags;
        aiMetaAutoApplied = true;
      } else {
        const merged = Array.from(new Set([...existing, ...suggestedTags]));
        if (merged.length > existing.length) {
          conflicts.push({
            target: 'tagNames',
            label: mains.value['tags'].name ?? t('article.tags'),
            oldDisplay: existing.join(','),
            newDisplay: merged.join(','),
            newValue: merged,
            apply: true,
          });
        }
      }
    }
    if (conflicts.length > 0) {
      aiMetaOverwriteItems.value = conflicts;
      aiMetaOverwriteVisible.value = true;
    } else if (aiMetaAutoApplied) {
      fireAiMetaAccept();
      ElMessage.success(t('article.ai.meta.applied'));
    } else {
      ElMessage.info(t('article.ai.meta.emptyResult'));
    }
  } finally {
    aiMetaLoading.value = false;
  }
};

const handleAiMetaOverwrite = () => {
  let applied = aiMetaAutoApplied;
  aiMetaOverwriteItems.value.forEach((item) => {
    if (item.apply) {
      values.value[item.target] = item.newValue;
      applied = true;
    }
  });
  aiMetaOverwriteVisible.value = false;
  if (applied) {
    fireAiMetaAccept();
    ElMessage.success(t('article.ai.meta.applied'));
  }
};

const handleAiMetaOverwriteClose = () => {
  // 放弃覆盖时，已直接填充的空字段仍属采纳（fireAiMetaAccept 幂等）
  if (aiMetaAutoApplied) {
    fireAiMetaAccept();
  }
};

// 别名（固定链接）：站点链接形态为“数字型”时文章地址不含别名，字段隐藏；新增文章标题失焦且别名为空时按标题请求建议，
// 用户手动改过别名（slugTouched）后不再自动覆盖，切换文章时重置。建议为空串表示保存时回退为文章 ID。
// 既有文章：等于文章 ID 的别名是“未设别名”的占位值，表单显示为空；未手动改动则原样送回，避免改个标题再保存就换了地址
const slugTouched = ref<boolean>(false);
const origSlug = ref<string>('');
const slugVisible = computed(() => mains.value['slug'].show && currentSiteStore.currentSite?.permalink?.article !== 'id');
const isSentinelSlug = (bean: any) => bean.id != null && bean.slug === String(bean.id);
const suggestSlug = async () => {
  const title = values.value.title?.trim();
  if (!slugVisible.value || slugTouched.value || values.value.slug || !title || values.value.id != null) {
    return;
  }
  try {
    const slug = await articleSlugSuggest(title, values.value.id);
    // 请求期间用户可能已手动填写
    if (!slugTouched.value && !values.value.slug) {
      values.value.slug = slug;
    }
  } catch {
    // 建议失败不阻断编辑，保存时由后端生成
  }
};

// AI 标题（docs/spec/ai/editorial-assist.md §6.2）：根据正文（及现有标题）生成候选标题，
// 列表中点「使用」才替换标题；与智能SEO同一开关（aiMetaAvailable），采纳标记在实际使用时发送一次
const aiTitlesVisible = ref<boolean>(false);
const aiTitlesLoading = ref<boolean>(false);
const aiTitlesList = ref<string[]>([]);
let aiTitlesLogId: string | null = null;

const handleAiTitles = async () => {
  const text = aiScreenText();
  if (!text.trim()) {
    ElMessage.warning(t('article.ai.meta.emptyText'));
    return;
  }
  aiTitlesLoading.value = true;
  try {
    const data = await aiArticleTitles({ title: values.value.title, text });
    const titles: string[] = data.titles ?? [];
    if (titles.length === 0) {
      // 重新生成落空时保留上一批候选
      ElMessage.info(t('article.ai.titles.emptyResult'));
      return;
    }
    aiTitlesLogId = data.logId ?? null;
    aiTitlesList.value = titles;
    aiTitlesVisible.value = true;
  } finally {
    aiTitlesLoading.value = false;
  }
};

const applyAiTitle = (title: string) => {
  values.value.title = title;
  if (aiTitlesLogId != null) {
    updateAiLogAccepted(aiTitlesLogId).catch(() => undefined);
    aiTitlesLogId = null;
  }
  aiTitlesVisible.value = false;
  ElMessage.success(t('article.ai.titles.applied'));
};

// AI 别名：根据标题（及正文）生成候选别名，服务端已 slug 化并去重，列表中点「使用」才填入；
// 与智能SEO同一开关（aiMetaAvailable），采纳标记在实际使用时发送一次
const aiSlugsVisible = ref<boolean>(false);
const aiSlugsLoading = ref<boolean>(false);
const aiSlugsList = ref<string[]>([]);
let aiSlugsLogId: string | null = null;

const handleAiSlugs = async () => {
  const title = values.value.title?.trim();
  if (!title) {
    return;
  }
  aiSlugsLoading.value = true;
  try {
    const text = aiScreenText().trim();
    const data = await aiArticleSlugs({ title, text: text || undefined, id: values.value.id });
    const slugs: string[] = data.slugs ?? [];
    if (slugs.length === 0) {
      // 重新生成落空时保留上一批候选
      ElMessage.info(t('article.ai.slug.empty'));
      return;
    }
    aiSlugsLogId = data.logId ?? null;
    aiSlugsList.value = slugs;
    aiSlugsVisible.value = true;
  } finally {
    aiSlugsLoading.value = false;
  }
};

const applyAiSlug = (slug: string) => {
  values.value.slug = slug;
  slugTouched.value = true;
  if (aiSlugsLogId != null) {
    updateAiLogAccepted(aiSlugsLogId).catch(() => undefined);
    aiSlugsLogId = null;
  }
  aiSlugsVisible.value = false;
};

// AIGC 标识（docs/spec/ai/ai-labeling.md §6）：采纳生成式改写（扩写/续写）自动勾选；
// 润色/校对与 SEO 元信息采纳不改动标识。自动勾选或打开已标记文章后，取消勾选需确认
// AI 生成段落确已移除，防止静默去标；手工勾选无需确认
const aiGeneratedSticky = ref<boolean>(false);

const handleAiAccept = (mode: string) => {
  if (mode === 'expand' || mode === 'continue') {
    // 开关位于右侧「设置」页签，自动勾选时提示一次，避免标记无声生效
    if (values.value.aiGenerated !== true) {
      ElMessage.info(t('article.aiGenerated.autoSet'));
    }
    values.value.aiGenerated = true;
    aiGeneratedSticky.value = true;
  }
};

const handleAiGeneratedChange = (checked: boolean) => {
  if (checked || !aiGeneratedSticky.value) {
    return;
  }
  // 先恢复勾选，确认通过后再清除，避免未确认时视觉状态与表单值脱节
  values.value.aiGenerated = true;
  ElMessageBox.confirm(t('article.aiGenerated.clearConfirm'), t('article.aiGenerated'), {
    confirmButtonText: t('ok'),
    cancelButtonText: t('cancel'),
    type: 'warning',
  })
    .then(() => {
      values.value.aiGenerated = false;
      aiGeneratedSticky.value = false;
    })
    .catch(() => undefined);
};
</script>

<template>
  <div>
    <dialog-form
      ref="dialog"
      v-model:values="values"
      :name="$t('menu.content.article')"
      append-to-body
      :query-bean="isReview ? queryArticleReview : queryArticle"
      :create-bean="createAndPublish"
      :update-bean="updateArticle"
      :delete-bean="completelyDeleteArticle"
      :bean-id="beanId"
      :bean-ids="beanIds"
      :action="action"
      :focus="focus"
      :init-values="
        () => {
          articleModelId = channel?.articleModelId;
          return {
            editorType: mains['text'].editorType,
            channelId: channel?.id,
            publishDate: new Date(),
            allowComment: true,
            aiGenerated: false,
            customs: initCustoms({}),
            fileList: [],
            imageList: [],
            refersChannelIds: [],
            copiesChannelIds: [],
            topicIds: [],
            related: [],
            incomingList: [],
          };
        }
      "
      :to-values="
        (bean) => ({
          ...bean,
          // 复制文章时不带走别名：源文章的别名（或等于其 ID 的占位值）在新文章上会冲突，留空由后端按标题重新生成；
          // 等于文章 ID 的占位别名显示为空（提交时未改动则原样送回，见 before-submit）
          slug: isCopy || isSentinelSlug(bean) ? '' : bean.slug,
          // 自定义字段的默认值在这里补上（按这篇文章自己的模型），不靠事后的监听——事后补就成了“未保存”
          customs: initCustoms({ ...(bean.customs ?? {}) }, fieldsOf(bean.channel?.articleModelId)),
          tagNames: bean.tags.map((item) => item.name),
          // 扩展栏目是本站之内的事：只取与本文同站点的子行。站群推送到其它站点的子行不在其列（服务端同步时也不动它们），
          // 仍列在下方只读的推送列表里（docs/spec/article-data-permission/plan.md D15）。子行只带 site 对象，没有 siteId
          refersChannelIds: (bean.destList ?? []).filter((d) => d.type === 3 && d.site?.id === bean.siteId).map((d) => d.channel.id),
          copiesChannelIds: (bean.destList ?? []).filter((d) => d.type === 1 && d.site?.id === bean.siteId).map((d) => d.channel.id),
          topicIds: (bean.topicList ?? []).map((t) => t.id),
          // 相关文章：行的 mutual 由“对方是否也把本文列为相关”推得；复制文章带走列表但不带走互链与被关联
          related: (bean.relatedList ?? []).map((a) => ({
            id: a.id,
            mutual: !isCopy && (bean.incomingList ?? []).some((i) => i.id === a.id),
            article: a,
          })),
          incomingList: isCopy ? [] : (bean.incomingList ?? []),
        })
      "
      perms="article"
      :model-value="modelValue"
      :addable="!isReview"
      :disable-edit="(bean) => (isReview ? perm('article:update') : bean.id != null && !bean.editable && !currentUser.allStatusPermission)"
      :prevent-submit="preventSubmit"
      label-width="140px"
      large
      @update:model-value="(event) => $emit('update:modelValue', event)"
      @finished="
        () => {
          clearDraft();
          $emit('finished');
        }
      "
      @discard="() => clearDraft()"
      @before-submit="
        (submitValues) => {
          // 既有文章、别名留空且未手动改动：送回原占位别名，地址保持不变；主动清空则由后端重新生成。
          // 克隆出来的是新文章（表单里带着的是源文章的 ID）：别名留空交给后端生成，送回源文章的别名必然重复
          if (dialog?.isEdit && submitValues.id != null && !submitValues.slug && !slugTouched) {
            submitValues.slug = origSlug;
          }
          // 相关文章只提交 {id, mutual}；回显用的 article 与被关联列表不入请求
          submitValues.related = (submitValues.related ?? []).map((r) => ({ id: r.id, mutual: r.mutual === true }));
          delete submitValues.relatedList;
          delete submitValues.incomingList;
        }
      "
      @bean-change="
        (bean, refreshed) => {
          // refreshed：新文章保存后留在对话框里，bean 按服务端现值在后台刷新了一次。表单没有换，
          // 用户正开着的可选字段（副标题等）不收起来；只更新别名的基准，并把服务端生成的别名、默认的组织补进表单
          if (!refreshed) {
            showSubtitle = !!bean.subtitle;
            showFullTitle = !!bean.fullTitle;
            showLinkUrl = !!bean.linkUrl;
            aiGeneratedSticky = bean.aiGenerated === true;
          } else {
            // 组织在新增时由服务端取默认值，表单里没有：不补上，接下来的保存就把“没有组织”提交了回去
            dialog?.patch((target) => {
              if (target.orgId == null) target.orgId = bean.orgId;
              if (!target.slug && !isSentinelSlug(bean)) target.slug = bean.slug;
            });
          }
          slugTouched = false;
          origSlug = bean.slug ?? '';
          articleModelId = bean.channel?.articleModelId ?? channel?.articleModelId;
        }
      "
    >
      <template #header-action="{ isEdit, bean, unsaved, handleDelete }">
        <el-button-group v-if="isReview">
          <el-button v-if="isEdit && isReview" :disabled="perm('articleReview:pass') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" type="primary" :icon="CircleCheck" @click="() => handlePass(bean)">
            {{ $t('review.pass') }}
          </el-button>
          <el-button :disabled="perm('articleReview:reject') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="CircleClose" @click="() => handleReject(bean)">{{ $t('review.reject') }}</el-button>
          <el-button v-if="currentUser.commercialTier >= 3" :disabled="perm('articleReview:back') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="Back" @click="() => handleBack(bean)">
            {{ $t('review.back') }}
          </el-button>
          <el-button v-if="currentUser.commercialTier >= 3" :disabled="perm('articleReview:delegate') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="SetUp" @click="() => handleDelegate(bean)">
            {{ $t('review.delegate') }}
          </el-button>
          <el-button v-if="currentUser.commercialTier >= 3" :disabled="perm('articleReview:transfer') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="Compass" @click="() => handleTransfer(bean)">
            {{ $t('review.transfer') }}
          </el-button>
        </el-button-group>
        <el-button-group v-if="!isReview">
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('submit', bean.id)">
            <template #reference>
              <el-button :disabled="!canSubmit(bean) || perm('article:submit') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="Finished">{{ $t('article.op.submit') }}</el-button>
            </template>
          </el-popconfirm>
          <el-button v-if="isEdit && canReject(bean)" :disabled="perm('article:reject') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="CircleClose" @click="() => handleRejectDirect(bean)">
            {{ $t('article.op.reject') }}
          </el-button>
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('archive', bean.id)">
            <template #reference>
              <el-button :disabled="!canArchive(bean) || perm('article:archive') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="Box">{{ $t('article.op.archive') }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('offline', bean.id)">
            <template #reference>
              <el-button :disabled="!canOffline(bean) || perm('article:offline') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="CircleClose">{{ $t('article.op.offline') }}</el-button>
            </template>
          </el-popconfirm>
        </el-button-group>
        <el-button v-if="isEdit" :icon="View" @click="() => openArticleLink(bean.status, bean.fullUrl, bean.dynamicFullUrl)">{{ $t('article.op.preview') }}</el-button>
        <el-button-group>
          <!-- 回收站里的文章：删除换成恢复（回到草稿，docs/spec/article-management/plan.md D2） -->
          <el-button
            v-if="isEdit && !isReview && canRestore(bean)"
            :disabled="perm('article:delete') || unsaved"
            :title="unsaved ? $t('article.unsavedFirst') : undefined"
            :icon="RefreshLeft" @click="() => handleExecute('restore', bean.id)">
            {{ $t('article.op.restore') }}
          </el-button>
          <el-popconfirm v-else-if="isEdit && !isReview" :title="$t('confirmDelete')" @confirm="() => handleExecute('delete', bean.id)">
            <template #reference>
              <el-button :disabled="perm('article:delete') || unsaved" :title="unsaved ? $t('article.unsavedFirst') : undefined" :icon="DocumentRemove">{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="isEdit" :title="$t('confirmCompletelyDelete')" @confirm="() => handleDelete()">
            <template #reference>
              <el-button :disabled="perm('article:completelyDelete')" :icon="Delete">{{ $t('article.op.completelyDelete') }}</el-button>
            </template>
          </el-popconfirm>
        </el-button-group>
      </template>
      <template #header-status="{ isEdit, bean }">
        <template v-if="isEdit">
          <span v-if="bean.status != null">
            <el-tag :type="articleStatusTagType(bean.status)" size="small" disable-transitions>{{ $t(`article.status.${bean.status}`) }}</el-tag>
          </span>
        </template>
      </template>
      <template #default="{ bean, isEdit, disabled }">
        <!-- 已退回的文章：审核人写的原因放在最上面，作者打开就看到 -->
        <el-alert v-if="isEdit && bean.status === 22 && bean.rejectReason" type="error" :closable="false" show-icon class="mb-4" :title="$t('article.rejectReason')" :description="bean.rejectReason" />
        <el-row>
          <el-col :span="18">
            <el-row>
              <el-col v-if="values.src != null" :span="mains['title'].double ? 12 : 24">
                <el-form-item :label="$t('article.src')">
                  <el-input :model-value="values.src.title" disabled>
                    <template #prepend>{{ t(`article.type.${values.type}`) }}</template>
                    <template #append>{{ values.src.channel.paths.map((it: any) => it.name).join('/') }}</template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="mains['title'].double ? 12 : 24">
                <el-form-item prop="title" :label="mains['title'].name ?? $t('article.title')" :rules="{ required: true, message: () => $t('v.required') }">
                  <el-input ref="focus" v-model="values.title" maxlength="150" @blur="() => suggestSlug()"></el-input>
                  <el-tooltip v-if="aiMetaAvailable" :content="$t('article.ai.titles.tip')" placement="top">
                    <el-button :loading="aiTitlesLoading" @click="() => handleAiTitles()">{{ $t('article.op.aiTitles') }}</el-button>
                  </el-tooltip>
                  <el-button :disabled="!values.title" class="mr-5" @click="() => titleSimilarity(values.title, isEdit ? values.id : undefined)">{{ $t('article.op.titleSimilarity') }}</el-button>
                  <el-checkbox v-if="mains['subtitle'].show && !mains['subtitle'].required" v-model="showSubtitle" @click="() => (values.subtitle = undefined)">
                    {{ mains['subtitle'].name ?? $t('article.subtitle') }}
                  </el-checkbox>
                  <el-checkbox v-if="mains['fullTitle'].show && !mains['fullTitle'].required" v-model="showFullTitle" @click="() => (values.fullTitle = undefined)">
                    {{ mains['fullTitle'].name ?? $t('article.fullTitle') }}
                  </el-checkbox>
                  <el-checkbox
                    v-if="mains['linkUrl'].show && !mains['linkUrl'].required"
                    v-model="showLinkUrl"
                    @click="() => ((values.linkUrl = undefined), (values.targetBlank = false))"
                  >
                    {{ mains['linkUrl'].name ?? $t('article.linkUrl') }}
                  </el-checkbox>
                </el-form-item>
              </el-col>
              <el-col v-if="slugVisible" :span="mains['slug'].double ? 12 : 24">
                <el-form-item
                  prop="slug"
                  :rules="[
                    ...(mains['slug'].required ? [{ required: true, message: () => $t('v.required') }] : []),
                    { pattern: /^[\w-]*$/, message: () => $t('article.error.slugPattern') },
                    {
                      trigger: 'blur',
                      asyncValidator: async (rule, value, callback) => {
                        // 编辑时未改动的别名不查重；复制的新文章 id 为源文章，不能用于排除自身
                        if (value && !(isEdit && value === bean.slug) && (await articleSlugExist(value, isEdit ? values.id : undefined))) {
                          callback($t('article.error.slugExist'));
                          return;
                        }
                        callback();
                      },
                    },
                  ]"
                >
                  <template #label><label-tip :label="mains['slug'].name ?? $t('article.slug')" message="article.slug" help /></template>
                  <el-input v-model="values.slug" maxlength="160" :placeholder="$t('article.slug.placeholder')" @input="() => (slugTouched = true)"></el-input>
                  <el-tooltip v-if="aiMetaAvailable" :content="$t('article.ai.slug.tip')" placement="top">
                    <el-button :loading="aiSlugsLoading" :disabled="!values.title" @click="() => handleAiSlugs()">{{ $t('article.op.aiSlug') }}</el-button>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col
                v-if="((values.subtitle || showSubtitle) && mains['subtitle'].show) || (mains['subtitle'].show && mains['subtitle'].required)"
                :span="mains['subtitle'].double ? 12 : 24"
              >
                <el-form-item
                  prop="subtitle"
                  :label="mains['subtitle'].name ?? $t('article.subtitle')"
                  :rules="mains['subtitle'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-input v-model="values.subtitle" maxlength="150"></el-input>
                </el-form-item>
              </el-col>
              <el-col
                v-if="((values.fullTitle || showFullTitle) && mains['fullTitle'].show) || (mains['fullTitle'].show && mains['fullTitle'].required)"
                :span="mains['fullTitle'].double ? 12 : 24"
              >
                <el-form-item
                  prop="fullTitle"
                  :label="mains['fullTitle'].name ?? $t('article.fullTitle')"
                  :rules="mains['fullTitle'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-input v-model="values.fullTitle" type="textarea" :rows="2" maxlength="150"></el-input>
                </el-form-item>
              </el-col>
              <el-col
                v-if="((values.linkUrl || showLinkUrl) && mains['linkUrl'].show) || (mains['linkUrl'].show && mains['linkUrl'].required)"
                :span="mains['linkUrl'].double ? 12 : 24"
              >
                <el-form-item
                  prop="linkUrl"
                  :rules="
                    mains['linkUrl'].required
                      ? [
                          { required: true, message: () => $t('v.required') },
                          { pattern: /^(http|\/).*$/, message: () => $t('article.error.linkUrl') },
                        ]
                      : { pattern: /^(http|\/).*$/, message: () => $t('article.error.linkUrl') }
                  "
                >
                  <template #label><label-tip :label="mains['linkUrl'].name ?? $t('article.linkUrl')" message="article.linkUrl" help /></template>
                  <el-input v-model="values.linkUrl" maxlength="255" :disabled="values.src != null && values.type === 3">
                    <template #append>
                      <el-checkbox v-model="values.targetBlank">{{ $t('article.targetBlank') }}</el-checkbox>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['tags'].show" :span="mains['tags'].double ? 12 : 24">
                <el-form-item
                  prop="tagNames"
                  :label="mains['tags'].name ?? $t('article.tags')"
                  :rules="mains['tags'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-select
                    v-model="values.tagNames"
                    multiple
                    filterable
                    remote
                    clearable
                    allow-create
                    default-first-option
                    :reserve-keyword="false"
                    :remote-method="tagRemoteMethod"
                    :loading="tagLoading"
                    class="flex-1 min-w-0"
                    @focus.once="() => tagRemoteMethod()"
                  >
                    <el-option v-for="item in tagList.map((it) => it.name)" :key="item" :label="item" :value="item" />
                  </el-select>
                  <el-tooltip v-if="aiMetaAnchor === 'tags'" :content="$t('article.ai.meta.tip')" placement="top">
                    <el-button class="self-start ml-2" :loading="aiMetaLoading" @click="() => handleAiMeta()">{{ $t('article.op.aiMeta') }}</el-button>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['source'].show" :span="mains['source'].double ? 12 : 24">
                <el-form-item
                  prop="source"
                  :label="mains['source'].name ?? $t('article.source')"
                  :rules="mains['source'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-autocomplete
                    v-model="values.source"
                    maxlength="50"
                    value-key="name"
                    :fetch-suggestions="async (query: string, callback: any) => callback(await fetchSourceList(query))"
                    class="w-full"
                    highlight-first-item
                    hide-loading
                    clearable
                    @keydown.enter.prevent
                  ></el-autocomplete>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['topics'].show && currentUser.commercialTier >= 2 && !perm('topic:list')" :span="mains['topics'].double ? 12 : 24">
                <el-form-item
                  prop="topicIds"
                  :label="mains['topics'].name ?? $t('article.topics')"
                  :rules="mains['topics'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-tree-select
                    v-model="values.topicIds"
                    :data="topicList"
                    node-key="id"
                    multiple
                    :props="{ label: 'name' }"
                    :render-after-expand="false"
                    show-checkbox
                    check-strictly
                    class="w-full"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="mains['seoKeywords'].show" :span="mains['seoKeywords'].double ? 12 : 24">
                <el-form-item prop="seoKeywords" :rules="mains['seoKeywords'].required ? { required: true, message: () => $t('v.required') } : undefined">
                  <template #label><label-tip :label="mains['seoKeywords'].name ?? $t('article.seoKeywords')" message="article.seoKeywords" help /></template>
                  <el-input v-model="values.seoKeywords" maxlength="150" class="flex-1 min-w-0"></el-input>
                  <el-tooltip v-if="aiMetaAnchor === 'seoKeywords'" :content="$t('article.ai.meta.tip')" placement="top">
                    <el-button class="self-start ml-2" :loading="aiMetaLoading" @click="() => handleAiMeta()">{{ $t('article.op.aiMeta') }}</el-button>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['seoDescription'].show" :span="mains['seoDescription'].double ? 12 : 24">
                <el-form-item
                  prop="seoDescription"
                  :label="mains['seoDescription'].name ?? $t('article.seoDescription')"
                  :rules="mains['seoDescription'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-input v-model="values.seoDescription" type="textarea" :rows="3" maxlength="1000" class="flex-1 min-w-0"></el-input>
                  <el-tooltip v-if="aiMetaAnchor === 'seoDescription'" :content="$t('article.ai.meta.tip')" placement="top">
                    <el-button class="self-start ml-2" :loading="aiMetaLoading" @click="() => handleAiMeta()">{{ $t('article.op.aiMeta') }}</el-button>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['author'].show" :span="mains['author'].double ? 12 : 24">
                <el-form-item
                  prop="author"
                  :label="mains['author'].name ?? $t('article.author')"
                  :rules="mains['author'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-input v-model="values.author" maxlength="50"></el-input>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['editor'].show" :span="mains['editor'].double ? 12 : 24">
                <el-form-item
                  prop="editor"
                  :label="mains['editor'].name ?? $t('article.editor')"
                  :rules="mains['editor'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-input v-model="values.editor" maxlength="50"></el-input>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['image'].show" :span="mains['image'].double ? 12 : 24">
                <el-form-item
                  prop="image"
                  :label="mains['image'].name ?? $t('article.image')"
                  :rules="mains['image'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <image-upload
                    v-model="values.image"
                    :height="mains['image'].imageHeight"
                    :width="mains['image'].imageWidth"
                    :mode="mains['image'].imageMode"
                    :disabled="disabled"
                  ></image-upload>
                  <el-button class="self-start ml-2" @click="() => extractImage()">{{ $t('article.extractImage') }}</el-button>
                </el-form-item>
              </el-col>
              <template v-for="field in fields" :key="field.code">
                <el-col :span="field.double ? 12 : 24">
                  <el-form-item :prop="`customs.${field.code}`" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
                    <template #label><label-tip :label="field.name" /></template>
                    <field-item v-model="values.customs[field.code]" v-model:model-key="values.customs[field.code + 'Key']" :field="field"></field-item>
                  </el-form-item>
                </el-col>
              </template>
              <el-col v-if="mains['file'].show" :span="mains['file'].double ? 12 : 24">
                <el-form-item
                  prop="file"
                  :label="mains['file'].name ?? $t('article.file')"
                  :rules="mains['file'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-row class="w-full">
                    <el-col :span="16">
                      <el-input v-model="values.fileName">
                        <template #prepend>{{ $t('name') }}</template>
                      </el-input>
                    </el-col>
                    <el-col :span="8">
                      <el-input v-model="values.fileLength">
                        <template #prepend>{{ $t('size') }}</template>
                        <template #append>Byte</template>
                      </el-input>
                    </el-col>
                  </el-row>
                  <el-input v-model="values.file">
                    <template #prepend>URL</template>
                  </el-input>
                  <base-upload
                    type="file"
                    pickable
                    :on-success="(res: any) => ((values.file = res.url), (values.fileName = res.name), (values.fileLength = res.size))"
                    :disabled="disabled"
                  ></base-upload>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['video'].show" :span="mains['video'].double ? 12 : 24">
                <el-form-item
                  prop="video"
                  :label="mains['video'].name ?? $t('article.video')"
                  :rules="mains['video'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-row class="w-full">
                    <el-col :span="16">
                      <el-input v-model="values.video" maxlength="255">
                        <template #prepend>URL</template>
                      </el-input>
                    </el-col>
                    <el-col :span="8">
                      <el-input v-model="values.videoDuration" maxlength="10">
                        <template #prepend>{{ $t('article.videoDuration') }}</template>
                        <template v-if="values.videoDuration" #append> {{ formatDuration(values.videoDuration) }}</template>
                      </el-input>
                    </el-col>
                  </el-row>
                  <base-upload
                    type="video"
                    pickable
                    :on-success="
                      (res) => {
                        values.video = res.url;
                        values.videoDuration = res.duration;
                        if (!values.image) {
                          values.image = res.image;
                        }
                      }
                    "
                    :disabled="disabled"
                  ></base-upload>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['audio'].show" :span="mains['audio'].double ? 12 : 24">
                <el-form-item
                  prop="audio"
                  :label="mains['audio'].name ?? $t('article.audio')"
                  :rules="mains['audio'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-row class="w-full">
                    <el-col :span="16">
                      <el-input v-model="values.audio" maxlength="255">
                        <template #prepend>URL</template>
                      </el-input>
                    </el-col>
                    <el-col :span="8">
                      <el-input v-model="values.audioDuration" maxlength="10">
                        <template #prepend>{{ $t('article.audioDuration') }}</template>
                        <template v-if="values.audioDuration" #append> {{ formatDuration(values.audioDuration) }}</template>
                      </el-input>
                    </el-col>
                  </el-row>
                  <base-upload
                    type="audio"
                    pickable
                    :on-success="
                      (res) => {
                        values.audio = res.url;
                        values.audioDuration = res.duration;
                      }
                    "
                    :disabled="disabled"
                  ></base-upload>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['doc'].show" :span="mains['doc'].double ? 12 : 24">
                <el-form-item
                  prop="doc"
                  :label="mains['doc'].name ?? $t('article.doc')"
                  :rules="mains['doc'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <div class="w-full">
                    <el-row>
                      <el-col :span="12">
                        <el-input v-model="values.docName">
                          <template #prepend>{{ $t('name') }}</template>
                        </el-input>
                      </el-col>
                      <el-col :span="12">
                        <el-input v-model="values.docLength">
                          <template #prepend>{{ $t('size') }}</template>
                          <template #append>Byte</template>
                        </el-input>
                      </el-col>
                    </el-row>
                    <el-input v-model="values.doc">
                      <template #prepend>URL</template>
                    </el-input>
                    <el-input v-model="values.docOrig">
                      <template #prepend>{{ $t('article.docOrig') }}</template>
                    </el-input>
                    <base-upload
                      v-if="docImport.office && currentUser.commercialTier > 0"
                      type="library"
                      :disabled="perm('jodConvert:library') || disabled"
                      :upload-action="jodConvertLibraryUrl"
                      :on-success="
                        (res: any) => {
                          values.doc = res.doc;
                          values.docOrig = res.docOrig;
                          values.docName = res.docName;
                          values.docLength = res.docLength;
                          values.image = res.docImage;
                        }
                      "
                    ></base-upload>
                    <el-alert v-if="!docImport.office && currentUser.commercialTier > 0" :title="$t('error.jodConverterNotEnabled')" type="error" :closable="false" />
                    <el-button v-if="currentUser.commercialTier === 0" @click="() => showCommercialNotice(1, $t('commercial.feature.docImport'))">
                      {{ $t('article.op.clickToUpload') }}<commercial-badge :tier="1" class="ml-1" />
                    </el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['imageList'].show" :span="mains['imageList'].double ? 12 : 24">
                <el-form-item
                  prop="imageList"
                  :label="mains['imageList'].name ?? $t('article.imageList')"
                  :rules="mains['imageList'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <image-list-upload
                    v-model="values.imageList"
                    :max-height="mains['imageList'].imageMaxHeight"
                    :max-width="mains['imageList'].imageMaxWidth"
                    :list-type="mains['imageList'].imageListType"
                    :disabled="disabled"
                  ></image-list-upload>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['fileList'].show" :span="mains['fileList'].double ? 12 : 24">
                <el-form-item
                  prop="fileList"
                  :label="mains['fileList'].name ?? $t('article.fileList')"
                  :rules="mains['fileList'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <file-list-upload v-model="values.fileList" :disabled="disabled"></file-list-upload>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['text'].show" :span="mains['text'].double ? 12 : 24">
                <el-form-item
                  prop="text"
                  :label="mains['text'].name ?? $t('article.text')"
                  :rules="mains['text'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <div class="w-full">
                    <el-radio-group v-if="mains['text'].editorSwitch" :model-value="values.editorType" class="mr-6" @change="onEditorTypeChange">
                      <el-radio v-for="n in [1, 2]" :key="n" :value="n">{{ $t(`model.field.editorType.${n}`) }}</el-radio>
                    </el-radio-group>
                    <!-- .docx/.doc/.wps 由内置引擎读取，按钮总是可用；LibreOffice 没开时悬停说明哪些格式读不了 -->
                    <div
                      v-if="values.editorType === 1 && currentUser.commercialTier > 0"
                      class="inline-flex mb-1"
                      :title="docImport.office ? undefined : $t('article.docImport.officeOff')"
                    >
                      <base-upload
                        type="doc"
                        :file-accept="docImportAccept"
                        :disabled="perm('jodConvert:doc') || docImport.extensions.length === 0 || disabled"
                        :upload-action="jodConvertDocUrl"
                        :on-success="(res: DocImportResult) => handleDocImported(res)"
                        :button="$t('article.op.docImport')"
                      ></base-upload>
                    </div>
                    <div v-if="values.editorType === 1 && currentUser.commercialTier === 0" class="inline-flex mb-1">
                      <el-button @click="() => showCommercialNotice(1, $t('commercial.feature.docImport'))">
                        {{ $t('article.op.docImport') }}<commercial-badge :tier="1" class="ml-1" />
                      </el-button>
                    </div>
                    <div v-if="aiScreenAvailable" class="inline-flex mb-1 ml-1">
                      <el-button :loading="aiScreenLoading" @click="() => handleAiScreen()">{{ $t('article.op.aiScreen') }}</el-button>
                    </div>
                    <markdown-editor v-if="values.editorType === 2" v-model="values.markdown" v-model:html="values.text" :disabled="disabled" class="leading-6" @keydown="handleSaveShortcut" />
                    <Tinymce
                      v-else
                      ref="tinymce"
                      v-model="values.text"
                      :disabled="disabled"
                      :draft-key="draftKey"
                      :init="{ typesetting_options: currentSiteStore.currentSite?.editor?.typesetting, ai_accept_handler: handleAiAccept }"
                      @keydown="handleSaveShortcut"
                    />
                  </div>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['related'].show" :span="24">
                <el-form-item prop="related" :rules="mains['related'].required ? { type: 'array', required: true, min: 1, message: () => $t('v.required') } : undefined">
                  <template #label><label-tip :label="mains['related'].name ?? $t('article.related')" message="article.related" help /></template>
                  <article-related-panel
                    v-model="values.related"
                    :incoming="values.incomingList ?? []"
                    :article-id="values.id"
                    :seed="{ title: values.title, channelId: values.channelId, tagNames: values.tagNames, text: values.text }"
                    :disabled="disabled"
                  />
                </el-form-item>
              </el-col>
              <template v-for="field in editorFields" :key="field.code">
                <el-col :span="field.double ? 12 : 24">
                  <el-form-item :prop="`customs.${field.code}`" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
                    <template #label><label-tip :label="field.name" /></template>
                    <field-item v-model="values.customs[field.code]" :field="field"></field-item>
                  </el-form-item>
                </el-col>
              </template>
            </el-row>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="ml-5">
              <template #header>{{ $t('article.tabs.setting') }}</template>
                <el-form-item
                  prop="channelId"
                  :label="asides['channel'].name ?? $t('article.channel')"
                  :rules="[
                    { required: true, message: () => $t('v.required') },
                    {
                      validator: (rule, value, callback) => {
                        if (flatChannelList.some((it) => it.parentId === value)) {
                          callback($t('article.error.leafChannelOnly'));
                          return;
                        }
                        callback();
                      },
                    },
                  ]"
                  label-position="top"
                >
                  <el-tree-select
                    v-model="values.channelId"
                    :data="channelList"
                    node-key="id"
                    :props="{ label: 'name' }"
                    :render-after-expand="false"
                    :default-expanded-keys="bean.channel?.paths.map((it) => it.id)"
                    class="w-full"
                    @change="
                      (value) => {
                        articleModelId = flatChannelList.find((item) => item.id === value)?.articleModelId;
                        // 主栏目不能同时是扩展栏目：改成已勾选的扩展栏目时去掉那一项，由服务端删去该子行、源文章顶上
                        values.refersChannelIds = (values.refersChannelIds ?? []).filter((id) => id !== value);
                        values.copiesChannelIds = (values.copiesChannelIds ?? []).filter((id) => id !== value);
                      }
                    "
                  />
                </el-form-item>
                <el-form-item
                  v-if="asides['channels'].show"
                  prop="refersChannelIds"
                  :rules="asides['channels'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['channels'].name ?? $t('article.channels')" :message="$t('article.channels.tooltip')" /></template>
                  <el-tree-select
                    v-model="values.refersChannelIds"
                    :data="channelList"
                    node-key="id"
                    multiple
                    :props="{
                      label: 'name',
                      disabled: (data) =>
                        data.id === values.channelId ||
                        (values.copiesChannelIds ?? []).includes(data.id) ||
                        ((data.children?.length ?? 0) > 0 && !(values.refersChannelIds ?? []).includes(data.id)),
                    }"
                    :render-after-expand="false"
                    show-checkbox
                    check-strictly
                    class="w-full"
                  />
                </el-form-item>

                <!-- 该功能会增加复杂性，但保留代码。特定客户需要时，可开启 -->
                <!--
                <el-form-item
                  v-if="asides['copies'].show"
                  prop="copiesChannelIds"
                  :rules="asides['copies'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['copies'].name ?? $t('article.copies')" :message="$t('article.copies.tooltip')" /></template>
                  <el-tree-select
                    v-model="values.copiesChannelIds"
                    :data="channelList"
                    node-key="id"
                    multiple
                    :props="{
                      label: 'name',
                      disabled: (data) =>
                        data.id === values.channelId ||
                        (values.refersChannelIds ?? []).includes(data.id) ||
                        ((data.children?.length ?? 0) > 0 && !(values.copiesChannelIds ?? []).includes(data.id)),
                    }"
                    :render-after-expand="false"
                    show-checkbox
                    check-strictly
                    class="w-full"
                  />
                </el-form-item>
                -->

                <el-form-item v-if="asides['org'].show" :label="asides['org'].name ?? $t('article.org')" :required="isEdit" label-position="top">
                  <el-tree-select
                    v-model="values.orgId"
                    :data="bean.org?.id != null && orgIds.indexOf(bean.org.id) === -1 ? [bean.org, ...orgList] : orgList"
                    node-key="id"
                    :props="{ label: 'name' }"
                    :render-after-expand="false"
                    :default-expanded-keys="(bean.org?.paths ?? orgList).map((it) => it.id)"
                    class="w-full"
                  />
                  <!-- <el-input :value="values.org?.name" disabled></el-input> -->
                </el-form-item>
                <el-form-item
                  v-if="asides['publishDate'].show"
                  prop="publishDate"
                  :rules="isEdit ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['publishDate'].name ?? $t('article.publishDate')" message="article.publishDate" help /></template>
                  <el-date-picker
                    v-model="values.publishDate"
                    type="datetime"
                    class="w-full"
                    @change="
                      (value: any) => {
                        // 清空日期值后，value会变成null，导致后台校验失败
                        if (value === null) values.publishDate = undefined;
                      }
                    "
                  ></el-date-picker>
                </el-form-item>
                <el-form-item
                  v-if="asides['onlineDate'].show"
                  prop="onlineDate"
                  :rules="asides['onlineDate'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['onlineDate'].name ?? $t('article.onlineDate')" message="article.onlineDate" help /></template>
                  <el-date-picker v-model="values.onlineDate" type="datetime" class="w-full"></el-date-picker>
                </el-form-item>
                <el-form-item
                  v-if="asides['offlineDate'].show"
                  prop="offlineDate"
                  :rules="asides['offlineDate'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['offlineDate'].name ?? $t('article.offlineDate')" message="article.offlineDate" help /></template>
                  <el-date-picker v-model="values.offlineDate" type="datetime" class="w-full"></el-date-picker>
                </el-form-item>
                <el-form-item
                  v-if="asides['articleTemplate'].show"
                  prop="articleTemplate"
                  :label="asides['articleTemplate'].name ?? $t('article.articleTemplate')"
                  :rules="asides['articleTemplate'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['articleTemplate'].name ?? $t('article.articleTemplate')" message="article.articleTemplate" help /></template>
                  <template-select v-model="values.articleTemplate" surface="article" :templates="articleTemplates" clearable />
                </el-form-item>
                <el-form-item
                  v-if="asides['articleStaticPath'].show"
                  prop="articleStaticPath"
                  :label="asides['articleStaticPath'].name ?? $t('article.articleStaticPath')"
                  :rules="asides['articleStaticPath'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['articleStaticPath'].name ?? $t('article.articleStaticPath')" message="article.articleStaticPath" help /></template>
                  <el-input v-model="values.articleStaticPath" maxlength="100"><template #append>.html</template></el-input>
                </el-form-item>
                <el-form-item prop="aiGenerated" label-position="top">
                  <template #label><label-tip message="article.aiGenerated" help /></template>
                  <el-switch v-model="values.aiGenerated" @change="(checked: any) => handleAiGeneratedChange(checked === true)"></el-switch>
                </el-form-item>
                <!--
                <el-form-item
                  v-if="asides['allowComment'].show"
                  prop="allowComment"
                  :rules="asides['allowComment'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['allowComment'].name ?? $t('article.allowComment')" message="article.allowComment" help /></template>
                  <el-switch v-model="values.allowComment"></el-switch>
                </el-form-item>
                -->
                <el-form-item v-if="asides['user'].show" :label="asides['user'].name ?? $t('article.user')" label-position="top">
                  <el-input :model-value="bean.user?.username" disabled></el-input>
                </el-form-item>
                <el-form-item v-if="asides['created'].show" :label="asides['created'].name ?? $t('article.created')" label-position="top">
                  <el-date-picker :model-value="bean.created" type="datetime" class="w-full" disabled></el-date-picker>
                </el-form-item>
                <el-form-item v-if="asides['modifiedUser'].show" :label="asides['modifiedUser'].name ?? $t('article.modifiedUser')" label-position="top">
                  <el-input :model-value="bean.modifiedUser?.username" disabled></el-input>
                </el-form-item>
                <el-form-item v-if="asides['modified'].show" :label="asides['modified'].name ?? $t('article.modified')" label-position="top">
                  <el-date-picker :model-value="bean.modified" type="datetime" class="w-full" disabled></el-date-picker>
                </el-form-item>
                <!-- 只读信息取自 bean（服务端现值），不取表单：复制文章时表单里还带着源文章的推送列表，
                     新文章保存后留在对话框里时表单里则根本没有 -->
                <el-form-item v-if="isEdit && bean.destList?.length > 0" :label="$t('article.destList')" label-position="top">
                  <el-tooltip
                    v-for="item in bean.destList"
                    :key="item.id"
                    :content="(item.site?.id != bean.siteId ? `[${item.site.name}] ` : '') + item.channel.paths.map((it: any) => it.name).join('/')"
                    placement="top-start"
                  >
                    <el-input :model-value="(item.site?.id != bean.siteId ? `[${item.site.name}] ` : '') + item.channel.paths.map((it: any) => it.name).join('/')" disabled>
                      <template #append>{{ t(`article.type.${item.type}`) }}</template>
                    </el-input>
                  </el-tooltip>
                </el-form-item>
            </el-card>
          </el-col>
          <!-- 翻译（multilingual P5）：当前站点在语言组内时显示；保存独立于主表单。
               channel-slug 用于新增译文对话框预选目标站点的同别名栏目 -->
          <el-col v-if="isEdit && values.id != null && !perm('article:show')" :span="24">
            <article-i18n-panel :article-id="values.id" :channel-slug="bean.channel?.slug" />
          </el-col>
        </el-row>
      </template>
      <template #footer-action="{ isEdit, bean, handleSubmit }">
        <el-button :disabled="perm(isEdit ? 'article:update' : 'article:create')" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
          {{ $t(isEdit ? 'save' : 'submit') }}<span v-if="isEdit" class="ml-1 opacity-70">({{ saveHint }})</span>
        </el-button>
        <!-- 改完草稿、退回稿直接发出去：先保存、再提交，一步完成 -->
        <el-button v-if="isEdit && !isReview && canSubmit(bean)" :disabled="perm('article:update') || perm('article:submit')" @click="() => handleSaveAndSubmit()">
          {{ $t('article.op.saveAndSubmit') }}
        </el-button>
        <el-button v-if="!isEdit" :disabled="perm('article:create')" @click="() => handleSaveAsDraft()">
          {{ $t('article.op.saveAsDraft') }}<span class="ml-1 opacity-70">({{ saveHint }})</span>
        </el-button>
      </template>
    </dialog-form>
    <image-extractor v-model="imageExtractorVisible" :urls="imageExtractorUrls" :append-to-body="true" @finished="(urls) => (values.image = urls[0])" />
    <el-dialog v-model="titleSimilarityVisible" :title="$t('article.op.titleSimilarity')" width="820px">
      <el-table :data="titleSimilarityList">
        <el-table-column property="id" label="ID" width="200" />
        <el-table-column property="title" :label="$t('article.similarity.title')" show-overflow-tooltip />
        <el-table-column
          property="channel.name"
          :label="$t('article.channel')"
          :formatter="(row) => row.channel.paths.map((it) => it.name).join(' / ')"
          width="180px"
          show-overflow-tooltip
        />
        <el-table-column property="score" :label="$t('article.similarity.score')" :formatter="(row) => $n(row.score, 'percent', { minimumFractionDigits: 0 })" width="100px" />
      </el-table>
    </el-dialog>
    <review-form-properties v-model="formPropertiesVisible" v-model:form-properties="formProperties" @finished="submitPass" />
    <review-delegate v-model="delegateVisible" @finished="submitDelegate" />
    <review-transfer v-model="transferVisible" @finished="submitTransfer" />
    <review-back v-model="backVisible" :task-id @finished="submitBack" />
    <el-drawer
      v-model="aiScreenVisible"
      :title="$t(aiScreenMode === 'save' ? 'article.wordCheck.title' : 'article.op.aiScreen')"
      :size="480"
      append-to-body
      @closed="() => handleScreenClosed()"
    >
      <el-alert v-if="aiScreenBlockCount > 0" type="error" :closable="false" class="mb-3" :title="$t('article.ai.screen.blockSummary', { count: aiScreenBlockCount })" />
      <el-alert
        v-else-if="aiScreenMode === 'save' && aiScreenFindings.length > 0"
        type="warning"
        :closable="false"
        class="mb-3"
        :title="$t('article.wordCheck.errorSummary', { count: aiScreenFindings.length })"
      />
      <el-alert v-if="aiScreenSemanticError" type="warning" :closable="false" class="mb-3" :title="$t('article.ai.screen.semanticError', { error: aiScreenSemanticError })" />
      <el-empty v-if="aiScreenFindings.length === 0" :description="$t('article.ai.screen.clean')" />
      <div v-for="group in aiScreenGroups" :key="group.key" class="mb-4">
        <div class="mb-2 font-bold">{{ $t(`article.ai.screen.group.${group.key}`) }}（{{ group.findings.length }}）</div>
        <div
          v-for="(finding, index) in group.findings"
          :key="index"
          :class="['p-2 mb-2 border rounded', canLocateFinding(finding) ? 'cursor-pointer hover:bg-gray-50' : '']"
          @click="() => locateFinding(finding)"
        >
          <div class="mb-1">
            <el-tag v-if="finding.severity === 'block'" type="danger" size="small" disable-transitions>{{ finding.word ?? $t(`article.ai.screen.group.${group.key}`) }}</el-tag>
            <el-tag v-else-if="finding.severity === 'warn'" type="warning" size="small" disable-transitions>
              {{ finding.word ?? $t(`article.ai.screen.group.${group.key}`) }}
            </el-tag>
            <el-tag v-else type="info" size="small" disable-transitions>{{ finding.word ?? $t(`article.ai.screen.group.${group.key}`) }}</el-tag>
            <!-- 标题、摘要等字段的命中标出所在字段；正文命中无法定位时（Markdown、语义片段未找到）给出提示 -->
            <el-tag v-if="findingField(finding) !== 'text'" size="small" type="info" effect="plain" class="ml-1" disable-transitions>{{ findingFieldLabel(finding) }}</el-tag>
            <el-tag v-else-if="!canLocateFinding(finding)" size="small" type="info" effect="plain" class="ml-1" disable-transitions>
              {{ $t('article.ai.screen.locateFailed') }}
            </el-tag>
          </div>
          <div class="text-sm break-all">{{ finding.snippet }}</div>
          <!-- 词库命中不带说明文字，按类型本地化；语义命中的说明来自模型 -->
          <div class="mt-1 text-xs text-gray-500">{{ finding.message ?? $t(`article.ai.screen.message.${finding.type}`) }}</div>
          <div v-if="finding.suggestion" class="mt-1 text-xs text-gray-500">
            {{ $t('article.ai.screen.suggestion') }}{{ $t('punct.colon') }}{{ finding.suggestion }}
            <el-button v-if="canFixFinding(finding)" link type="primary" size="small" class="ml-2" @click.stop="() => fixFinding(finding)">
              {{ $t('article.ai.screen.fix') }}
            </el-button>
          </div>
        </div>
      </div>
      <template v-if="aiScreenMode === 'save' || aiScreenFixableCount > 1" #footer>
        <div class="flex items-center">
          <el-button v-if="aiScreenFixableCount > 0" @click="() => fixAllFindings()">{{ $t('article.wordCheck.fixAll') }}</el-button>
          <div v-if="aiScreenMode === 'save'" class="ml-auto">
            <el-button @click="() => finishSaveCheck(true)">{{ $t('article.wordCheck.back') }}</el-button>
            <!-- 敏感词在保存时会被服务端拦截，不提供忽略 -->
            <el-button v-if="aiScreenBlockCount === 0" type="primary" @click="() => finishSaveCheck(false)">{{ $t('article.wordCheck.ignoreAndSave') }}</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
    <el-dialog v-model="aiMetaOverwriteVisible" :title="$t('article.ai.meta.overwriteTitle')" :width="640" append-to-body @close="handleAiMetaOverwriteClose">
      <div class="mb-2 text-sm text-gray-500">{{ $t('article.ai.meta.overwriteTip') }}</div>
      <div v-for="item in aiMetaOverwriteItems" :key="item.target" class="py-2 border-b">
        <el-checkbox v-model="item.apply">{{ item.label }}</el-checkbox>
        <div class="mt-1 text-xs text-gray-400 break-all">{{ $t('article.ai.meta.oldValue') }}{{ $t('punct.colon') }}{{ item.oldDisplay }}</div>
        <div class="mt-1 text-sm break-all">{{ $t('article.ai.meta.newValue') }}{{ $t('punct.colon') }}{{ item.newDisplay }}</div>
      </div>
      <template #footer>
        <el-button @click="() => (aiMetaOverwriteVisible = false)">{{ $t('cancel') }}</el-button>
        <el-button type="primary" @click="() => handleAiMetaOverwrite()">{{ $t('ok') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="aiTitlesVisible" :title="$t('article.ai.titles.dialogTitle')" :width="640" append-to-body>
      <div v-if="values.title" class="mb-2 text-sm text-gray-500 break-all">{{ $t('article.ai.titles.current') }}{{ $t('punct.colon') }}{{ values.title }}</div>
      <div v-for="(title, index) in aiTitlesList" :key="index" class="flex items-center py-2 border-b">
        <div class="flex-1 min-w-0 break-all">{{ title }}</div>
        <el-button link type="primary" size="small" class="ml-2" @click="() => applyAiTitle(title)">{{ $t('article.ai.titles.use') }}</el-button>
      </div>
      <template #footer>
        <el-button :loading="aiTitlesLoading" @click="() => handleAiTitles()">{{ $t('article.ai.titles.regenerate') }}</el-button>
        <el-button @click="() => (aiTitlesVisible = false)">{{ $t('cancel') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="aiSlugsVisible" :title="$t('article.ai.slug.title')" :width="480" append-to-body>
      <div v-for="(slug, index) in aiSlugsList" :key="index" class="flex items-center py-2 border-b">
        <div class="flex-1 min-w-0 break-all">{{ slug }}</div>
        <el-button link type="primary" size="small" class="ml-2" @click="() => applyAiSlug(slug)">{{ $t('article.ai.slug.use') }}</el-button>
      </div>
      <template #footer>
        <el-button :loading="aiSlugsLoading" @click="() => handleAiSlugs()">{{ $t('article.ai.slug.regenerate') }}</el-button>
        <el-button @click="() => (aiSlugsVisible = false)">{{ $t('cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped></style>

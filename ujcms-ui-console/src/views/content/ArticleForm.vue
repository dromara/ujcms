<script setup lang="ts">
import { computed, ref, toRefs, watch, onMounted, PropType } from 'vue';
import { Finished, Box, DocumentRemove, Delete, CircleCheck, CircleClose, View, Back, SetUp, Compass } from '@element-plus/icons-vue';
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
  archiveArticle,
  offlineArticle,
  deleteArticle,
  completelyDeleteArticle,
  rejectArticle,
  delegateArticle,
  transferArticle,
  backArticle,
  queryChannelList,
  queryArticleTemplates,
  queryArticleTitleSimilarity,
} from '@/api/content';
import { queryModelList } from '@/api/config';
import { validateErrorWord, validateSensitiveWord, queryTaskFormProperties } from '@/api/system';
import { jodConvertDocUrl, jodConvertLibraryUrl, queryJodConvertEnabled, queryDictListByAlias, queryTagList } from '@/api/content';
import { queryOrgList, queryOrgPermissions } from '@/api/user';
import { toTree, disableTreeWithPermission } from '@/utils/tree';
import { formatDuration } from '@/utils/common';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import FieldItem from '@/views/config/components/FieldItem.vue';
import DialogForm from '@/components/DialogForm.vue';
import Tinymce from '@/components/Tinymce';
import { TuiEditor } from '@/components/TuiEditor';
import LabelTip from '@/components/LabelTip.vue';
import { BaseUpload, ImageUpload, ImageListUpload, FileListUpload } from '@/components/Upload';
import ImageExtractor from './components/ImageExtractor.vue';
import { openArticleLink } from './components/articleUtils';
import ReviewFormProperties from './components/ReviewFormProperties.vue';
import ReviewDelegate from './components/ReviewDelegate.vue';
import ReviewTransfer from './components/ReviewTransfer.vue';
import ReviewBack from './components/ReviewBack.vue';

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
const tinyText = ref<any>();
const jodConverterEnabled = ref<boolean>(false);
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
const fields = computed(() => JSON.parse(articleModel.value?.customs || '[]').filter((item: any) => item.type !== 'tinyEditor'));
const editorFields = computed(() => JSON.parse(articleModel.value?.customs || '[]').filter((item: any) => item.type === 'tinyEditor'));

const tagLoading = ref<boolean>(false);
const tagList = ref<any[]>([]);
const tagRemoteMethod = async (query?: string) => {
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
    fetchArticleTemplates();
    fetchJodConverterEnabled();
  }
});
watch(fields, () => {
  initCustoms(values.value.customs);
});
const fetchJodConverterEnabled = async () => {
  if (currentUser.epRank > 0) {
    jodConverterEnabled.value = await queryJodConvertEnabled();
  }
};
const fetchSourceList = (name: string) => queryDictListByAlias('sys_article_source', name);
const fetchOrgList = async () => {
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
const fetchArticleModeList = async () => {
  articleModelList.value = await queryModelList({ type: 'article' });
};
const fetchArticleTemplates = async () => {
  articleTemplates.value = await queryArticleTemplates();
};
const initCustoms = (customs: any) => {
  fields.value.forEach((field: any) => {
    if (customs[field.code] == null) {
      customs[field.code] = field.defaultValue;
      if (field.defaultValueKey != null) {
        customs[field.code + 'Key'] = field.defaultValueKey;
      }
    }
  });
  return customs;
};

const preventSubmit = async (bean: any) => {
  if (currentUser.epRank <= 1) {
    return false;
  }

  const errorWordData = await validateErrorWord({ text: bean.text });
  if (errorWordData.status === -1) {
    if (bean.editorType === 1) {
      const searchreplace = tinyText.value.editor.plugins.searchreplace;
      searchreplace.find(errorWordData.result.name);
      searchreplace.done();
      ElMessageBox.confirm(t('article.error.errorWord.confirm', { name: errorWordData.result.name, correct: errorWordData.result.correct }), {
        cancelButtonText: t('cancel'),
        confirmButtonText: t('ok'),
        type: 'warning',
        callback: (action: string) => {
          if (action === 'confirm') {
            searchreplace.find(errorWordData.result.name);
            searchreplace.replace(errorWordData.result.correct, false, true);
            searchreplace.done();
            values.value.text = tinyText.value.editor.getContent();
          }
        },
      });
    } else if (bean.editorType === 2) {
      ElMessageBox.alert(t('article.error.errorWord.alert.message', { name: errorWordData.result.name }), t('article.error.errorWord.alert.title'), {
        confirmButtonText: t('ok'),
      });
    }
    return true;
  }
  const sensitiveWordData = await validateSensitiveWord({ text: bean.text });
  if (sensitiveWordData.status === -1) {
    if (bean.editorType === 1) {
      const searchreplace = tinyText.value.editor.plugins.searchreplace;
      searchreplace.find(sensitiveWordData.result.name);
      searchreplace.done();
    }
    ElMessageBox.alert(t('article.error.sensitiveWord.alert.message', { name: sensitiveWordData.result.name }), t('article.error.sensitiveWord.alert.title'), {
      confirmButtonText: t('ok'),
    });
    return true;
  }
  return false;
};

const handleSaveAsDraft = () => {
  dialog.value.submit(async (values: any, { focus, emit, props, form }: { focus: any; emit: any; props: any; form: any }) => {
    // 草稿
    values.status = 10;
    await createArticle(values);
    focus?.focus?.();
    emit('update:values', props.initValues(values.value));
    form.resetFields();
    ElMessage.success(t('success'));
  });
};

const taskId = ref<string>('');
const formProperties = ref<any[]>([]);
const formPropertiesVisible = ref<boolean>(false);
const backVisible = ref<boolean>(false);
const delegateVisible = ref<boolean>(false);
const transferVisible = ref<boolean>(false);

const editorSave = async (event: KeyboardEvent) => {
  if (event.ctrlKey && event.key.toLowerCase() === 's') {
    event.preventDefault();
    event.stopPropagation();
    dialog.value.defaultSubmit(true);
  }
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
  try {
    const { value } = await ElMessageBox.prompt(t('review.rejectReason'), t('review.reject'), {
      confirmButtonText: t('submit'),
      cancelButtonText: t('cancel'),
      inputPattern: /\S+/,
      inputErrorMessage: t('v.required'),
    });
    dialog.value.remove(async () => {
      await rejectArticle([bean.taskId], value);
    });
  } catch {
    /* empty */
  }
};

const handleExecute = (action: string, id: string) => {
  dialog.value.submit(async (values: any, { loadBean }: { loadBean: () => Promise<any> }) => {
    if (action === 'delete') {
      await deleteArticle([id]);
    } else if (action === 'submit') {
      await submitArticle([id]);
    } else if (action === 'archive') {
      await archiveArticle([id]);
    } else if (action === 'offline') {
      await offlineArticle([id]);
    } else {
      throw new Error(`not support action: ${action}`);
    }
    loadBean();
  });
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
</script>

<template>
  <div>
    <dialog-form
      ref="dialog"
      v-model:values="values"
      :name="$t('menu.content.article')"
      :query-bean="isReview ? queryArticleReview : queryArticle"
      :create-bean="createArticle"
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
            customs: initCustoms({}),
            fileList: [],
            imageList: [],
          };
        }
      "
      :to-values="(bean) => ({ ...bean, tagNames: bean.tags.map((item) => item.name) })"
      perms="article"
      :model-value="modelValue"
      :addable="!isReview"
      :disable-edit="(bean) => bean.id != null && !bean.editable && !currentUser.allStatusPermission && !isReview"
      :prevent-submit="preventSubmit"
      label-width="140px"
      large
      @update:model-value="(event) => $emit('update:modelValue', event)"
      @finished="() => $emit('finished')"
      @bean-change="
        (bean) => {
          showSubtitle = !!bean.subtitle;
          showFullTitle = !!bean.fullTitle;
          showLinkUrl = !!bean.linkUrl;
          articleModelId = bean.channel?.articleModelId ?? channel?.articleModelId;
        }
      "
    >
      <template #header-action="{ isEdit, bean, unsaved, handleDelete }">
        <el-button-group v-if="isReview">
          <el-button v-if="isEdit && isReview" :disabled="perm('articleReview:pass') || unsaved" type="primary" :icon="CircleCheck" @click="() => handlePass(bean)">
            {{ $t('review.pass') }}
          </el-button>
          <el-button :disabled="perm('articleReview:reject') || unsaved" :icon="CircleClose" @click="() => handleReject(bean)">{{ $t('review.reject') }}</el-button>
          <el-button v-if="currentUser.epRank >= 3" :disabled="perm('articleReview:back') || unsaved" :icon="Back" @click="() => handleBack(bean)">
            {{ $t('review.back') }}
          </el-button>
          <el-button v-if="currentUser.epRank >= 3" :disabled="perm('articleReview:delegate') || unsaved" :icon="SetUp" @click="() => handleDelegate(bean)">
            {{ $t('review.delegate') }}
          </el-button>
          <el-button v-if="currentUser.epRank >= 3" :disabled="perm('articleReview:transfer') || unsaved" :icon="Compass" @click="() => handleTransfer(bean)">
            {{ $t('review.transfer') }}
          </el-button>
        </el-button-group>
        <el-button-group v-if="!isReview">
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('submit', bean.id)">
            <template #reference>
              <el-button :disabled="[0, 12].includes(bean.status) || perm('article:submit') || unsaved" :icon="Finished">{{ $t('article.op.submit') }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('archive', bean.id)">
            <template #reference>
              <el-button :disabled="![0].includes(bean.status) || perm('article:archive') || unsaved" :icon="Box">{{ $t('article.op.archive') }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="isEdit" :title="$t('confirmExecute')" @confirm="() => handleExecute('offline', bean.id)">
            <template #reference>
              <el-button :disabled="![0, 1, 5, 11, 12].includes(bean.status) || perm('article:offline') || unsaved" :icon="CircleClose">{{ $t('article.op.offline') }}</el-button>
            </template>
          </el-popconfirm>
        </el-button-group>
        <el-button v-if="isEdit" :icon="View" @click="() => openArticleLink(values.status, values.url, values.dynamicUrl)">{{ $t('article.op.preview') }}</el-button>
        <el-button-group>
          <el-popconfirm v-if="isEdit && !isReview" :title="$t('confirmDelete')" @confirm="() => handleExecute('delete', bean.id)">
            <template #reference>
              <el-button :disabled="perm('article:delete') || unsaved" :icon="DocumentRemove">{{ $t('delete') }}</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="isEdit" :title="$t('confirmCompletelyDelete')" @confirm="() => handleDelete()">
            <template #reference>
              <el-button :disabled="perm('article:completelyDelete')" :icon="Delete">{{ $t('article.op.completelyDelete') }}</el-button>
            </template>
          </el-popconfirm>
        </el-button-group>
      </template>
      <template #header-status="{ isEdit }">
        <template v-if="isEdit">
          <span v-if="values.status != null">
            <el-tag v-if="values.status === 0" type="success" size="small" disable-transitions>{{ $t(`article.status.${values.status}`) }}</el-tag>
            <el-tag v-else-if="values.status === 1" size="small" disable-transitions>{{ $t(`article.status.${values.status}`) }}</el-tag>
            <el-tag v-else-if="values.status === 20" type="danger" size="small" disable-transitions>{{ $t(`article.status.${values.status}`) }}</el-tag>
            <el-tag v-else-if="[21, 22].includes(values.status)" type="warning" size="small" disable-transitions>{{ $t(`article.status.${values.status}`) }}</el-tag>
            <el-tag v-else type="info" size="small" disable-transitions>{{ $t(`article.status.${values.status}`) }}</el-tag>
          </span>
        </template>
      </template>
      <template #default="{ bean, isEdit, disabled }">
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
                  <el-input ref="focus" v-model="values.title" maxlength="150"></el-input>
                  <el-button :disabled="!values.title" class="mr-5" @click="() => titleSimilarity(values.title, values.id)">{{ $t('article.op.titleSimilarity') }}</el-button>
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
                    class="w-full"
                    @focus.once="() => tagRemoteMethod()"
                  >
                    <el-option v-for="item in tagList.map((it) => it.name)" :key="item" :label="item" :value="item" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['seoKeywords'].show" :span="mains['seoKeywords'].double ? 12 : 24">
                <el-form-item prop="seoKeywords" :rules="mains['seoKeywords'].required ? { required: true, message: () => $t('v.required') } : undefined">
                  <template #label><label-tip :label="mains['seoKeywords'].name ?? $t('article.seoKeywords')" message="article.seoKeywords" help /></template>
                  <el-input v-model="values.seoKeywords" maxlength="150"></el-input>
                </el-form-item>
              </el-col>
              <el-col v-if="mains['seoDescription'].show" :span="mains['seoDescription'].double ? 12 : 24">
                <el-form-item
                  prop="seoDescription"
                  :label="mains['seoDescription'].name ?? $t('article.seoDescription')"
                  :rules="mains['seoDescription'].required ? { required: true, message: () => $t('v.required') } : undefined"
                >
                  <el-input v-model="values.seoDescription" type="textarea" :rows="3" maxlength="1000"></el-input>
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
                        <template #append> {{ formatDuration(values.videoDuration) }}</template>
                      </el-input>
                    </el-col>
                  </el-row>
                  <base-upload
                    type="video"
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
                        <template #append> {{ formatDuration(values.audioDuration) }}</template>
                      </el-input>
                    </el-col>
                  </el-row>
                  <base-upload
                    type="audio"
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
                      v-if="jodConverterEnabled && currentUser.epRank > 0"
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
                    <el-alert v-if="!jodConverterEnabled && currentUser.epRank > 0" :title="$t('error.jodConverterNotEnabled')" type="error" :closable="false" />
                    <el-button v-if="currentUser.epRank === 0" type="primary" @click="() => $alert($t('error.enterprise'), { dangerouslyUseHTMLString: true })">
                      {{ $t('article.op.clickToUpload') }}
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
                    <el-radio-group v-if="mains['text'].editorSwitch" v-model="values.editorType" class="mr-6" @change="() => (values.markdown = '')">
                      <el-radio v-for="n in [1, 2]" :key="n" :value="n">{{ $t(`model.field.editorType.${n}`) }}</el-radio>
                    </el-radio-group>
                    <div v-if="values.editorType === 1 && jodConverterEnabled && currentUser.epRank > 0" class="inline-flex mb-1">
                      <base-upload
                        type="doc"
                        :disabled="perm('jodConvert:doc')"
                        :upload-action="jodConvertDocUrl"
                        :on-success="(res: string) => (values.text = res)"
                        :button="$t('article.op.docImport')"
                      ></base-upload>
                    </div>
                    <div v-if="currentUser.epRank === 0 && currentUser.epDisplay" class="inline-flex mb-1">
                      <el-button type="primary" @click="() => $alert($t('error.enterprise'), { dangerouslyUseHTMLString: true })">{{ $t('article.op.docImport') }}</el-button>
                    </div>
                    <tui-editor
                      v-if="values.editorType === 2"
                      v-model="values.markdown"
                      v-model:html="values.text"
                      class="leading-6"
                      @keydown="(editorType, event) => editorSave(event)"
                    />
                    <tinymce
                      v-else
                      ref="tinyText"
                      v-model="values.text"
                      :disabled="disabled"
                      :init="{ typesetting_options: currentSiteStore.currentSite.editor?.typesetting }"
                      @keydown="editorSave"
                    />
                  </div>
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
            <el-tabs type="border-card" class="ml-5">
              <el-tab-pane :label="$t('article.tabs.setting')">
                <el-form-item
                  prop="channelId"
                  :label="asides['channel'].name ?? $t('article.channel')"
                  :rules="[
                    { required: true, message: () => $t('v.required') },
                    {
                      validator: (rule, value, callback) => {
                        if (channelList.find((it) => it.id === value)?.children.length > 0) {
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
                      }
                    "
                  />
                </el-form-item>
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
                  v-if="asides['source'].show"
                  prop="source"
                  :label="asides['source'].name ?? $t('article.source')"
                  :rules="asides['source'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
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
                <el-form-item
                  v-if="asides['articleTemplate'].show"
                  prop="articleTemplate"
                  :label="asides['articleTemplate'].name ?? $t('article.articleTemplate')"
                  :rules="asides['articleTemplate'].required ? { required: true, message: () => $t('v.required') } : undefined"
                  label-position="top"
                >
                  <template #label><label-tip :label="asides['articleTemplate'].name ?? $t('article.articleTemplate')" message="article.articleTemplate" help /></template>
                  <el-select v-model="values.articleTemplate" clearable class="w-full">
                    <el-option v-for="item in articleTemplates" :key="item" :label="item + '.html'" :value="item"></el-option>
                  </el-select>
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
                  <el-input :model-value="values.user?.username" disabled></el-input>
                </el-form-item>
                <el-form-item v-if="asides['created'].show" :label="asides['created'].name ?? $t('article.created')" label-position="top">
                  <el-date-picker :model-value="values.created" type="datetime" class="w-full" disabled></el-date-picker>
                </el-form-item>
                <el-form-item v-if="asides['modifiedUser'].show" :label="asides['modifiedUser'].name ?? $t('article.modifiedUser')" label-position="top">
                  <el-input :model-value="values.modifiedUser?.username" disabled></el-input>
                </el-form-item>
                <el-form-item v-if="asides['modified'].show" :label="asides['modified'].name ?? $t('article.modified')" label-position="top">
                  <el-date-picker :model-value="values.modified" type="datetime" class="w-full" disabled></el-date-picker>
                </el-form-item>
                <el-form-item v-if="values.destList?.length > 0" :label="$t('article.destList')" label-position="top">
                  <el-tooltip
                    v-for="item in values.destList"
                    :key="item.id"
                    :content="(item.siteId != values.siteId ? `[${item.site.name}] ` : '') + item.channel.paths.map((it: any) => it.name).join('/')"
                    placement="top-start"
                  >
                    <el-input :model-value="(item.siteId != values.siteId ? `[${item.site.name}] ` : '') + item.channel.paths.map((it: any) => it.name).join('/')" disabled>
                      <template #append>{{ t(`article.type.${item.type}`) }}</template>
                    </el-input>
                  </el-tooltip>
                </el-form-item>
              </el-tab-pane>
            </el-tabs>
          </el-col>
        </el-row>
      </template>
      <template #footer-action="{ isEdit, handleSubmit }">
        <el-button :disabled="perm(isEdit ? 'article:update' : 'article:create')" type="primary" native-type="submit" @click.prevent="() => handleSubmit()">
          {{ $t(isEdit ? 'save' : 'submit') }}
        </el-button>
        <el-button v-if="!isEdit" @click="() => handleSaveAsDraft()">{{ $t('article.op.saveAsDraft') }}</el-button>
      </template>
    </dialog-form>
    <image-extractor v-model="imageExtractorVisible" :urls="imageExtractorUrls" :append-to-body="true" @finished="(urls) => (values.image = urls[0])" />
    <el-dialog v-model="titleSimilarityVisible" :title="$t('article.op.titleSimilarity')" width="820px">
      <el-table :data="titleSimilarityList">
        <el-table-column property="id" label="ID" width="170px" />
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
  </div>
</template>

<style lang="scss" scoped></style>

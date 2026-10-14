<script setup lang="ts">
import { ref, watch, toRefs, computed } from 'vue';
import type { PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import draggable from 'vuedraggable';
import { Rank, Close } from '@element-plus/icons-vue';
import { queryArticle } from '@/api/content';
import { previewWechatArticle, pushWechatArticle, queryWechatAccountList } from '@/api/wechat';
import WechatPreviewDialog from '@/views/wechat/WechatPreviewDialog.vue';
import { loadOrClose } from '@/utils/common';

defineOptions({
  name: 'WechatPushForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  // 文章ID列表，按列表顺序合并为一篇图文草稿（第一篇为头条）；单篇即单图文
  beanIds: { type: Array as PropType<string[]>, default: () => [] },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

// 微信一篇图文草稿最多包含的文章数（与后端 WechatPushService.MAX_ARTICLES 一致）
const MAX_ARTICLES = 8;
// 微信图文各字段的长度限制
const TITLE_MAX = 64;
const AUTHOR_MAX = 8;
const DIGEST_MAX = 120;

const { beanIds, modelValue: visible } = toRefs(props);
const { t } = useI18n();
const form = ref<any>();
const items = ref<any[]>([]);
const values = ref<any>({});
const accountList = ref<any[]>([]);
const loading = ref<boolean>(false);
const buttonLoading = ref<boolean>(false);
const previewVisible = ref<boolean>(false);
const previewLoading = ref<string | null>(null);
const preview = ref<any>({});

// 不满足推送条件的原因：未发布显示其状态名，无封面单独提示
const problem = (item: any): string | undefined => {
  if (item.status !== 0) return t(`article.status.${item.status}`);
  if (!item.image) return t('wechatArticle.push.noCover');
  return undefined;
};
// 封面候选：文章封面、图片集、正文图片（保持存储中的相对地址）
const coverCandidates = (article: any): string[] => {
  const urls: string[] = [];
  const push = (url?: string | null) => {
    if (url && !urls.includes(url)) urls.push(url);
  };
  push(article.image);
  (article.imageList ?? []).forEach((image: any) => push(image.url));
  new DOMParser()
    .parseFromString(article.text ?? '', 'text/html')
    .querySelectorAll('img[src]')
    .forEach((img) => push(img.getAttribute('src')));
  return urls;
};
const toItem = (article: any) => {
  const item: any = {
    id: article.id,
    status: article.status,
    // 推送时使用的公众号侧字段，初始为文章原值（按微信限制截断），修改只影响本次推送
    title: (article.title ?? '').slice(0, TITLE_MAX),
    author: (article.author ?? '').slice(0, AUTHOR_MAX),
    digest: (article.description ?? '').slice(0, DIGEST_MAX),
    image: article.image ?? undefined,
    covers: coverCandidates(article),
    expanded: false,
  };
  item.problem = problem(item);
  return item;
};

watch(visible, async () => {
  if (!visible.value) return;
  loading.value = true;
  try {
    const articles = await loadOrClose(() => Promise.all((beanIds.value ?? []).map((id) => queryArticle(id))), () => emit('update:modelValue', false));
    items.value = articles.map(toItem);
    accountList.value = await queryWechatAccountList();
    values.value = { accountId: accountList.value.length > 0 ? accountList.value[0].id : undefined, publish: false };
  } finally {
    loading.value = false;
  }
});

const removeItem = (index: number) => {
  items.value.splice(index, 1);
};
const selectCover = (item: any, url: string) => {
  item.image = url;
  item.problem = problem(item);
};
const blocker = computed<string | undefined>(() => {
  if (items.value.length <= 0) return t('wechatArticle.push.empty');
  if (items.value.length > MAX_ARTICLES) return t('wechatArticle.push.tooMany', { max: MAX_ARTICLES });
  if (items.value.some((item) => item.problem)) return t('wechatArticle.push.ineligible');
  return undefined;
});
const toParams = (item: any) => ({ id: item.id, title: item.title, author: item.author, digest: item.digest, image: item.image });
const accountName = computed(() => accountList.value.find((item) => item.id === values.value.accountId)?.name ?? '');

const handlePreview = async (item: any) => {
  if (!values.value.accountId) return;
  previewLoading.value = item.id;
  try {
    preview.value = await previewWechatArticle({ accountId: values.value.accountId, ...toParams(item) });
    previewVisible.value = true;
  } finally {
    previewLoading.value = null;
  }
};

const handleSubmit = () => {
  form.value.validate(async (valid: boolean) => {
    if (!valid || blocker.value) return;
    buttonLoading.value = true;
    try {
      await pushWechatArticle({ items: items.value.map(toParams), accountId: values.value.accountId, publish: values.value.publish });
      emit('finished');
      emit('update:modelValue', false);
      ElMessage.success(t('success'));
    } finally {
      buttonLoading.value = false;
    }
  });
};
</script>

<template>
  <el-drawer :title="$t('article.op.wechatPush')" :model-value="modelValue" :size="560" @update:model-value="(event) => $emit('update:modelValue', event)">
    <template #default>
      <el-form ref="form" v-loading="loading" :model="values" label-position="top">
        <el-form-item prop="accountId" :label="$t('wechatArticle.account')" :rules="[{ required: true, message: () => $t('v.required') }]">
          <el-select v-model="values.accountId" class="w-full">
            <el-option v-for="item in accountList" :key="item.id" :label="item.name" :value="item.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('wechatArticle.push.articles')">
          <div class="w-full">
            <draggable :list="items" item-key="id" handle=".drag-handle" class="space-y-1">
              <template #item="{ element, index }">
                <div class="rounded border px-2 py-1" :class="element.problem ? 'border-danger' : 'border-gray-200'">
                  <div class="flex items-center">
                    <el-icon class="drag-handle cursor-move mr-2 text-gray-secondary"><Rank /></el-icon>
                    <span class="mr-2 text-xs text-gray-secondary">{{ index + 1 }}</span>
                    <el-tag v-if="index === 0 && items.length > 1" size="small" class="mr-2">{{ $t('wechatArticle.push.headline') }}</el-tag>
                    <span class="flex-1 truncate text-sm" :title="element.title">{{ element.title }}</span>
                    <el-tag v-if="element.problem" size="small" type="danger" class="ml-2">{{ element.problem }}</el-tag>
                    <el-button link type="primary" size="small" class="ml-2" @click="() => (element.expanded = !element.expanded)">
                      {{ element.expanded ? $t('wechatArticle.push.collapse') : $t('wechatArticle.push.edit') }}
                    </el-button>
                    <el-button link :icon="Close" class="ml-1" @click="() => removeItem(index)" />
                  </div>
                  <div v-if="element.expanded" class="mt-2 space-y-2 border-t border-gray-200 pt-2">
                    <div class="flex items-center">
                      <span class="w-12 text-xs text-gray-secondary">{{ $t('wechatArticle.push.title') }}</span>
                      <el-input v-model="element.title" size="small" :maxlength="TITLE_MAX" show-word-limit class="flex-1"></el-input>
                    </div>
                    <div class="flex items-center">
                      <span class="w-12 text-xs text-gray-secondary">{{ $t('wechatArticle.push.author') }}</span>
                      <el-input v-model="element.author" size="small" :maxlength="AUTHOR_MAX" show-word-limit class="w-48"></el-input>
                    </div>
                    <div class="flex items-start">
                      <span class="w-12 pt-1 text-xs text-gray-secondary">{{ $t('wechatArticle.push.digest') }}</span>
                      <div class="flex-1">
                        <el-input v-model="element.digest" type="textarea" :rows="3" resize="none" size="small" :maxlength="DIGEST_MAX" show-word-limit></el-input>
                        <div v-if="items.length > 1 && index > 0" class="mt-1 text-xs text-gray-secondary">{{ $t('wechatArticle.push.digest.headlineOnly') }}</div>
                      </div>
                    </div>
                    <div class="flex items-start">
                      <span class="w-12 pt-1 text-xs text-gray-secondary">{{ $t('wechatArticle.push.cover') }}</span>
                      <div class="flex flex-1 flex-wrap gap-2">
                        <div
                          v-for="url in element.covers"
                          :key="url"
                          class="h-16 w-16 cursor-pointer overflow-hidden rounded border-2"
                          :class="url === element.image ? 'border-primary' : 'border-gray-200'"
                          :title="url"
                          @click="() => selectCover(element, url)"
                        >
                          <img :src="url" class="h-full w-full object-cover" alt="" />
                        </div>
                        <span v-if="element.covers.length <= 0" class="text-xs text-danger">{{ $t('wechatArticle.push.cover.none') }}</span>
                      </div>
                    </div>
                    <div class="flex items-center justify-between">
                      <span class="text-xs text-gray-secondary">{{ $t('wechatArticle.push.overrideTip') }}</span>
                      <el-button size="small" :loading="previewLoading === element.id" :disabled="!values.accountId" @click="() => handlePreview(element)">
                        {{ $t('wechatArticle.preview') }}
                      </el-button>
                    </div>
                  </div>
                </div>
              </template>
            </draggable>
            <div class="mt-1 text-xs text-gray-secondary">{{ $t('wechatArticle.push.orderTip', { max: MAX_ARTICLES }) }}</div>
          </div>
        </el-form-item>
        <el-alert v-if="blocker" :title="blocker" type="warning" :closable="false" class="mb-4" />
        <el-form-item prop="publish" :label="$t('wechatArticle.publish')">
          <div>
            <el-switch v-model="values.publish" />
            <div class="text-xs text-gray-secondary">{{ $t('wechatArticle.publishTip') }}</div>
          </div>
        </el-form-item>
        <el-alert :title="$t('wechatArticle.pushTip')" type="info" :closable="false" />
      </el-form>
      <wechat-preview-dialog
        v-model="previewVisible"
        :title="preview.title ?? ''"
        :author="preview.author ?? ''"
        :digest="preview.digest ?? ''"
        :image="preview.image ?? ''"
        :content="preview.content ?? ''"
        :account-name="accountName"
      />
    </template>
    <template #footer>
      <div class="flex items-center justify-between">
        <div></div>
        <div>
          <el-button @click="() => $emit('update:modelValue', false)">{{ $t('cancel') }}</el-button>
          <el-button type="primary" :loading="buttonLoading" :disabled="!!blocker" @click="() => handleSubmit()">{{ $t('save') }}</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

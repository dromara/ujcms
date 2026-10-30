<script setup lang="ts">
import { ref, computed, defineAsyncComponent, toRefs, watch, type PropType } from 'vue';
import { toTree, disableSubtree, disableTreeWithPermission } from '@/utils/tree';
import {
  queryChannel,
  createChannel,
  updateChannel,
  deleteChannel,
  queryChannelList,
  queryChannelPermissions,
  queryChannelTemplates,
  queryArticleTemplates,
  channelSlugExist,
  channelSlugSuggest,
} from '@/api/content';
import { queryProcessModelList } from '@/api/system';
import { queryModelList, queryPerformanceTypeList } from '@/api/config';
import { queryGroupList } from '@/api/user';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import { currentUser } from '@/stores/useCurrentUser';
import FieldItem from '@/views/config/components/FieldItem.vue';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import TemplateSelect from './TemplateSelect.vue';
import Tinymce from '@/components/Tinymce';
import { ImageUpload } from '@/components/Upload';

// Markdown 编辑器（bytemd 及其 remark/rehype 管线，约 650 kB）仅在正文选用 Markdown 编辑器类型时才需要，异步加载以免随表单一并下载。
const MarkdownEditor = defineAsyncComponent(() => import('@/components/MarkdownEditor/MarkdownEditor.vue'));

defineOptions({
  name: 'ChannelForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  parent: { type: Object, default: null },
});
const emit = defineEmits({ 'update:modelValue': null, finished: null });

const { modelValue: visible, parent } = toRefs(props);
const focus = ref<any>();
const values = ref<any>({});
const dialog = ref<any>();
const processModelList = ref<any[]>([]);
const performanceTypeList = ref<any[]>([]);
const channelList = ref<any[]>([]);
const channelModelList = ref<any[]>([]);
const articleModelList = ref<any[]>([]);
const groupList = ref<any[]>([]);
const channelPermissions = ref<string[]>([]);
const channelTemplates = ref<any[]>([]);
const articleTemplates = ref<any[]>([]);
const channelModelId = ref<string>();
const channelModel = computed(() => channelModelList.value.find((item) => item.id === channelModelId.value));
const mains = computed(() => arr2obj(mergeModelFields(getModelData().channel.mains, channelModel.value?.mains, 'channel')));
// 名称失焦且别名为空时按名称请求建议；用户手动改过别名（slugTouched）后不再自动覆盖，切换栏目时重置
const slugTouched = ref<boolean>(false);
const suggestSlug = async () => {
  const name = values.value.name?.trim();
  if (slugTouched.value || values.value.slug || !name) {
    return;
  }
  try {
    const slug = await channelSlugSuggest(name, values.value.id);
    if (!slugTouched.value && !values.value.slug) {
      values.value.slug = slug;
    }
  } catch {
    // 建议失败不阻断编辑，保存时由后端生成
  }
};
const asides = computed(() => arr2obj(mergeModelFields(getModelData().channel.asides, channelModel.value?.asides, 'channel')));
const fields = computed(() => JSON.parse(channelModel.value?.customs || '[]'));
const parentChannelList = computed(() => {
  const list = disableSubtree(channelList.value, values.value.id);
  if (!currentUser.allChannelPermission) {
    return disableTreeWithPermission(list, channelPermissions.value);
  }
  return list;
});

const fetchChannelList = async () => {
  channelList.value = toTree(await queryChannelList());
};
const fetchProcessModelList = async () => {
  if (currentUser.commercialTier > 0) {
    processModelList.value = await queryProcessModelList({ category: 'sys_article', deployed: true });
  }
};
const fetchPerformanceTypeList = async () => {
  if (currentUser.commercialTier >= 2) {
    performanceTypeList.value = await queryPerformanceTypeList();
  }
};
const finished = async () => {
  await fetchChannelList();
  emit('finished');
};
const fetchChannelModelList = async () => {
  channelModelList.value = await queryModelList({ type: 'channel' });
  // 如果 channelModelId 无值，则默认赋予第一个模型的值
  if (channelModelId.value == null && channelModelList.value.length > 0) {
    channelModelId.value = channelModelList.value[0].id;
  }
};
const fetchArticleModelList = async () => {
  articleModelList.value = await queryModelList({ type: 'article' });
};
const fetchChannelTemplates = async () => {
  channelTemplates.value = await queryChannelTemplates();
};
const fetchArticleTemplates = async () => {
  articleTemplates.value = await queryArticleTemplates();
};
const fetchChannelPermissions = async () => {
  channelPermissions.value = await queryChannelPermissions();
};
// 投稿用户组选项（docs/spec/member-contribute/plan.md D3）。用户组是全局的，一次取齐
const fetchGroupList = async () => {
  groupList.value = await queryGroupList();
};
// 新建时模型的默认值取自下面的模型列表，模板下拉的标签取自模板列表：等四份列表到齐才打开对话框。
// 否则页面加载后第一次点“新增”时列表还是空的，栏目模型、文章模型两个必填项会是空白
const ready = ref<boolean>(false);
// 对话框刚打开：新建的默认值不沿用上一次对话框留下的表单值。values 里还是上回编辑或新增的那个栏目，
// 不排除的话，在根栏目点“新增”会把新栏目建到上回那个栏目的上级之下、套用它的模型。
// 打开之后（对话框内的“新增”、连续新增）仍以当前表单为蓝本
const opening = ref<boolean>(false);
const initChannelValues = (bean?: any, isEdit?: boolean): any => {
  const base = opening.value ? undefined : bean;
  return {
    parentId: isEdit ? base?.parentId : (parent.value?.id ?? base?.parentId),
    type: 1,
    channelModelId: base?.channelModelId ?? parent.value?.channelModelId ?? channelModelList.value[0]?.id,
    articleModelId: base?.articleModelId ?? parent.value?.articleModelId ?? articleModelList.value[0]?.id,
    nav: base?.nav ?? parent.value?.nav ?? true,
    book: false,
    real: base?.real ?? parent.value?.real ?? true,
    // 模板沿用蓝本（当前表单，否则上级栏目）已存的值；都没有就留空，即“主题默认”，不替操作员挑一个文件写进去
    channelTemplate: (base ?? parent.value)?.channelTemplate ?? null,
    articleTemplate: (base ?? parent.value)?.articleTemplate ?? null,
    pageSize: 10,
    allowComment: base?.allowComment ?? parent.value?.allowComment ?? true,
    // 允许投稿默认关；投稿用户组新建时不填，由服务端沿用上级栏目的
    allowContribute: base?.allowContribute ?? parent.value?.allowContribute ?? false,
    allowSearch: base?.allowSearch ?? parent.value?.allowSearch ?? true,
    orderDesc: base?.orderDesc ?? parent.value?.orderDesc ?? true,
    customs: {},
  };
};
watch(visible, async () => {
  if (!visible.value) {
    ready.value = false;
    return;
  }
  // 上级栏目选择器的数据每次打开取一次；上一条、下一条之间不必重取
  fetchChannelList();
  fetchChannelPermissions();
  fetchGroupList();
  fetchProcessModelList();
  fetchPerformanceTypeList();
  try {
    await Promise.all([fetchChannelModelList(), fetchArticleModelList(), fetchChannelTemplates(), fetchArticleTemplates()]);
  } finally {
    // 列表请求失败时拦截器已提示错误：对话框照常打开
    channelModelId.value = parent.value?.channelModelId ?? channelModelList.value[0]?.id;
    opening.value = true;
    ready.value = visible.value;
  }
});
// 自定义字段的默认值不是用户的改动：表单与基线一起补（dialog.patch），刚打开的对话框不因此成了“未保存”
watch(fields, () => {
  dialog.value?.patch((target: any) => {
    if (target?.customs != null) {
      initCustoms(target.customs);
    }
  });
});
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
</script>

<template>
  <dialog-form
    ref="dialog"
    v-model:values="values"
    :name="$t('menu.content.channel')"
    append-to-body
    :query-bean="queryChannel"
    :create-bean="createChannel"
    :update-bean="updateChannel"
    :delete-bean="deleteChannel"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="initChannelValues"
    :to-values="(bean) => ({ ...bean })"
    perms="channel"
    :model-value="modelValue && ready"
    :disable-edit="(bean) => !currentUser.allChannelPermission && bean.id != null && !channelPermissions.includes(bean.id)"
    label-width="120px"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="finished"
    @bean-change="
      (bean) => {
        opening = false;
        slugTouched = false;
        channelModelId = bean?.channelModelId ?? parent?.channelModelId ?? channelModelList[0]?.id;
      }
    "
  >
    <template #default="{ bean, isEdit }">
      <el-row>
        <el-col :span="18">
          <el-row>
            <el-col :span="mains['name'].double ? 12 : 24">
              <el-form-item prop="name" :label="mains['name'].name ?? $t('channel.name')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-input ref="focus" v-model="values.name" maxlength="50" @blur="() => suggestSlug()"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="mains['slug'].double ? 12 : 24">
              <el-form-item
                prop="slug"
                :rules="[
                  { pattern: /^[\w-]*$/, message: () => $t('channel.error.slugPattern') },
                  {
                    asyncValidator: async (rule, value, callback) => {
                      if (value !== bean.slug && (await channelSlugExist(value))) {
                        callback($t('channel.error.slugExist'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip :label="mains['slug'].name ?? $t('channel.slug')" message="channel.slug" help /></template>
                <el-input v-model="values.slug" maxlength="50" :placeholder="$t('channel.slug.placeholder')" @input="() => (slugTouched = true)"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="values.type === 3" :span="mains['linkUrl'].double ? 12 : 24">
              <el-form-item
                prop="linkUrl"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^(http|\/).*$/, message: () => $t('channel.error.linkUrl') },
                ]"
              >
                <template #label><label-tip :label="mains['linkUrl'].name ?? $t('channel.linkUrl')" message="channel.linkUrl" help /></template>
                <el-input v-model="values.linkUrl" maxlength="255">
                  <template #append>
                    <el-checkbox v-model="values.targetBlank">{{ $t('channel.targetBlank') }}</el-checkbox>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['seoTitle'].show" :span="mains['seoTitle'].double ? 12 : 24">
              <el-form-item prop="seoTitle" :rules="mains['seoTitle'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['seoTitle'].name ?? $t('channel.seoTitle')" message="channel.seoTitle" help /></template>
                <el-input v-model="values.seoTitle" maxlength="150"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['seoKeywords'].show" :span="mains['seoKeywords'].double ? 12 : 24">
              <el-form-item prop="seoKeywords" :rules="mains['seoKeywords'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['seoKeywords'].name ?? $t('channel.seoKeywords')" message="channel.seoKeywords" help /></template>
                <el-input v-model="values.seoKeywords" maxlength="150"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['seoDescription'].show" :span="mains['seoDescription'].double ? 12 : 24">
              <el-form-item prop="seoDescription" :rules="mains['seoDescription'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['seoDescription'].name ?? $t('channel.seoDescription')" message="channel.seoDescription" help /></template>
                <el-input v-model="values.seoDescription" maxlength="1000"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['image'].show" :span="mains['image'].double ? 12 : 24">
              <el-form-item
                prop="image"
                :label="mains['image'].name ?? $t('channel.image')"
                :rules="mains['image'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <image-upload v-model="values.image" :height="mains['image'].imageHeight" :width="mains['image'].imageWidth" :mode="mains['image'].imageMode"></image-upload>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['channelModel'].show" :span="mains['channelModel'].double ? 12 : 24">
              <el-form-item prop="channelModelId" :label="mains['channelModel'].name ?? $t('channel.channelModel')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-select
                  v-model="values.channelModelId"
                  class="w-full"
                  @change="
                    (value) => {
                      channelModelId = value;
                    }
                  "
                >
                  <el-option v-for="item in channelModelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['articleModel'].show" :span="mains['articleModel'].double ? 12 : 24">
              <el-form-item prop="articleModelId" :label="mains['articleModel'].name ?? $t('channel.articleModel')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-select v-model="values.articleModelId" class="w-full">
                  <el-option v-for="item in articleModelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['channelTemplate'].show" :span="mains['channelTemplate'].double ? 12 : 24">
              <el-form-item prop="channelTemplate" :label="mains['channelTemplate'].name ?? $t('channel.channelTemplate')">
                <template-select v-model="values.channelTemplate" surface="channel" :templates="channelTemplates" default-option />
              </el-form-item>
            </el-col>
            <el-col v-if="mains['articleTemplate'].show" :span="mains['articleTemplate'].double ? 12 : 24">
              <el-form-item prop="articleTemplate" :label="mains['articleTemplate'].name ?? $t('channel.articleTemplate')">
                <template-select v-model="values.articleTemplate" surface="article" :templates="articleTemplates" default-option />
              </el-form-item>
            </el-col>
            <!--
            <el-col v-if="mains['allowComment'].show" :span="mains['allowComment'].double ? 12 : 24">
              <el-form-item
                prop="allowComment"
                :label="mains['allowComment'].name ?? $t('channel.allowComment')"
                :rules="mains['allowComment'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <el-switch v-model="values.allowComment"></el-switch>
              </el-form-item>
            </el-col>
            -->
            <el-col v-if="mains['allowContribute'].show" :span="mains['allowContribute'].double ? 12 : 24">
              <el-form-item prop="allowContribute" :rules="mains['allowContribute'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['allowContribute'].name ?? $t('channel.allowContribute')" message="channel.allowContribute" help /></template>
                <el-switch v-model="values.allowContribute"></el-switch>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['allowContribute'].show && values.allowContribute" :span="mains['allowContribute'].double ? 12 : 24">
              <el-form-item prop="contributeGroupIds">
                <template #label><label-tip message="channel.contributeGroupIds" help /></template>
                <el-select v-model="values.contributeGroupIds" multiple clearable class="w-full" :placeholder="$t('all')">
                  <el-option v-for="item in groupList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['nav'].show" :span="mains['nav'].double ? 12 : 24">
              <el-form-item prop="nav" :rules="mains['nav'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['nav'].name ?? $t('channel.nav')" message="channel.nav" help /></template>
                <el-switch v-model="values.nav"></el-switch>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['real'].show" :span="mains['real'].double ? 12 : 24">
              <el-form-item prop="real" :rules="mains['real'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['real'].name ?? $t('channel.real')" message="channel.real" help /></template>
                <el-switch v-model="values.real"></el-switch>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['allowSearch'].show" :span="mains['allowSearch'].double ? 12 : 24">
              <el-form-item prop="allowSearch" :rules="mains['allowSearch'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['allowSearch'].name ?? $t('channel.allowSearch')" message="channel.allowSearch" help /></template>
                <el-switch v-model="values.allowSearch"></el-switch>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['book'].show" :span="mains['book'].double ? 12 : 24">
              <el-form-item prop="book" :rules="mains['book'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <template #label><label-tip :label="mains['book'].name ?? $t('channel.book')" message="channel.book" help /></template>
                <el-switch v-model="values.book"></el-switch>
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
            <el-col v-if="values.type === 2" :span="mains['text'].double ? 12 : 24">
              <el-form-item
                prop="text"
                :label="mains['text'].name ?? $t('channel.text')"
                :rules="mains['text'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <div class="w-full">
                  <el-radio-group v-if="mains['text'].editorSwitch" v-model="values.editorType" class="mr-6" @change="() => (values.markdown = '')">
                    <el-radio v-for="n in [1, 2]" :key="n" :value="n">{{ $t(`model.field.editorType.${n}`) }}</el-radio>
                  </el-radio-group>
                  <markdown-editor v-if="values.editorType === 2" v-model="values.markdown" v-model:html="values.text" class="leading-6" />
                  <tinymce v-else v-model="values.text" />
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-col>
        <el-col :span="6">
          <el-tabs type="border-card" class="ml-5">
            <el-tab-pane :label="$t('channel.tabs.setting')">
              <el-form-item prop="parentId" :label="asides['parent'].name ?? $t('channel.parent')" label-position="top">
                <el-tree-select
                  v-model="values.parentId"
                  :data="parentChannelList"
                  node-key="id"
                  :props="{ label: 'name', disabled: 'disabled' }"
                  :render-after-expand="false"
                  :disabled="isEdit"
                  placeholder=""
                  check-strictly
                  clearable
                  class="w-full"
                />
              </el-form-item>
              <el-form-item prop="type" :label="asides['type'].name ?? $t('channel.type')" :rules="{ required: true, message: () => $t('v.required') }" label-position="top">
                <el-select v-model="values.type" class="w-full">
                  <el-option v-for="n in [1, 2, 3, 4]" :key="n" :label="$t(`channel.type.${n}`)" :value="n"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="asides['processKey'].show"
                prop="processKey"
                :label="asides['processKey'].name ?? $t('channel.processKey')"
                :rules="asides['processKey'].required ? { required: true, message: () => $t('v.required') } : undefined"
                label-position="top"
              >
                <el-select v-model="values.processKey" clearable class="w-full">
                  <el-option v-for="item in processModelList" :key="item.key" :label="item.name" :value="item.key"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="currentUser.commercialTier >= 2 && asides['performanceType'].show"
                prop="performanceTypeId"
                :label="asides['performanceType'].name ?? $t('channel.performanceType')"
                :rules="asides['performanceType'].required ? { required: true, message: () => $t('v.required') } : undefined"
                label-position="top"
              >
                <el-select v-model="values.performanceTypeId" clearable class="w-full">
                  <el-option v-for="item in performanceTypeList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="asides['channelStaticPath'].show"
                prop="channelStaticPath"
                :label="asides['channelStaticPath'].name ?? $t('channel.channelStaticPath')"
                :rules="asides['channelStaticPath'].required ? { required: true, message: () => $t('v.required') } : undefined"
                label-position="top"
              >
                <template #label><label-tip :label="asides['channelStaticPath'].name ?? $t('channel.channelStaticPath')" message="channel.channelStaticPath" help /></template>
                <el-input v-model="values.channelStaticPath" maxlength="100"><template #append>.html</template></el-input>
              </el-form-item>
              <el-form-item
                prop="pageSize"
                :label="asides['pageSize'].name ?? $t('channel.pageSize')"
                :rules="{ required: true, message: () => $t('v.required') }"
                label-position="top"
              >
                <el-input-number v-model="values.pageSize" :min="1" :max="200"></el-input-number>
              </el-form-item>
              <el-form-item prop="orderDesc" :rules="{ required: true, message: () => $t('v.required') }" label-position="top">
                <template #label><label-tip :label="asides['orderDesc'].name ?? $t('channel.orderDesc')" message="channel.orderDesc" help /></template>
                <el-switch v-model="values.orderDesc" />
              </el-form-item>
            </el-tab-pane>
          </el-tabs>
        </el-col>
      </el-row>
    </template>
  </dialog-form>
</template>

<style lang="scss" scoped></style>

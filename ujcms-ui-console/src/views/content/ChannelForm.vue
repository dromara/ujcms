<script setup lang="ts">
import { ref, computed, toRefs, watch, PropType } from 'vue';
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
  channelAliasExist,
} from '@/api/content';
import { queryProcessModelList } from '@/api/system';
import { queryModelList, queryPerformanceTypeList } from '@/api/config';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import { currentUser } from '@/stores/useCurrentUser';
import FieldItem from '@/views/config/components/FieldItem.vue';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import Tinymce from '@/components/Tinymce';
import { TuiEditor } from '@/components/TuiEditor';
import { ImageUpload } from '@/components/Upload';

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
const processModelList = ref<any[]>([]);
const performanceTypeList = ref<any[]>([]);
const channelList = ref<any[]>([]);
const channelModelList = ref<any[]>([]);
const articleModelList = ref<any[]>([]);
const channelPermissions = ref<string[]>([]);
const channelTemplates = ref<any[]>([]);
const articleTemplates = ref<any[]>([]);
const channelModelId = ref<string>();
const channelModel = computed(() => channelModelList.value.find((item) => item.id === channelModelId.value));
const mains = computed(() => arr2obj(mergeModelFields(getModelData().channel.mains, channelModel.value?.mains, 'channel')));
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
  if (currentUser.epRank > 0) {
    processModelList.value = await queryProcessModelList({ category: 'sys_article', deployed: true });
  }
};
const fetchPerformanceTypeList = async () => {
  if (currentUser.epRank >= 3) {
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
watch(visible, () => {
  if (visible.value) {
    channelModelId.value = parent.value?.articleModelId ?? channelModelList.value[0]?.id;
    fetchChannelPermissions();
    fetchChannelModelList();
    fetchArticleModelList();
    fetchChannelTemplates();
    fetchArticleTemplates();
    fetchProcessModelList();
    fetchPerformanceTypeList();
  }
});
watch(fields, () => {
  initCustoms(values.value.customs);
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
    v-model:values="values"
    :name="$t('menu.content.channel')"
    :query-bean="queryChannel"
    :create-bean="createChannel"
    :update-bean="updateChannel"
    :delete-bean="deleteChannel"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="
      (bean: any, isEdit?: boolean): any => ({
        parentId: isEdit ? bean?.parentId : (parent?.id ?? bean?.parentId),
        type: 1,
        channelModelId: bean?.channelModelId ?? parent?.channelModelId ?? channelModelList[0]?.id,
        articleModelId: bean?.articleModelId ?? parent?.articleModelId ?? articleModelList[0]?.id,
        nav: bean?.nav ?? parent?.nav ?? true,
        book: false,
        real: bean?.real ?? parent?.real ?? true,
        channelTemplate: bean?.channelTemplate ?? parent?.channelTemplate ?? channelTemplates[0],
        articleTemplate: bean?.articleTemplate ?? parent?.articleTemplate ?? articleTemplates[0],
        pageSize: 10,
        allowComment: bean?.allowComment ?? parent?.allowComment ?? true,
        allowContribute: bean?.allowContribute ?? parent?.allowContribute ?? true,
        allowSearch: bean?.allowSearch ?? parent?.allowSearch ?? true,
        orderDesc: bean?.orderDesc ?? parent?.orderDesc ?? true,
        customs: {},
      })
    "
    :to-values="(bean) => ({ ...bean })"
    perms="channel"
    :model-value="modelValue"
    :disable-edit="(bean) => !currentUser.allChannelPermission && bean.id != null && !channelPermissions.includes(bean.id)"
    label-width="120px"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="finished"
    @bean-change="
      async (bean) => {
        channelModelId = bean?.channelModelId ?? parent?.channelModelId ?? articleModelList[0]?.id;
        await fetchChannelList();
      }
    "
  >
    <template #default="{ bean, isEdit }">
      <el-row>
        <el-col :span="18">
          <el-row>
            <el-col :span="mains['name'].double ? 12 : 24">
              <el-form-item prop="name" :label="mains['name'].name ?? $t('channel.name')" :rules="{ required: true, message: () => $t('v.required') }">
                <el-input ref="focus" v-model="values.name" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="mains['alias'].double ? 12 : 24">
              <el-form-item
                prop="alias"
                :rules="[
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^[\w-]*$/, message: () => $t('channel.error.aliasPattern') },
                  {
                    asyncValidator: async (rule, value, callback) => {
                      if (value !== bean.alias && (await channelAliasExist(value))) {
                        callback($t('channel.error.aliasExist'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip :label="mains['alias'].name ?? $t('channel.alias')" message="channel.alias" help /></template>
                <el-input v-model="values.alias" maxlength="50"></el-input>
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
              <el-form-item
                prop="channelTemplate"
                :label="mains['channelTemplate'].name ?? $t('channel.channelTemplate')"
                :rules="mains['channelTemplate'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <el-select v-model="values.channelTemplate" class="w-full">
                  <el-option v-for="item in channelTemplates" :key="item" :label="item + '.html'" :value="item"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['articleTemplate'].show" :span="mains['articleTemplate'].double ? 12 : 24">
              <el-form-item
                prop="articleTemplate"
                :label="mains['articleTemplate'].name ?? $t('channel.articleTemplate')"
                :rules="mains['articleTemplate'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <el-select v-model="values.articleTemplate" class="w-full">
                  <el-option v-for="item in articleTemplates" :key="item" :label="item + '.html'" :value="item"></el-option>
                </el-select>
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
            <el-col v-if="mains['allowContribute'].show" :span="mains['allowContribute'].double ? 12 : 24">
              <el-form-item
                prop="allowContribute"
                :label="mains['allowContribute'].name ?? $t('channel.allowContribute')"
                :rules="mains['allowContribute'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <el-switch v-model="values.allowContribute"></el-switch>
              </el-form-item>
            </el-col>
            -->
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
                  <tui-editor v-if="values.editorType === 2" v-model="values.markdown" v-model:html="values.text" class="leading-6" />
                  <tinymce v-else ref="tinyText" v-model="values.text" />
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
                v-if="currentUser.epRank >= 3 && asides['performanceType'].show"
                prop="performanceType"
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

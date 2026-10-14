<script setup lang="ts">
import { computed, defineAsyncComponent, ref, toRefs, watch, type PropType } from 'vue';
import { toTree, disableSubtree } from '@/utils/tree';
import { queryTopic, createTopic, updateTopic, deleteTopic, queryTopicList, queryTopicTemplates, topicSlugExist, topicSlugSuggest } from '@/api/content';
import { queryModelList } from '@/api/config';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import FieldItem from '@/views/config/components/FieldItem.vue';
import DialogForm from '@/components/DialogForm.vue';
import LabelTip from '@/components/LabelTip.vue';
import TemplateSelect from './TemplateSelect.vue';
import Tinymce from '@/components/Tinymce';
import { ImageUpload } from '@/components/Upload';

// Markdown 编辑器（bytemd 及其 remark/rehype 管线，约 650 kB）仅在正文选用 Markdown 编辑器类型时才需要，异步加载以免随表单一并下载。
const MarkdownEditor = defineAsyncComponent(() => import('@/components/MarkdownEditor/MarkdownEditor.vue'));

defineOptions({
  name: 'TopicForm',
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
const topicList = ref<any[]>([]);
const topicModelList = ref<any[]>([]);
const topicTemplates = ref<any[]>([]);
const topicModelId = ref<string>();
const topicModel = computed(() => topicModelList.value.find((item) => item.id === topicModelId.value));
const mains = computed(() => arr2obj(mergeModelFields(getModelData().topic.mains, topicModel.value?.mains, 'topic')));
// 标题失焦且别名为空时按标题请求建议；用户手动改过别名（slugTouched）后不再自动覆盖，切换专题时重置
const slugTouched = ref<boolean>(false);
const suggestSlug = async () => {
  const title = values.value.title?.trim();
  if (!mains.value['slug'].show || slugTouched.value || values.value.slug || !title) {
    return;
  }
  try {
    const slug = await topicSlugSuggest(title, values.value.id);
    if (!slugTouched.value && !values.value.slug) {
      values.value.slug = slug;
    }
  } catch {
    // 建议失败不阻断编辑，保存时由后端生成
  }
};
const asides = computed(() => arr2obj(mergeModelFields(getModelData().topic.asides, topicModel.value?.asides, 'topic')));
const fields = computed(() => JSON.parse(topicModel.value?.customs || '[]'));
const parentTopicList = computed(() => disableSubtree(topicList.value, values.value.id));

const fetchTopicList = async () => {
  topicList.value = toTree(await queryTopicList());
};
const fetchTopicModelList = async () => {
  topicModelList.value = await queryModelList({ type: 'topic' });
  if (topicModelId.value == null && topicModelList.value.length > 0) {
    topicModelId.value = topicModelList.value[0].id;
  }
};
const fetchTopicTemplates = async () => {
  topicTemplates.value = await queryTopicTemplates();
};
const finished = async () => {
  await fetchTopicList();
  emit('finished');
};
watch(visible, () => {
  if (visible.value) {
    topicModelId.value = parent.value?.topicModelId ?? topicModelList.value[0]?.id;
    fetchTopicModelList();
    fetchTopicTemplates();
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
    :name="$t('topic.form.title')"
    append-to-body
    :query-bean="queryTopic"
    :create-bean="createTopic"
    :update-bean="updateTopic"
    :delete-bean="deleteTopic"
    :bean-id="beanId"
    :bean-ids="beanIds"
    :focus="focus"
    :init-values="
      (bean: any, isEdit?: boolean): any => ({
        parentId: isEdit ? bean?.parentId : (parent?.id ?? bean?.parentId),
        topicModelId: bean?.topicModelId ?? parent?.topicModelId ?? topicModelList[0]?.id,
        // 沿用已存的值；没有就留空，即“主题默认”（docs/spec/channel-management/plan.md D12）
        topicTemplate: bean?.topicTemplate ?? parent?.topicTemplate ?? null,
        editorType: mains['text'].editorType,
        pageSize: 20,
        orderDesc: bean?.orderDesc ?? parent?.orderDesc ?? true,
        customs: initCustoms({}),
      })
    "
    :to-values="(bean) => ({ ...bean })"
    perms="topic"
    :model-value="modelValue"
    label-width="120px"
    large
    @update:model-value="(event) => $emit('update:modelValue', event)"
    @finished="finished"
    @bean-change="
      async (bean) => {
        slugTouched = false;
        topicModelId = bean?.topicModelId ?? parent?.topicModelId ?? topicModelList[0]?.id;
        await fetchTopicList();
      }
    "
  >
    <template #default="{ bean, isEdit }">
      <el-row>
        <el-col :span="18">
          <el-row>
            <el-col v-if="mains['name'].show" :span="mains['name'].double ? 12 : 24">
              <el-form-item prop="title" :label="mains['name'].name ?? $t('topic.name')" :rules="mains['name'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <el-input ref="focus" v-model="values.title" maxlength="150" @blur="() => suggestSlug()"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['slug'].show" :span="mains['slug'].double ? 12 : 24">
              <el-form-item
                prop="slug"
                :rules="[
                  ...(mains['slug'].required ? [{ required: true, message: () => $t('v.required') }] : []),
                  { pattern: /^[\w-]*$/, message: () => $t('topic.error.slugPattern') },
                  {
                    asyncValidator: async (rule, value, callback) => {
                      if (value !== bean.slug && (await topicSlugExist(value))) {
                        callback($t('topic.error.slugExist'));
                        return;
                      }
                      callback();
                    },
                  },
                ]"
              >
                <template #label><label-tip :label="mains['slug'].name ?? $t('topic.slug')" message="topic.slug" help /></template>
                <el-input v-model="values.slug" maxlength="50" :placeholder="$t('topic.slug.placeholder')" @input="() => (slugTouched = true)"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['seoTitle'].show" :span="mains['seoTitle'].double ? 12 : 24">
              <el-form-item prop="seoTitle" :label="mains['seoTitle'].name ?? $t('topic.seoTitle')" :rules="mains['seoTitle'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <el-input v-model="values.seoTitle" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['seoKeywords'].show" :span="mains['seoKeywords'].double ? 12 : 24">
              <el-form-item prop="seoKeywords" :label="mains['seoKeywords'].name ?? $t('topic.seoKeywords')" :rules="mains['seoKeywords'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <el-input v-model="values.seoKeywords" maxlength="50"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['seoDescription'].show" :span="mains['seoDescription'].double ? 12 : 24">
              <el-form-item prop="seoDescription" :label="mains['seoDescription'].name ?? $t('topic.seoDescription')" :rules="mains['seoDescription'].required ? { required: true, message: () => $t('v.required') } : undefined">
                <el-input v-model="values.seoDescription" maxlength="85"></el-input>
              </el-form-item>
            </el-col>
            <el-col v-if="mains['image'].show" :span="mains['image'].double ? 12 : 24">
              <el-form-item
                prop="image"
                :label="mains['image'].name ?? $t('topic.image')"
                :rules="mains['image'].required ? { required: true, message: () => $t('v.required') } : undefined"
              >
                <image-upload v-model="values.image" :height="mains['image'].imageHeight" :width="mains['image'].imageWidth" :mode="mains['image'].imageMode"></image-upload>
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
            <el-col v-if="mains['text'].show" :span="mains['text'].double ? 12 : 24">
              <el-form-item
                prop="text"
                :label="mains['text'].name ?? $t('topic.text')"
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
            <el-tab-pane :label="$t('topic.tabs.setting')">
              <el-form-item v-if="asides['parent'].show" prop="parentId" :label="asides['parent'].name ?? $t('topic.parent')" label-position="top">
                <el-tree-select
                  v-model="values.parentId"
                  :data="parentTopicList"
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
              <el-form-item v-if="asides['topicModel'].show" prop="topicModelId" :label="asides['topicModel'].name ?? $t('topic.topicModel')" :rules="asides['topicModel'].required ? { required: true, message: () => $t('v.required') } : undefined" label-position="top">
                <el-select
                  v-model="values.topicModelId"
                  class="w-full"
                  @change="
                    (value) => {
                      topicModelId = value;
                    }
                  "
                >
                  <el-option v-for="item in topicModelList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item v-if="asides['topicTemplate'].show" prop="topicTemplate" :label="asides['topicTemplate'].name ?? $t('topic.topicTemplate')" label-position="top">
                <template-select v-model="values.topicTemplate" surface="topic" :templates="topicTemplates" default-option />
              </el-form-item>
              <el-form-item v-if="asides['topicStaticPath'].show" prop="topicStaticPath" label-position="top">
                <template #label><label-tip :label="asides['topicStaticPath'].name ?? $t('topic.topicStaticPath')" message="topic.topicStaticPath" help /></template>
                <el-input v-model="values.topicStaticPath" maxlength="100"><template #append>.html</template></el-input>
              </el-form-item>
              <el-form-item v-if="asides['pageSize'].show" prop="pageSize" :label="asides['pageSize'].name ?? $t('topic.pageSize')" :rules="asides['pageSize'].required ? { required: true, message: () => $t('v.required') } : undefined" label-position="top">
                <el-input-number v-model="values.pageSize" :min="1" :max="200"></el-input-number>
              </el-form-item>
              <el-form-item v-if="asides['orderDesc'].show" prop="orderDesc" :rules="asides['orderDesc'].required ? { required: true, message: () => $t('v.required') } : undefined" label-position="top">
                <template #label><label-tip :label="asides['orderDesc'].name ?? $t('topic.orderDesc')" message="topic.orderDesc" help /></template>
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

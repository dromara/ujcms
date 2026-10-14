<script setup lang="ts">
import { computed, ref, toRefs, watch, type PropType } from 'vue';
import { queryBlockList } from '@/api/config';
import { queryBlockItem, createBlockItem, updateBlockItem, deleteBlockItem } from '@/api/content';
import { queryModelList } from '@/api/config';
import { getModelData, mergeModelFields, arr2obj } from '@/data';
import DialogForm from '@/components/DialogForm.vue';
import FieldItem from '@/views/config/components/FieldItem.vue';
import { BaseUpload, ImageUpload } from '@/components/Upload';
import ImageExtractor from './components/ImageExtractor.vue';

defineOptions({
  name: 'BlockItemForm',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  beanId: { type: String, default: null },
  beanIds: { type: Array as PropType<string[]>, required: true },
  blockId: { type: String, default: null },
  articleId: { type: String, default: null },
  title: { type: String, default: null },
  description: { type: String, default: null },
  video: { type: String, default: null },
  images: { type: Array as PropType<string[]>, default: () => [] },
});
defineEmits({ 'update:modelValue': null, finished: null });
const { blockId } = toRefs(props);
const currentBlockId = ref<string | null>();
const focus = ref<any>();
const values = ref<any>({});
const dialog = ref<any>();
const blockList = ref<any[]>([]);
const blockItemModelList = ref<any[]>([]);
const block = computed(() => blockList.value.find((item) => item.id === (currentBlockId.value ?? blockId?.value)));
const blockItemModel = computed(() => blockItemModelList.value.find((item) => item.id === block.value?.blockItemModelId));
const mains = computed(() => arr2obj(mergeModelFields(getModelData().blockItem.mains, blockItemModel.value?.mains, 'blockItem')));
const fields = computed(() => JSON.parse(blockItemModel.value?.customs || '[]'));

// 每次打开时取数而非挂载时取一次：区块在“区块管理”同一屏增删、改绑区块项模型，挂载时取的那份会过期。
// 本表单有两个宿主（区块管理、文章管理的“推荐到区块”），故自取而不由宿主传入（immediate：热更新重挂时对话框可能已是打开状态）
watch(
  () => props.modelValue,
  async (visible) => {
    if (visible) {
      [blockList.value, blockItemModelList.value] = await Promise.all([queryBlockList(), queryModelList({ type: 'blockItem' })]);
    }
  },
  { immediate: true },
);

// 自定义字段的默认值不是用户的改动：表单与基线一起补（dialog.patch），刚打开的对话框不因此成了“未保存”
watch(fields, () => {
  dialog.value?.patch((target: any) => {
    if (target == null) {
      return;
    }
    if (target.customs == null) {
      target.customs = {};
    }
    initCustoms(target.customs);
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

const imageExtractorVisible = ref<boolean>(false);
</script>

<template>
  <div>
    <dialog-form
      ref="dialog"
      v-model:values="values"
      :name="$t('blockItem.label')"
      :query-bean="queryBlockItem"
      :create-bean="createBlockItem"
      :update-bean="updateBlockItem"
      :delete-bean="deleteBlockItem"
      :bean-id="beanId"
      :bean-ids="beanIds"
      :focus="focus"
      :init-values="() => ({ blockId: blockId, articleId: articleId, title: title, description: description, video: video, enabled: true, customs: {} })"
      :to-values="(bean) => ({ ...bean, articleTitle: bean.article?.title, articleId: bean.article?.id, customs: bean.customs ?? {} })"
      :addable="block?.enabled"
      perms="blockItem"
      :model-value="modelValue"
      @update:model-value="(event) => $emit('update:modelValue', event)"
      @finished="() => $emit('finished')"
      @bean-change="(bean) => (currentBlockId = bean.blockId)"
    >
      <template #default="{ isEdit }">
        <el-form-item prop="blockId" :label="$t('blockItem.block')" :rules="{ required: true, message: () => $t('v.required') }">
          <el-select v-model="values.blockId" class="w-full" disabled @change="(value: any) => (currentBlockId = value)">
            <template v-for="item in blockList" :key="item.id">
              <el-option v-if="isEdit || item.enabled" :label="item.name" :value="item.id" :disabled="!item.enabled"></el-option>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item v-if="isEdit && values.articleId != null" :label="$t('blockItem.articleId')">
          <el-input v-model="values.articleId" disabled></el-input>
        </el-form-item>
        <el-form-item v-if="isEdit && values.articleTitle != null" :label="$t('blockItem.articleTitle')">
          <el-input v-model="values.articleTitle" disabled></el-input>
        </el-form-item>
        <el-form-item
          v-if="mains['title']?.show"
          prop="title"
          :label="mains['title'].name ?? $t('blockItem.title')"
          :rules="mains['title'].required ? { required: true, message: () => $t('v.required') } : undefined"
        >
          <el-input ref="focus" v-model="values.title" maxlength="150"></el-input>
        </el-form-item>
        <el-form-item
          v-if="values.articleId == null && mains['linkUrl']?.show"
          prop="linkUrl"
          :label="mains['linkUrl'].name ?? $t('blockItem.linkUrl')"
          :rules="
            mains['linkUrl'].required
              ? [
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^(http|\/).*$/, message: () => $t('blockItem.error.linkUrl') },
                ]
              : { pattern: /^(http|\/).*$/, message: () => $t('blockItem.error.linkUrl') }
          "
        >
          <el-input v-model="values.linkUrl" maxlength="255"></el-input>
        </el-form-item>
        <el-form-item
          v-if="mains['subtitle']?.show"
          prop="subtitle"
          :label="mains['subtitle'].name ?? $t('blockItem.subtitle')"
          :rules="mains['subtitle'].required ? { required: true, message: () => $t('v.required') } : undefined"
        >
          <el-input v-model="values.subtitle" maxlength="150"></el-input>
        </el-form-item>
        <el-form-item
          v-if="mains['description']?.show"
          prop="description"
          :label="mains['description'].name ?? $t('blockItem.description')"
          :rules="mains['description'].required ? { required: true, message: () => $t('v.required') } : undefined"
        >
          <el-input v-model="values.description" type="textarea" :rows="5" maxlength="1000"></el-input>
        </el-form-item>
        <el-form-item
          v-if="mains['image']?.show"
          prop="image"
          :label="mains['image'].name ?? $t('blockItem.image')"
          :rules="mains['image'].required ? { required: true, message: () => $t('v.required') } : undefined"
        >
          <image-upload v-model="values.image" :width="mains['image'].imageWidth" :height="mains['image'].imageHeight" :mode="mains['image'].imageMode ?? 'manual'"></image-upload>
          <el-button v-if="images.length > 0" class="self-start ml-2" @click="() => (imageExtractorVisible = true)">{{ $t('article.extractImage') }}</el-button>
        </el-form-item>
        <el-form-item
          v-if="mains['mobileImage']?.show"
          prop="mobileImage"
          :label="mains['mobileImage'].name ?? $t('blockItem.mobileImage')"
          :rules="mains['mobileImage'].required ? { required: true, message: () => $t('v.required') } : undefined"
        >
          <image-upload v-model="values.mobileImage" :width="mains['mobileImage'].imageWidth" :height="mains['mobileImage'].imageHeight" :mode="mains['mobileImage'].imageMode ?? 'manual'"></image-upload>
        </el-form-item>
        <el-form-item
          v-if="mains['video']?.show"
          prop="video"
          :label="mains['video'].name ?? $t('blockItem.video')"
          :rules="mains['video'].required ? { required: true, message: () => $t('v.required') } : undefined"
        >
          <el-input v-model="values.video" maxlength="255">
            <template #prepend>URL</template>
          </el-input>
          <base-upload type="video" pickable :on-success="(res: any) => (values.video = res.url)"></base-upload>
        </el-form-item>
        <template v-for="field in fields" :key="field.code">
          <el-form-item :prop="`customs.${field.code}`" :label="field.name" :rules="field.required ? { required: true, message: () => $t('v.required') } : undefined">
            <field-item v-model="values.customs[field.code]" v-model:model-key="values.customs[field.code + 'Key']" :field="field"></field-item>
          </el-form-item>
        </template>
        <el-form-item prop="targetBlank" :label="$t('blockItem.targetBlank')">
          <el-switch v-model="values.targetBlank" />
        </el-form-item>
        <el-form-item v-if="mains['startDate']?.show" prop="startDate" :label="mains['startDate'].name ?? $t('blockItem.startDate')">
          <el-date-picker v-model="values.startDate" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" class="w-full"></el-date-picker>
        </el-form-item>
        <el-form-item v-if="mains['endDate']?.show" prop="endDate" :label="mains['endDate'].name ?? $t('blockItem.endDate')">
          <el-date-picker v-model="values.endDate" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" class="w-full"></el-date-picker>
        </el-form-item>
        <el-form-item prop="enabled" :label="$t('blockItem.enabled')">
          <el-switch v-model="values.enabled" />
        </el-form-item>
      </template>
    </dialog-form>
    <image-extractor v-model="imageExtractorVisible" :urls="images" :append-to-body="true" @finished="(urls: string[]) => (values.image = urls[0])" />
  </div>
</template>

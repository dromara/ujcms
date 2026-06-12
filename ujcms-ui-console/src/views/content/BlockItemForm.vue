<script setup lang="ts">
import { computed, onMounted, ref, toRefs, PropType } from 'vue';
import { queryBlockList } from '@/api/config';
import { queryBlockItem, createBlockItem, updateBlockItem, deleteBlockItem } from '@/api/content';
import DialogForm from '@/components/DialogForm.vue';
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
const blockList = ref<any[]>([]);
const block = computed(() => blockList.value.find((item) => item.id === (currentBlockId.value ?? blockId?.value)));

onMounted(async () => {
  blockList.value = await queryBlockList();
});

const imageExtractorVisible = ref<boolean>(false);
</script>

<template>
  <div>
    <dialog-form
      v-model:values="values"
      :name="$t('menu.content.blockItem')"
      :query-bean="queryBlockItem"
      :create-bean="createBlockItem"
      :update-bean="updateBlockItem"
      :delete-bean="deleteBlockItem"
      :bean-id="beanId"
      :bean-ids="beanIds"
      :focus="focus"
      :init-values="() => ({ blockId: blockId, articleId: articleId, title: title, description: description, video: video, enabled: true })"
      :to-values="(bean) => ({ ...bean, articleTitle: bean.article?.title, articleId: bean.article?.id })"
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
        <el-form-item prop="title" :label="$t('blockItem.title')" :rules="{ required: true, message: () => $t('v.required') }">
          <el-input ref="focus" v-model="values.title" maxlength="150"></el-input>
        </el-form-item>
        <el-form-item
          v-if="values.articleId == null && block?.withLinkUrl"
          prop="linkUrl"
          :label="$t('blockItem.linkUrl')"
          :rules="
            block?.linkUrlRequired
              ? [
                  { required: true, message: () => $t('v.required') },
                  { pattern: /^(http|\/).*$/, message: () => $t('blockItem.error.linkUrl') },
                ]
              : { pattern: /^(http|\/).*$/, message: () => $t('blockItem.error.linkUrl') }
          "
        >
          <el-input v-model="values.linkUrl" maxlength="255"></el-input>
        </el-form-item>
        <el-form-item v-if="block?.withSubtitle" prop="subtitle" :label="$t('blockItem.subtitle')" :rules="{ required: block?.subtitleRequired, message: () => $t('v.required') }">
          <el-input v-model="values.subtitle" maxlength="150"></el-input>
        </el-form-item>
        <el-form-item
          v-if="block?.withDescription"
          prop="description"
          :label="$t('blockItem.description')"
          :rules="{ required: block?.descriptionRequired, message: () => $t('v.required') }"
        >
          <el-input v-model="values.description" type="textarea" :rows="5" maxlength="1000"></el-input>
        </el-form-item>
        <el-form-item v-if="block?.withImage" prop="image" :label="$t('blockItem.image')" :rules="{ required: block?.imageRequired, message: () => $t('v.required') }">
          <image-upload v-model="values.image" :width="block.imageWidth" :height="block.imageHeight" mode="manual"></image-upload>
          <el-button v-if="images.length > 0" class="self-start ml-2" @click="() => (imageExtractorVisible = true)">{{ $t('article.extractImage') }}</el-button>
        </el-form-item>
        <el-form-item
          v-if="block?.withMobileImage"
          prop="mobileImage"
          :label="$t('blockItem.mobileImage')"
          :rules="{ required: block?.mobileImageRequired, message: () => $t('v.required') }"
        >
          <image-upload v-model="values.mobileImage" :width="block.mobileImageWidth" :height="block.mobileImageHeight" mode="manual"></image-upload>
        </el-form-item>
        <el-form-item v-if="block?.withVideo" prop="video" :label="$t('blockItem.video')" :rules="{ required: block?.videoRequired, message: () => $t('v.required') }">
          <el-input v-model="values.video" maxlength="255">
            <template #prepend>URL</template>
          </el-input>
          <base-upload type="video" :on-success="(res: any) => (values.video = res.url)"></base-upload>
        </el-form-item>
        <el-form-item prop="targetBlank" :label="$t('blockItem.targetBlank')">
          <el-switch v-model="values.targetBlank" />
        </el-form-item>
        <el-form-item prop="enabled" :label="$t('blockItem.enabled')">
          <el-switch v-model="values.enabled" />
        </el-form-item>
      </template>
    </dialog-form>
    <image-extractor v-model="imageExtractorVisible" :urls="images" :append-to-body="true" @finished="(urls: string[]) => (values.image = urls[0])" />
  </div>
</template>

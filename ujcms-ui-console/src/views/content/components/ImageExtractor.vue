<script setup lang="ts">
import { ref, toRefs, watch, PropType } from 'vue';
import _ from 'lodash';

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  urls: { type: Array as PropType<string[]>, default: () => [] },
  min: { type: Number, default: 1 },
  max: { type: Number, default: 1 },
  appendToBody: { type: Boolean },
});
const emit = defineEmits<{
  (e: 'update:modelValue', visible: boolean): void;
  (e: 'finished', urls: string[]): void;
}>();

const { urls, max, modelValue: visible } = toRefs(props);
const images = ref<any>();

watch(visible, () => {
  if (visible.value) {
    images.value = _.uniq(urls.value).map((url: string) => ({ url, checked: false }));
  }
});
const checkImg = (img: any) => {
  if (img.checked) {
    img.checked = !img.checked;
    return;
  }
  let count = 0;
  images.value.forEach((it: any) => {
    if (it.checked) {
      count += 1;
    }
    if (count >= max.value) {
      it.checked = false;
    }
  });
  img.checked = true;
};
const finish = () => {
  emit(
    'finished',
    images.value.filter((img: any) => img.checked).map((img: any) => img.url),
  );
  emit('update:modelValue', false);
};
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('article.extractImage')"
    :width="768"
    :append-to-body="appendToBody"
    @update:model-value="(event) => $emit('update:modelValue', event)"
  >
    <el-row :gutter="16">
      <el-col v-for="image in images" :key="image.url" :src="image" :span="8" class="mt-2">
        <div class="p-1 border-2 rounded cursor-pointer text-center" :class="{ 'border-primary': image.checked, 'border-2': image.checked }" @click="() => checkImg(image)">
          <!-- <el-image :src="image.url" :preview-src-list="images.map((it: any) => it.url)" preview-teleported :initial-index="index" class="inline" /> -->
          <img :src="image.url" class="max-h-36 inline" />
        </div>
      </el-col>
    </el-row>
    <template #footer>
      <el-button type="primary" :disabled="images.filter((image: any) => image.checked).length < min" @click="() => finish()">{{ $t('ok') }}</el-button>
    </template>
  </el-dialog>
</template>

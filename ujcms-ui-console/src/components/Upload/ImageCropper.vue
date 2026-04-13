<script setup lang="ts">
import { computed, ref, toRefs, PropType } from 'vue';
import Cropper from 'cropperjs';
import 'cropperjs/dist/cropper.css';
import { cropImage, cropAvatar } from '@/api/config';

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  /**
   * image: 图片上传, avatar: 头像上传
   */
  type: { type: String as PropType<'image' | 'avatar'>, default: 'image' },
  src: { type: String, default: null },
  width: { type: Number, default: null },
  height: { type: Number, default: null },
  thumbnailWidth: { type: Number, default: null },
  thumbnailHeight: { type: Number, default: null },
});
const emit = defineEmits({ 'update:modelValue': null, success: null });

const { modelValue, type, src, width, height, thumbnailWidth, thumbnailHeight } = toRefs(props);
const visible = computed({
  get: () => modelValue.value,
  set: (val) => emit('update:modelValue', val),
});

const imgRef = ref<any>();
const cropper = ref<any>();
const cropParam = ref<any>({});

const initCropper = () => {
  if (imgRef.value) {
    cropper.value = new Cropper(imgRef.value, {
      aspectRatio: width?.value && height?.value ? width.value / height.value : NaN,
      autoCropArea: width?.value && height?.value ? 1 : 0.8,
      viewMode: 1,
      minCropBoxWidth: width?.value ?? 16,
      minCropBoxHeight: height?.value ?? 16,
      zoomable: false,
      crop(event) {
        cropParam.value.url = src.value;
        cropParam.value.x = Math.floor(event.detail.x);
        cropParam.value.y = Math.floor(event.detail.y);
        cropParam.value.width = Math.floor(event.detail.width);
        cropParam.value.height = Math.floor(event.detail.height);
        cropParam.value.maxWidth = width?.value;
        cropParam.value.maxHeight = height?.value;
        cropParam.value.thumbnailWidth = thumbnailWidth?.value;
        cropParam.value.thumbnailHeight = thumbnailHeight?.value;
      },
    });
  }
};
const destroyCropper = () => {
  if (cropper.value) {
    cropper.value.destroy();
  }
};
const handleSubmit = async () => {
  visible.value = false;
  if (type.value === 'avatar') {
    emit('success', (await cropAvatar(cropParam.value)).url);
  } else {
    emit('success', (await cropImage(cropParam.value)).url);
  }
};
</script>

<template>
  <el-dialog v-model="visible" :title="$t('imageCrop')" top="5vh" :width="768" destroy-on-close append-to-body @closed="() => destroyCropper()">
    <div class="text-center">
      <img ref="imgRef" :src="src" alt="" class="inline" style="max-height: 410px" @load="() => initCropper()" />
    </div>
    <div class="text-right">
      <el-button type="primary" native-type="submit" class="mt-4" @click.prevent="() => handleSubmit()">{{ $t('save') }}</el-button>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
/* Ensure the size of the image fit the container perfectly */
:deep(img) {
  display: block;
  /* This rule is very important, please don't ignore this */
  max-width: 100%;
}
</style>

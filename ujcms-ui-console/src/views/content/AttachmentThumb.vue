<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Document, Files, Headset, Picture, VideoCamera } from '@element-plus/icons-vue';

const props = defineProps<{ name?: string; url?: string }>();

// 图片加载失败（文件已被删除/移动，仅剩附件记录）：显示“文件缺失”占位而不是浏览器的破图 alt 文本
const broken = ref(false);
watch(
  () => props.url,
  () => (broken.value = false),
);

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif', 'ico'];
const VIDEO_EXT = ['mp4', 'webm', 'mov', 'mkv', 'avi', 'flv', 'm4v', 'ogv'];
const AUDIO_EXT = ['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac', 'wma'];
const PDF_EXT = ['pdf'];

type Kind = 'image' | 'video' | 'audio' | 'pdf' | 'other';

const getExt = (s?: string): string => {
  if (!s) return '';
  const clean = s.split('?')[0]?.split('#')[0] ?? '';
  const dot = clean.lastIndexOf('.');
  return dot >= 0 ? clean.slice(dot + 1).toLowerCase() : '';
};

const kind = computed<Kind>(() => {
  const ext = getExt(props.url) || getExt(props.name);
  if (IMAGE_EXT.includes(ext)) return 'image';
  if (VIDEO_EXT.includes(ext)) return 'video';
  if (AUDIO_EXT.includes(ext)) return 'audio';
  if (PDF_EXT.includes(ext)) return 'pdf';
  return 'other';
});

const ext = computed(() => getExt(props.url) || getExt(props.name));
</script>

<template>
  <div class="attachment-thumb">
    <img v-if="kind === 'image' && url && !broken" :src="url" :alt="name" loading="lazy" @error="broken = true" />
    <div v-else class="glyph" :class="broken ? 'glyph-missing' : `glyph-${kind}`">
      <el-icon :size="36">
        <Picture v-if="kind === 'image'" />
        <VideoCamera v-else-if="kind === 'video'" />
        <Headset v-else-if="kind === 'audio'" />
        <Document v-else-if="kind === 'pdf'" />
        <Files v-else />
      </el-icon>
      <span v-if="broken" class="ext-badge missing-badge">{{ $t('attachment.missing') }}</span>
      <span v-else-if="ext" class="ext-badge">{{ ext.toUpperCase() }}</span>
    </div>
  </div>
</template>

<style scoped>
.attachment-thumb {
  position: relative;
  width: 100%;
  height: 140px;
  padding: 8px;
  box-sizing: border-box;
  background: var(--el-fill-color-light);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.attachment-thumb img {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 2px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.glyph {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}
.glyph-video {
  color: var(--el-color-primary);
}
.glyph-audio {
  color: var(--el-color-success);
}
.glyph-pdf {
  color: var(--el-color-danger);
}
.glyph-missing {
  color: var(--el-text-color-placeholder);
}
.missing-badge {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.ext-badge {
  font-size: 10px;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 2px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
  letter-spacing: 0.5px;
}
</style>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { CopyDocument, Document, Download, Files, Headset, Loading, Picture, VideoCamera } from '@element-plus/icons-vue';
import { useI18n } from 'vue-i18n';
import dayjs from 'dayjs';
import { queryAttachment } from '@/api/system';

interface AttachmentRow {
  id: string | number;
  name?: string;
  path?: string;
  url?: string;
  size?: string;
  length?: number;
  created?: string;
  used?: boolean;
  user?: { username?: string; nickname?: string };
  referList?: Array<{ referType: string; referId: string | number; referTitle?: string | null }>;
}

// zIndex：从编辑器内的选择器（层级 2000）打开时需显式抬高，否则详情与图片预览会被选择器盖住
const props = defineProps<{ modelValue: boolean; row?: AttachmentRow | null; zIndex?: number }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();

const { t } = useI18n();

// 列表接口不返回引用列表（referList），打开详情时按 ID 取完整记录（引用 + 最新使用状态）；取回前先用列表行渲染
const detail = ref<AttachmentRow | null>(null);
const detailLoading = ref(false);
const item = computed<AttachmentRow | null>(() => detail.value ?? props.row ?? null);
watch(
  () => (props.modelValue ? props.row?.id : undefined),
  async (id) => {
    detail.value = null;
    if (id == null) return;
    detailLoading.value = true;
    try {
      const full = await queryAttachment(String(id));
      if (props.row?.id === id && full) {
        detail.value = { ...props.row, ...full };
      }
    } catch {
      /* 取不到完整记录时保留列表行数据（错误提示由全局拦截器处理） */
    } finally {
      if (props.row?.id === id) detailLoading.value = false;
    }
  },
  { immediate: true },
);

// 引用类型显示名：有译文用译文，否则退回原始类型名（如商业模块自定义的 referType）
const referLabel = (type: string): string => {
  const key = `attachment.referType.${type}`;
  const label = t(key);
  return label === key ? type : label;
};

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif', 'ico'];
const VIDEO_EXT = ['mp4', 'webm', 'mov', 'mkv', 'avi', 'flv', 'm4v', 'ogv'];
const AUDIO_EXT = ['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac', 'wma'];
const PDF_EXT = ['pdf'];

type Kind = 'image' | 'video' | 'audio' | 'pdf' | 'other';

const getExt = (s?: string): string => {
  if (!s) return '';
  const clean = (s.split('?')[0] ?? '').split('#')[0] ?? '';
  const dot = clean.lastIndexOf('.');
  return dot >= 0 ? clean.slice(dot + 1).toLowerCase() : '';
};

const kind = computed<Kind>(() => {
  const ext = getExt(props.row?.url) || getExt(props.row?.name);
  if (IMAGE_EXT.includes(ext)) return 'image';
  if (VIDEO_EXT.includes(ext)) return 'video';
  if (AUDIO_EXT.includes(ext)) return 'audio';
  if (PDF_EXT.includes(ext)) return 'pdf';
  return 'other';
});

const ext = computed(() => getExt(props.row?.url) || getExt(props.row?.name));

const kindLabelKey = computed(() => {
  switch (kind.value) {
    case 'image':
      return 'attachment.type.image';
    case 'video':
      return 'attachment.type.video';
    case 'audio':
      return 'attachment.type.audio';
    case 'pdf':
      return 'attachment.type.doc';
    default:
      return 'attachment.type.file';
  }
});

const previewImageList = computed(() => (props.row?.url ? [props.row.url] : []));

const mediaKey = ref(0);
const imageDims = ref<{ w: number; h: number } | null>(null);
watch(
  () => props.row?.id,
  () => {
    mediaKey.value += 1;
    imageDims.value = null;
  },
);

const pdfUrl = ref('');
const pdfLoading = ref(false);

const revokePdf = () => {
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value);
    pdfUrl.value = '';
  }
};

// X-Frame-Options: DENY on file responses blocks framing the URL directly, so we
// fetch the bytes (fetch is not subject to that header) and frame a same-origin blob URL.
const loadPdf = async (url: string) => {
  revokePdf();
  if (!url) return;
  pdfLoading.value = true;
  try {
    const resp = await fetch(url, { credentials: 'same-origin' });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const blob = await resp.blob();
    if (props.row?.url !== url) return; // superseded or dialog closed while loading
    const pdf = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' });
    pdfUrl.value = URL.createObjectURL(pdf);
  } catch {
    pdfUrl.value = '';
  } finally {
    if (props.row?.url === url) pdfLoading.value = false;
  }
};

watch(
  () => (props.modelValue && kind.value === 'pdf' ? (props.row?.url ?? '') : ''),
  (url) => loadPdf(url),
  { immediate: true },
);

onBeforeUnmount(revokePdf);

const handleImageLoad = (event: Event) => {
  const img = event.target as HTMLImageElement;
  if (img.naturalWidth && img.naturalHeight) {
    imageDims.value = { w: img.naturalWidth, h: img.naturalHeight };
  }
};

const handleCopy = async (text?: string) => {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(t('success'));
  } catch {
    ElMessage.error(t('error.title'));
  }
};
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="$t('attachment.detail')"
    width="80%"
    :z-index="zIndex"
    align-center
    :close-on-click-modal="true"
    destroy-on-close
    class="attachment-detail-dialog"
  >
    <div v-if="item" class="detail-body">
      <div class="preview-pane" :class="`preview-kind-${kind}`">
        <el-image
          v-if="kind === 'image'"
          :src="item.url"
          :preview-src-list="previewImageList"
          :z-index="zIndex ? zIndex + 1 : undefined"
          fit="contain"
          hide-on-click-modal
          class="preview-image"
          @load="handleImageLoad"
        >
          <template #error>
            <div class="glyph glyph-fallback">
              <el-icon :size="72"><Picture /></el-icon>
              <span class="glyph-hint">{{ item.name }}</span>
            </div>
          </template>
        </el-image>
        <video
          v-else-if="kind === 'video'"
          :key="`video-${mediaKey}`"
          :src="item.url"
          controls
          preload="metadata"
          class="preview-video"
        />
        <div v-else-if="kind === 'audio'" class="preview-fallback">
          <div class="glyph glyph-audio">
            <el-icon :size="88"><Headset /></el-icon>
            <span v-if="ext" class="ext-badge">{{ ext.toUpperCase() }}</span>
          </div>
          <audio :key="mediaKey" :src="item.url" controls preload="metadata" class="preview-audio" />
        </div>
        <iframe
          v-else-if="kind === 'pdf' && pdfUrl"
          :key="`pdf-${mediaKey}`"
          :src="pdfUrl"
          class="preview-pdf"
          :title="item.name"
        />
        <div v-else-if="kind === 'pdf' && pdfLoading" class="preview-fallback">
          <el-icon class="is-loading" :size="48"><Loading /></el-icon>
        </div>
        <div v-else class="preview-fallback">
          <div class="glyph" :class="kind === 'pdf' ? 'glyph-pdf' : 'glyph-other'">
            <el-icon :size="88">
              <Document v-if="kind === 'pdf'" />
              <Files v-else />
            </el-icon>
            <span v-if="ext" class="ext-badge">{{ ext.toUpperCase() }}</span>
          </div>
        </div>
      </div>

      <div class="meta-pane">
        <header class="meta-header">
          <div class="meta-header-top">
            <el-icon class="kind-icon" :class="`glyph-${kind}`" :size="20">
              <Picture v-if="kind === 'image'" />
              <VideoCamera v-else-if="kind === 'video'" />
              <Headset v-else-if="kind === 'audio'" />
              <Document v-else-if="kind === 'pdf'" />
              <Files v-else />
            </el-icon>
            <span class="kind-label">{{ $t(kindLabelKey) }}</span>
            <span v-if="ext" class="ext-chip">{{ ext.toUpperCase() }}</span>
            <el-tag :type="item.used ? 'success' : 'info'" size="small" class="used-tag">
              {{ $t(item.used ? 'attachment.inUse' : 'attachment.unused') }}
            </el-tag>
          </div>
          <h2 class="meta-name" :title="item.name">{{ item.name }}</h2>
          <div class="meta-subtitle">
            <span>{{ item.size }}</span>
            <template v-if="imageDims">
              <span class="dot">·</span>
              <span>{{ imageDims.w }} × {{ imageDims.h }}</span>
            </template>
          </div>
        </header>

        <section class="meta-section">
          <div class="meta-row">
            <span class="meta-label">ID</span>
            <span class="meta-text meta-mono">{{ item.id }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ $t('attachment.created') }}</span>
            <span class="meta-text">{{ item.created ? dayjs(item.created).format('YYYY-MM-DD HH:mm:ss') : '' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ $t('attachment.user') }}</span>
            <span class="meta-text">{{ item.user?.username }}</span>
          </div>
        </section>

        <section class="meta-section">
          <div class="meta-row">
            <span class="meta-label">{{ $t('attachment.path') }}</span>
            <div class="meta-text meta-copy">
              <code :title="item.path">{{ item.path }}</code>
              <el-button :icon="CopyDocument" link size="small" @click="handleCopy(item.path)" />
            </div>
          </div>
          <div class="meta-row">
            <span class="meta-label">{{ $t('attachment.url') }}</span>
            <div class="meta-text meta-copy">
              <a :href="item.url" target="_blank" rel="noopener" :title="item.url" class="meta-link">{{ item.url }}</a>
              <el-button :icon="CopyDocument" link size="small" @click="handleCopy(item.url)" />
            </div>
          </div>
        </section>

        <section v-if="item.referList && item.referList.length" class="meta-section">
          <div class="meta-row meta-row-block">
            <span class="meta-label">{{ $t('attachment.refer') }}</span>
            <div class="refer-list">
              <span v-for="r in item.referList" :key="`${r.referType}-${r.referId}`" class="refer-chip" :title="`${r.referType}-${r.referId}`">
                {{ referLabel(r.referType) }}{{ $t('punct.colon') }}{{ r.referTitle ?? `#${r.referId}` }}
              </span>
            </div>
          </div>
        </section>
        <section v-else-if="detailLoading" class="meta-section">
          <div class="meta-row meta-row-block">
            <span class="meta-label">{{ $t('attachment.refer') }}</span>
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
        </section>

        <div class="meta-actions">
          <el-button type="primary" :icon="Download" tag="a" :href="item.url" target="_blank" rel="noopener" size="large" class="action-primary">
            {{ $t('attachment.openInNewTab') }}
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.attachment-detail-dialog :deep(.el-dialog) {
  max-width: 1200px;
  min-width: 640px;
}
.attachment-detail-dialog :deep(.el-dialog__body) {
  padding: 16px 24px 24px;
}
.detail-body {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(360px, 1fr);
  gap: 20px;
  min-height: 480px;
}
.preview-pane {
  position: relative;
  background:
    linear-gradient(45deg, var(--el-fill-color-lighter) 25%, transparent 25%) 0 0 / 16px 16px,
    linear-gradient(-45deg, var(--el-fill-color-lighter) 25%, transparent 25%) 0 8px / 16px 16px,
    linear-gradient(45deg, transparent 75%, var(--el-fill-color-lighter) 75%) 8px -8px / 16px 16px,
    linear-gradient(-45deg, transparent 75%, var(--el-fill-color-lighter) 75%) -8px 0 / 16px 16px,
    var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  min-height: 480px;
  max-height: 70vh;
  overflow: hidden;
}
.preview-kind-video,
.preview-kind-audio,
.preview-kind-pdf,
.preview-kind-other {
  background: var(--el-fill-color-light);
}
.preview-image {
  max-width: 100%;
  max-height: calc(70vh - 32px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.preview-image :deep(img) {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: calc(70vh - 32px);
  object-fit: contain;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-radius: 2px;
  cursor: zoom-in;
}
.preview-video {
  width: 100%;
  max-height: calc(70vh - 32px);
  background: #000;
  border-radius: 4px;
}
.preview-pdf {
  width: 100%;
  height: calc(70vh - 32px);
  min-height: 460px;
  border: none;
  border-radius: 4px;
  background: #fff;
}
.preview-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 32px;
  width: 100%;
}
.preview-audio {
  width: 100%;
  max-width: 420px;
}
.glyph {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-secondary);
}
.glyph-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.glyph-image {
  color: var(--el-color-info);
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
.glyph-fallback,
.glyph-other {
  color: var(--el-text-color-placeholder);
}
.ext-badge {
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  padding: 5px 10px;
  border-radius: 3px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  letter-spacing: 1px;
  font-family: var(--el-font-family-monospace, ui-monospace, monospace);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.meta-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 18px;
}
.meta-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.meta-header-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.kind-icon {
  flex-shrink: 0;
}
.kind-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}
.ext-chip {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
  font-family: var(--el-font-family-monospace, ui-monospace, monospace);
}
.used-tag {
  margin-left: auto;
}
.meta-name {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  margin: 0;
  word-break: break-all;
}
.meta-subtitle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.meta-subtitle .dot {
  opacity: 0.5;
}
.meta-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.meta-row {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-height: 28px;
}
.meta-row-block {
  align-items: start;
}
.meta-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}
.meta-text {
  font-size: 13px;
  color: var(--el-text-color-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-mono {
  font-family: var(--el-font-family-monospace, ui-monospace, monospace);
  font-size: 12px;
}
.meta-copy {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  padding: 4px 6px 4px 10px;
  min-width: 0;
}
.meta-copy code,
.meta-copy a {
  font-family: var(--el-font-family-monospace, ui-monospace, monospace);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
  background: none;
  padding: 0;
}
.meta-copy code {
  color: var(--el-text-color-regular);
}
.meta-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
.meta-link:hover {
  text-decoration: underline;
}
.refer-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.refer-chip {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  background: var(--el-color-info-light-9);
  color: var(--el-color-info-dark-2);
  border: 1px solid var(--el-color-info-light-7);
}
.meta-actions {
  margin-top: auto;
  display: flex;
  gap: 8px;
  padding-top: 12px;
}
.action-primary {
  flex: 1;
}
@media (max-width: 900px) {
  .detail-body {
    grid-template-columns: 1fr;
    min-height: auto;
  }
  .preview-pane {
    min-height: 320px;
    max-height: 50vh;
  }
}
</style>

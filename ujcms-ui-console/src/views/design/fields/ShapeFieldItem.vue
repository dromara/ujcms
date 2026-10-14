<script lang="ts">
import { queryThemeConfig } from '@/api/composition';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';

// 活动主题调色板（palette 分组：令牌 → 颜色值）模块级缓存：同一表单/会话内多个 themeColor 字段
// 共享一次 /theme-config 请求；失败不缓存（下次打开重试），降级为无色板的纯令牌下拉。
const palettePromises: Record<string, Promise<Record<string, any>>> = {};
const loadPalette = (): Promise<Record<string, any>> => {
  const siteId = useCurrentSiteStore().getCurrentSiteId() ?? '';
  palettePromises[siteId] ??= queryThemeConfig(siteId || undefined)
    .then((config: any) => config?.palette ?? {})
    .catch(() => {
      delete palettePromises[siteId];
      return {};
    });
  return palettePromises[siteId];
};
</script>

<script setup lang="ts">
import { computed, onMounted, ref, toRefs } from 'vue';
import { useI18n } from 'vue-i18n';
import { currentLocaleTag, localizedLabel } from '@/i18n';
import { Plus, Delete, ArrowUp, ArrowDown } from '@element-plus/icons-vue';
import Tinymce from '@/components/Tinymce';
import { ImageUpload, VideoUpload } from '@/components/Upload';
import ArticleRefInput from './ArticleRefInput.vue';
import BlockRefInput from './BlockRefInput.vue';
import ChannelRefInput from './ChannelRefInput.vue';
import VoteRefInput from './VoteRefInput.vue';
import { isValidLinkUrl, normalizeLinkUrl } from './linkUrl';

defineOptions({
  name: 'ShapeFieldItem',
});
const props = defineProps({
  // 字段定义（Shape JSON 中的单个字段对象，含 type 与类型参数）
  def: { type: Object, required: true },
  modelValue: { type: [Object, String, Number, Boolean, Array], default: null },
});
// videoPoster：视频上传接口返回自动抽帧封面（image）时带 url 上抛，供父表单预填兄弟 poster 字段
const emit = defineEmits({ 'update:modelValue': null, videoPoster: null });

const { t } = useI18n();
const { def, modelValue } = toRefs(props);
const data = computed<any>({
  get: () => modelValue.value,
  set: (val) => emit('update:modelValue', val),
});

// 复合类型（media/link/badge/themeColor/object）的子键读写
const sub = (key: string): any => (data.value ?? {})[key];
const setSub = (key: string, val: any) => {
  const next = { ...data.value };
  if (val === null || val === undefined || val === '') {
    delete next[key];
  } else {
    next[key] = val;
  }
  data.value = Object.keys(next).length > 0 ? next : null;
};
// 多子键一次性读改写：连续两次 setSub 会因 prop 回流异步而丢失前一次改动
const setSubs = (patch: Record<string, any>) => {
  const next = { ...data.value };
  Object.entries(patch).forEach(([key, val]) => {
    if (val === null || val === undefined || val === '') {
      delete next[key];
    } else {
      next[key] = val;
    }
  });
  data.value = Object.keys(next).length > 0 ? next : null;
};

// 链接地址：失焦归一（剥空白、裸域名补 https://）；非法形态即时红字提示（服务端 LinkFieldType 同口径，
// 否则要到保存时才弹 400）。url 留空而其它子键已填也提示——服务端视为「url 不能为空」。
const normalizeLinkSub = (key: 'url' | 'mobileUrl') => {
  const raw = sub(key);
  if (typeof raw !== 'string') return;
  const normalized = normalizeLinkUrl(raw);
  if (normalized !== raw) setSub(key, normalized);
};
const linkUrlHint = (key: 'url' | 'mobileUrl'): string => {
  const raw = sub(key);
  const url = typeof raw === 'string' ? raw.trim() : '';
  if (url === '') {
    return key === 'url' && data.value != null && Object.keys(data.value).length > 0 ? t('compositionField.link.urlRequired') : '';
  }
  return isValidLinkUrl(url) ? '' : t('compositionField.link.urlInvalid');
};

// 枚举：选项少时用单选钮（§11.6「按选项数」）；非必填且无声明缺省时提供清除——
// 有缺省的枚举清除后立即回退缺省值（表单与渲染期同语义），与直接点选缺省项无异，按钮徒增困惑（D23）
const ENUM_RADIO_MAX = 4;
const enumAsRadio = computed<boolean>(() => def.value.type === 'enum' && (def.value.values ?? []).length <= ENUM_RADIO_MAX);
// 枚举选项显示名（component-settings §4.1 valueLabels：与 values 平行的按 locale 数组）；未声明回退原值
const enumLabel = (item: any): string => {
  const enumValues: any[] = def.value.values ?? [];
  const labels: any[] = def.value.valueLabels?.[currentLocaleTag()] ?? def.value.valueLabels?.['zh-CN'] ?? def.value.valueLabels?.en ?? [];
  const index = enumValues.indexOf(item);
  return index >= 0 && labels[index] != null ? String(labels[index]) : String(item);
};

// 主题色（§11.6）：色板取活动主题合并设置的 palette 分组（GET /api/theme-config）。
// 模块级缓存——同一表单内多个 themeColor 字段共享一次请求；取不到（SSR 站点无模板主题设置等）则无色板降级。
const palette = ref<Record<string, any>>({});
const paletteTokens = computed<string[]>(() => {
  const allowed: string[] = def.value.palette ?? [];
  return allowed.length > 0 ? allowed : Object.keys(palette.value);
});
onMounted(() => {
  if (def.value.type === 'themeColor') {
    loadPalette().then((loaded) => (palette.value = loaded));
  }
});

// media 约束（§7.2 / §11.6）：aspectRatio「宽/高」（规范格式如 "16/9"；容错 "16:9"）锁定裁剪比例；
// constraints 限制上传（minWidth/maxBytes/formats）
const mediaConstraints = computed<any>(() => def.value.constraints ?? {});
// media 无 kind 参数，按 formats 判别：全部为视频扩展名才走视频控件，否则维持图片控件（含未声明 formats）
const VIDEO_FORMATS = ['mp4', 'webm', 'ogg', 'ogv', 'm3u8', 'mov'];
const mediaKind = computed<'image' | 'video'>(() => {
  const formats: string[] = mediaConstraints.value.formats ?? [];
  return formats.length > 0 && formats.every((fmt) => VIDEO_FORMATS.includes(fmt)) ? 'video' : 'image';
});
const mediaAspect = computed<number | undefined>(() => {
  if (def.value.type !== 'media' || typeof def.value.aspectRatio !== 'string') return undefined;
  const [w = 0, h = 0] = def.value.aspectRatio.split(/[/:]/).map((part: string) => Number(part.trim()));
  return w > 0 && h > 0 ? w / h : undefined;
});
// formats（服务端按 mime 后缀匹配）→ accept：mime 供文件对话框过滤，扩展名供拖入校验
const VIDEO_MIMES: Record<string, string> = {
  mp4: 'video/mp4,.mp4',
  webm: 'video/webm,.webm',
  ogg: 'video/ogg,.ogg',
  ogv: 'video/ogg,.ogv',
  m3u8: 'application/vnd.apple.mpegurl,.m3u8',
  mov: 'video/quicktime,.mov',
};
const mediaAccept = computed<string | undefined>(() => {
  const formats: string[] = mediaConstraints.value.formats ?? [];
  if (formats.length === 0) return undefined;
  if (mediaKind.value === 'video') {
    return formats.map((fmt) => VIDEO_MIMES[fmt] ?? `.${fmt}`).join(',');
  }
  return formats
    .map((fmt) => (fmt === 'jpeg' || fmt === 'jpg' ? 'image/jpeg,.jpg,.jpeg' : `image/${fmt},.${fmt}`))
    .join(',');
});
const mediaHint = computed<string>(() => {
  if (def.value.type !== 'media') return '';
  const parts: string[] = [];
  if (typeof def.value.aspectRatio === 'string') parts.push(t('compositionField.media.aspect', { ratio: def.value.aspectRatio }));
  if (mediaConstraints.value.minWidth != null) parts.push(t('compositionField.media.minWidth', { width: mediaConstraints.value.minWidth }));
  if (mediaConstraints.value.maxBytes != null) parts.push(t('compositionField.media.maxBytes', { size: `${Math.round((mediaConstraints.value.maxBytes / 1024 / 1024) * 10) / 10} MB` }));
  if ((mediaConstraints.value.formats ?? []).length > 0) parts.push(t('compositionField.media.formats', { formats: mediaConstraints.value.formats.join(' / ') }));
  return parts.join(' · ');
});
const mediaTooSmall = computed<boolean>(() => {
  const min = mediaConstraints.value.minWidth;
  const width = data.value?.width;
  return typeof min === 'number' && typeof width === 'number' && width < min;
});
// 记录媒体实际宽高（服务端 minWidth 校验依据、渲染端防布局抖动）；加载失败或用户已换则不写
const handleMediaUrl = (url: any) => {
  if (!url) {
    setSubs({ url: null, width: null, height: null });
    return;
  }
  setSubs({ url });
  if (mediaKind.value === 'video') {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.onloadedmetadata = () => {
      if (sub('url') === url && video.videoWidth > 0) setSubs({ width: video.videoWidth, height: video.videoHeight });
    };
    video.src = url;
    return;
  }
  const img = new Image();
  img.onload = () => {
    if (sub('url') === url) setSubs({ width: img.naturalWidth, height: img.naturalHeight });
  };
  img.src = url;
};
// 视频上传响应带自动抽帧封面（image）时上抛；url 已由 update:modelValue → handleMediaUrl 写入
const handleVideoUploadSuccess = (res: any) => {
  if (res?.image) emit('videoPoster', res.image);
};

// list 类型操作
const items = computed<any[]>(() => (Array.isArray(data.value) ? data.value : []));
const addItem = () => {
  const initial = def.value.of?.type === 'object' ? {} : null;
  data.value = [...items.value, initial];
};
const setItem = (index: number, val: any) => {
  const next = [...items.value];
  next[index] = val;
  data.value = next;
};
const removeItem = (index: number) => {
  data.value = items.value.filter((item, i) => i !== index);
};
const moveItem = (index: number, offset: number) => {
  const next = [...items.value];
  const [item] = next.splice(index, 1);
  next.splice(index + offset, 0, item);
  data.value = next;
};
</script>

<template>
  <!-- 原始类型 -->
  <el-input v-if="def.type === 'text'" v-model="data" :maxlength="def.max" :show-word-limit="def.max != null" class="w-full"></el-input>
  <el-input v-else-if="def.type === 'longText'" v-model="data" type="textarea" :rows="4" :maxlength="def.max" :show-word-limit="def.max != null" class="w-full"></el-input>
  <tinymce v-else-if="def.type === 'html'" v-model="data" class="w-full" />
  <el-input-number
    v-else-if="def.type === 'number'"
    v-model="data"
    :min="def.min"
    :max="def.max"
    :step="def.integer ? 1 : undefined"
    :step-strictly="def.integer === true"
    class="w-full"
  ></el-input-number>
  <el-switch v-else-if="def.type === 'boolean'" v-model="data"></el-switch>
  <el-date-picker v-else-if="def.type === 'date'" v-model="data" type="date" value-format="YYYY-MM-DD" class="w-full"></el-date-picker>
  <el-date-picker v-else-if="def.type === 'datetime'" v-model="data" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" class="w-full"></el-date-picker>
  <!-- 枚举：选项少用单选钮，多用下拉（§11.6 按选项数） -->
  <div v-else-if="def.type === 'enum' && enumAsRadio" class="w-full flex items-center">
    <el-radio-group v-model="data">
      <el-radio v-for="item in def.values ?? []" :key="item" :value="item">{{ enumLabel(item) }}</el-radio>
    </el-radio-group>
    <el-button v-if="data != null && !def.required && def.default == null" size="small" text type="info" class="ml-2" @click="() => (data = null)">
      {{ $t('compositionField.enum.clear') }}
    </el-button>
  </div>
  <el-select v-else-if="def.type === 'enum'" v-model="data" :clearable="!def.required && def.default == null" class="w-full">
    <el-option v-for="item in def.values ?? []" :key="item" :label="enumLabel(item)" :value="item"></el-option>
  </el-select>

  <!-- 复合类型 -->
  <!-- 媒体：aspectRatio 锁定裁剪比例；constraints（minWidth/maxBytes/formats）限制上传并展示约束提示（§11.6）；
       formats 全为视频扩展名时切视频控件（video-upload 接口，自动抽帧封面经 videoPoster 上抛） -->
  <div v-else-if="def.type === 'media'" class="w-full space-y-2">
    <video-upload
      v-if="mediaKind === 'video'"
      :model-value="sub('url')"
      :file-accept="mediaAccept"
      :file-max-size="mediaConstraints.maxBytes"
      @update:model-value="handleMediaUrl"
      @upload-success="handleVideoUploadSuccess"
    ></video-upload>
    <image-upload
      v-else
      :model-value="sub('url')"
      mode="manual"
      :aspect-ratio="mediaAspect"
      :file-accept="mediaAccept"
      :file-max-size="mediaConstraints.maxBytes"
      @update:model-value="handleMediaUrl"
    ></image-upload>
    <div v-if="mediaHint" class="text-xs" :class="mediaTooSmall ? 'text-danger' : 'text-gray-400'">
      {{ mediaHint }}
      <span v-if="mediaTooSmall">— {{ $t('compositionField.media.tooSmall', { width: sub('width'), min: mediaConstraints.minWidth }) }}</span>
    </div>
    <el-input :model-value="sub('alt')" :placeholder="$t('compositionField.media.alt')" @update:model-value="(val: any) => setSub('alt', val)"></el-input>
  </div>
  <div v-else-if="def.type === 'link'" class="w-full space-y-2">
    <el-input
      :model-value="sub('url')"
      :placeholder="$t('compositionField.link.url')"
      @update:model-value="(val: any) => setSub('url', val)"
      @blur="() => normalizeLinkSub('url')"
    ></el-input>
    <div v-if="linkUrlHint('url') !== ''" class="text-xs text-danger">{{ linkUrlHint('url') }}</div>
    <template v-if="def.mobileOverride">
      <el-input
        :model-value="sub('mobileUrl')"
        :placeholder="$t('compositionField.link.mobileUrl')"
        @update:model-value="(val: any) => setSub('mobileUrl', val)"
        @blur="() => normalizeLinkSub('mobileUrl')"
      ></el-input>
      <div v-if="linkUrlHint('mobileUrl') !== ''" class="text-xs text-danger">{{ linkUrlHint('mobileUrl') }}</div>
    </template>
    <!-- 链接文字只在字段声明 withLabel 时显示：模板通常用周边标题作链接文字，LinkFieldType 保存时丢弃未声明字段的 label -->
    <el-input
      v-if="def.withLabel === true"
      :model-value="sub('label')"
      :placeholder="$t('compositionField.link.label')"
      @update:model-value="(val: any) => setSub('label', val)"
    ></el-input>
    <div v-if="def.allowTargetBlank !== false" class="flex items-center">
      <span class="mr-2 text-sm text-gray-500">{{ $t('compositionField.link.targetBlank') }}</span>
      <el-switch :model-value="sub('targetBlank') === true" @update:model-value="(val: any) => setSub('targetBlank', val === true ? true : null)"></el-switch>
    </div>
  </div>
  <div v-else-if="def.type === 'badge'" class="w-full flex items-center space-x-2">
    <el-input :model-value="sub('text')" :placeholder="$t('compositionField.badge.text')" class="w-48" @update:model-value="(val: any) => setSub('text', val)"></el-input>
    <span class="text-sm text-gray-500">{{ $t('compositionField.badge.bg') }}</span>
    <el-color-picker :model-value="sub('bg')" @update:model-value="(val: any) => setSub('bg', val)"></el-color-picker>
    <span class="text-sm text-gray-500">{{ $t('compositionField.badge.fg') }}</span>
    <el-color-picker :model-value="sub('fg')" @update:model-value="(val: any) => setSub('fg', val)"></el-color-picker>
  </div>
  <!-- 主题色：色板取活动主题调色板（GET /api/theme-config 的 palette 分组，§11.6） -->
  <el-select
    v-else-if="def.type === 'themeColor'"
    :model-value="sub('token')"
    clearable
    class="w-full"
    :placeholder="$t('compositionField.themeColor.token')"
    @update:model-value="(val: any) => setSub('token', val)"
  >
    <template #prefix>
      <span
        v-if="palette[sub('token')] != null"
        class="inline-block w-3.5 h-3.5 rounded border border-gray-300"
        :style="{ background: String(palette[sub('token')]) }"
      ></span>
    </template>
    <el-option v-for="token in paletteTokens" :key="token" :label="token" :value="token">
      <span
        class="inline-block w-3.5 h-3.5 rounded mr-2 align-middle border border-gray-300"
        :style="{ background: palette[token] != null ? String(palette[token]) : 'transparent' }"
      ></span>
      <span>{{ token }}</span>
      <span v-if="palette[token] != null" class="ml-2 text-xs text-gray-400">{{ palette[token] }}</span>
    </el-option>
  </el-select>
  <el-color-picker v-else-if="def.type === 'freeColor'" v-model="data"></el-color-picker>

  <!-- 引用类型：Phase 1 暂以 ID 输入，选择器随 Phase 2+ 投放编辑器引入 -->
  <article-ref-input v-else-if="def.type === 'articleRef'" v-model="data"></article-ref-input>
  <channel-ref-input v-else-if="def.type === 'channelRef'" v-model="data"></channel-ref-input>
  <block-ref-input v-else-if="def.type === 'blockRef'" v-model="data"></block-ref-input>
  <vote-ref-input v-else-if="def.type === 'voteRef'" v-model="data"></vote-ref-input>

  <!-- 结构类型 -->
  <div v-else-if="def.type === 'list'" class="w-full space-y-2">
    <div v-for="(item, index) in items" :key="index" class="flex items-start border border-gray-200 dark:border-gray-600 rounded p-2">
      <div class="flex-1">
        <shape-field-item :def="def.of ?? {}" :model-value="item" @update:model-value="(val: any) => setItem(index, val)"></shape-field-item>
      </div>
      <div class="ml-2 flex flex-col">
        <el-button :icon="ArrowUp" size="small" text :disabled="index === 0" @click="() => moveItem(index, -1)"></el-button>
        <el-button :icon="ArrowDown" size="small" text :disabled="index === items.length - 1" class="!ml-0" @click="() => moveItem(index, 1)"></el-button>
        <el-button :icon="Delete" size="small" text class="!ml-0" @click="() => removeItem(index)"></el-button>
      </div>
    </div>
    <el-button :icon="Plus" :disabled="def.max != null && items.length >= def.max" @click="() => addItem()">{{ $t('compositionField.list.add') }}</el-button>
  </div>
  <div v-else-if="def.type === 'object'" class="w-full space-y-2 border border-gray-200 dark:border-gray-600 rounded p-2">
    <div v-for="(fieldDef, fieldName) in def.fields ?? {}" :key="fieldName">
      <div class="text-sm text-gray-500 mb-1">{{ localizedLabel(fieldDef.label, String(fieldName)) }}<span v-if="fieldDef.required" class="text-danger">*</span></div>
      <shape-field-item :def="fieldDef" :model-value="sub(String(fieldName))" @update:model-value="(val: any) => setSub(String(fieldName), val)"></shape-field-item>
    </div>
  </div>

  <div v-else class="text-sm text-gray-400">Unsupported field type: {{ def.type }}</div>
</template>

<script setup lang="ts">
import { computed, onMounted, nextTick, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useFormItem } from 'element-plus';
// @bytemd/vue-next's editor.vue.d.ts re-exports Vue's `DefineComponent` type as
// the default export, so TS sees `Editor` as type-only under verbatimModuleSyntax
// even though it is a real runtime component. Import the value explicitly.
// @ts-expect-error -- upstream type declaration mistypes the component as a type
import { Editor } from '@bytemd/vue-next';
import gfm from '@bytemd/plugin-gfm';
import highlight from './highlight';
import math from '@bytemd/plugin-math';
import gfmZh from '@bytemd/plugin-gfm/locales/zh_Hans.json';
import gfmEn from '@bytemd/plugin-gfm/locales/en.json';
import bytemdZh from 'bytemd/locales/zh_Hans.json';
import bytemdEn from 'bytemd/locales/en.json';
import type { BytemdPlugin } from 'bytemd';
import { htmlToMarkdown, markdownToHtml, uploadImages } from './utils';
import FilePicker from '@/components/FilePicker/FilePicker.vue';
import 'bytemd/dist/index.css';
import 'highlight.js/styles/github.css';
import 'katex/dist/katex.css';

defineOptions({
  name: 'MarkdownEditor',
});

const props = defineProps({
  modelValue: { type: String, default: '' },
  html: { type: String, default: '' },
  height: { type: String, default: '300px' },
  // 只读：详情模式下不能改。CodeMirror 设为只读，工具栏不响应
  disabled: { type: Boolean, default: false },
});
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'update:html', value: string): void;
  (e: 'load', md: string, html: string): void;
  (e: 'change', md: string, html: string): void;
  (e: 'focus', event: Event): void;
  (e: 'blur', event: Event): void;
  (e: 'keydown', event: KeyboardEvent): void;
}>();

const { formItem } = useFormItem();
const { t, locale } = useI18n();

// 应用 i18n locale ('en' / 'zh-cn') → ByteMD 内置 locale 与插件 locale
const isZh = computed(() => locale.value.startsWith('zh'));
const editorLocale = computed(() => (isZh.value ? bytemdZh : bytemdEn));

// “从资源库选择”：ByteMD 工具栏 action 打开共享 FilePicker，选中后在光标处插入 markdown。
// 与内置图片上传 action（uploadImages）互补：一个上传新图，一个选用已有资源。
const pickerVisible = ref(false);
// 记录触发时的编辑器上下文（含 CodeMirror 实例），供选中回调在原光标处插入。
let pendingCtx: any = null;
// 图标路径取自 Feather 的 folder（Copyright (c) 2013-2023 Cole Bemis，MIT，见 NOTICE），未修改
const pickIcon =
  '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>';
const imageExtPattern = /\.(png|jpe?g|gif|webp|bmp|svg|ico|avif|apng)$/i;
// 图片插入为图片语法，其余插入为链接语法；扩展名先去掉查询串/锚点再判断。
const toInsertMarkdown = (item: any): string => {
  const url: string = item.url;
  const name: string = item.name || '';
  const isImage = imageExtPattern.test(url.split(/[?#]/)[0] ?? url) || imageExtPattern.test(name);
  return isImage ? `![${name}](${url})` : `[${name || url}](${url})`;
};
const handlePick = (items: any) => {
  const item = Array.isArray(items) ? items[0] : items;
  if (item?.url && pendingCtx) {
    const cm = pendingCtx.editor;
    cm.replaceSelection(toInsertMarkdown(item));
    cm.focus();
  }
  pendingCtx = null;
};
const resourcePlugin = (): BytemdPlugin => ({
  actions: [
    {
      title: t('resource.selectFromLibrary'),
      icon: pickIcon,
      position: 'left',
      handler: {
        type: 'action',
        click(ctx) {
          pendingCtx = ctx;
          pickerVisible.value = true;
        },
      },
    },
  ],
});
// Core bundle + GFM (tables/strikethrough/task lists) + highlight + math (KaTeX).
// mermaid 已移除：前台模板不渲染图表，落库 HTML 只是 <pre><code class="language-mermaid">，
// 保留只会让编辑器预览与前台不一致，且随包附带约 2.8 MB 的图表渲染代码。
// Plugin list is keyed by reference so markdownToHtml can cache the unified
// processor across keystrokes.
const plugins = computed(() => [
  gfm({ locale: isZh.value ? gfmZh : gfmEn }),
  highlight(),
  math(),
  resourcePlugin(),
]);

const onChange = (value: string) => {
  if (props.disabled) {
    return;
  }
  const nextHtml = markdownToHtml(value, plugins.value);
  if (value !== props.modelValue) {
    emit('update:modelValue', value);
  }
  if (nextHtml !== props.html) {
    emit('update:html', nextHtml);
  }
  formItem?.validate?.('change').catch((err: any) => {
    if (import.meta.env.MODE !== 'production') {
      console.warn(err);
    }
  });
  emit('change', value, nextHtml);
};

onMounted(() => {
  // 旧数据：仅有 HTML、无 markdown 源 → 转换后回写父组件
  if (!props.modelValue && props.html) {
    const md = htmlToMarkdown(props.html);
    const nextHtml = markdownToHtml(md, plugins.value);
    nextTick(() => {
      emit('update:modelValue', md);
      if (nextHtml !== props.html) {
        emit('update:html', nextHtml);
      }
    });
  }
  emit('load', props.modelValue, props.html);
});
</script>

<template>
  <div class="markdown-editor" :class="{ 'is-disabled': disabled }" @keydown="(e) => emit('keydown', e as KeyboardEvent)" @focusin="(e) => emit('focus', e)" @focusout="(e) => emit('blur', e)">
    <!-- 编辑器配置只在创建时读取：只读状态变了就重建一次 -->
    <Editor
      :key="String(disabled)"
      :value="modelValue"
      :plugins="plugins"
      :locale="editorLocale"
      :upload-images="uploadImages"
      :editor-config="{ readOnly: disabled ? 'nocursor' : false }"
      mode="split"
      @change="onChange"
    />
    <!-- z-index 高于 ByteMD 全屏层（.bytemd-fullscreen=1000），否则全屏时选择器会被编辑器盖住 -->
    <file-picker v-model="pickerVisible" :z-index="2000" @pick="handlePick" />
  </div>
</template>

<style lang="scss" scoped>
// ByteMD's right toolbar is a fixed list: [toc, help, writeOnly?, previewOnly?,
// fullscreen, github, ...plugin-right-actions]. Our plugins add only left-side
// actions, so github is always the last icon — hide it (no config hook exists to
// drop built-in toolbar icons). Help stays visible.
.markdown-editor :deep(.bytemd-toolbar-right .bytemd-toolbar-icon:last-child) {
  display: none;
}

// ByteMD's fullscreen rule (position:fixed;inset:0) ships without a z-index, so
// page content (e.g. the form's settings panel) paints over it. Lift it above
// page chrome but keep it below Element Plus overlays (which start at 2000) so
// dialogs launched from the editor stay on top.
// 只读：工具栏上的操作（加粗、插入图片……）绕过 CodeMirror 的只读直接改正文，所以整条禁用；全屏、预览在右侧，保留
.markdown-editor.is-disabled :deep(.bytemd-toolbar-left) {
  pointer-events: none;
  opacity: 0.5;
}

.markdown-editor :deep(.bytemd-fullscreen) {
  z-index: 1000;
}

.markdown-editor :deep(.bytemd) {
  height: v-bind(height);
  font-family:
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    'Segoe UI',
    Roboto,
    'Helvetica Neue',
    Arial,
    'Noto Sans',
    'PingFang SC',
    'Hiragino Sans GB',
    'Microsoft YaHei',
    'WenQuanYi Micro Hei',
    sans-serif,
    'Apple Color Emoji',
    'Segoe UI Emoji',
    'Segoe UI Symbol',
    'Noto Color Emoji';
}
</style>

<!--
  Global (un-scoped) on purpose: ByteMD renders its toolbar dropdowns into
  document.body via tippy, so they live outside this component's scoped subtree
  and a `:deep` rule can't reach them.

  ByteMD gives toolbar icons `vertical-align: top` but forgets it on the dropdown
  item icon, so the icon sits on the text baseline and the line's descender space
  pushes each item a few px past its declared `height: 32px`. That tiny overflow
  trips `.bytemd-dropdown`'s `overflow: auto`, showing a spurious scrollbar on
  short menus (e.g. the formula menu). Supplying the missing alignment fixes the
  short case and leaves the genuine max-height: 300px scrollbar intact for menus
  that really overflow.
-->
<style lang="scss">
.bytemd-dropdown-item-icon {
  vertical-align: top;
}
</style>

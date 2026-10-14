<script lang="ts">
import { defineComponent, ref, toRaw, toRefs, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated, type PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { useFormItem } from 'element-plus';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';
import { currentUser, perm } from '@/stores/useCurrentUser';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';
import { imageUploadUrl, fetchImage } from '@/api/config';

// 参考：https://www.tiny.cloud/docs/tinymce/6/vite-es6-npm/
// 组件结构（属性、初始化与销毁时机）改编自 tinymce-vue 的 Editor.ts（Copyright (c) 2018-present, Ephox, Inc.，见 NOTICE）
// Import TinyMCE
import tinymce, { type Editor, type RawEditorOptions } from 'tinymce';
// UndoLevel 在 v6 d.ts 中虽然声明但未在顶层 export，这里通过 Editor 类型派生
type UndoLevel = Editor['undoManager']['data'][number];
// Default icons are required for TinyMCE 5.3 or above
import 'tinymce/icons/default';
// A theme is also required
import 'tinymce/themes/silver';
// 模型(必须)：在 v6 中需要显式导入 model/dom
import 'tinymce/models/dom';
// 插件：v6 移除了 imagetools / paste / hr 插件，其功能已并入 core 或 image 插件
import 'tinymce/plugins/advlist';
import 'tinymce/plugins/autoresize';
import 'tinymce/plugins/autosave';
import 'tinymce/plugins/charmap';
import 'tinymce/plugins/code';
import 'tinymce/plugins/codesample';
import 'tinymce/plugins/directionality';
// emoticons：插件本体 + 表情数据库。打包环境下必须显式 import emojis，否则插件会回退到按 URL 远程拉取数据。
import 'tinymce/plugins/emoticons';
import 'tinymce/plugins/emoticons/js/emojis';
import 'tinymce/plugins/fullscreen';
import 'tinymce/plugins/image';
import 'tinymce/plugins/link';
import 'tinymce/plugins/lists';
import 'tinymce/plugins/media';
import 'tinymce/plugins/pagebreak';
import 'tinymce/plugins/preview';
import 'tinymce/plugins/quickbars';
import 'tinymce/plugins/searchreplace';
import 'tinymce/plugins/table';
import 'tinymce/plugins/visualblocks';
import 'tinymce/plugins/visualchars';

import './plugins/ai';
import './plugins/indent2em';
// 一键排版、格式刷（商业版 T2）由 tier2Tools 装载并注册：社区版（开源版）源码不含这两个插件的实现
import { hasTier2Plugin } from './tier2Tools';

import { isTextarea, uuid, initEditor } from './utils';
import { LOCKED_PLUGIN, lockedName } from './lockedButtons';
import { editorDraftPrefix, registerDraftEditor, removeLegacyEditorDrafts } from './draft';
import FilePicker from '@/components/FilePicker/FilePicker.vue';

// 旧版按随机的编辑器 ID 存下的草稿取不回来了，只占地方：加载编辑器时清一次
removeLegacyEditorDrafts();

export default defineComponent({
  name: 'TinymceEditor',
  components: { FilePicker },
  props: {
    id: { type: String, default: null },
    modelValue: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    inline: { type: Boolean },
    init: { type: Object, default: null },
    modelEvents: { type: [String, Array], default: null },
    plugins: { type: [String, Array] as PropType<string | string[]>, default: null },
    toolbar: { type: [String, Array], default: null },
    outputFormat: { type: String as PropType<'html' | 'text'>, default: 'html' },
    // 本地草稿的键（见 ./draft.ts）。不给就不启用草稿，工具栏也没有“恢复草稿”
    draftKey: { type: String, default: null },
  },
  emits: ['update:modelValue', 'input', 'change', 'blur', 'keydown'],
  setup(props, ctx) {
    const { disabled, modelValue, draftKey } = toRefs(props);
    const { t, locale } = useI18n();
    const sysConfig = useSysConfigStore();
    const element = ref<HTMLTextAreaElement>();
    const vueEditor = ref<Editor>();
    const elementId: string = props.id || uuid('tiny-vue');
    const inlineEditor: boolean = (props.init && props.init.inline) || props.inline;
    let mounting = true;
    const { formItem } = useFormItem();
    // “从资源库选择” 状态：在 setup 作用域声明，供 initWrapper 内的 file_picker_callback 与模板共享。
    const pickerVisible = ref(false);
    const pickAccept = ref<'image' | 'video' | 'audio' | 'media' | 'doc' | 'file' | 'any'>('any');
    const pickMultiple = ref(false);
    // 两条打开路径：dialog = 插入图片/媒体/链接对话框里的“浏览”（单选，回填 URL 到对话框）；
    // toolbar = 工具栏“从资源库选择”按钮（可多选，按类型直接插入正文）
    let pickMode: 'dialog' | 'toolbar' = 'dialog';
    let pendingCallback: ((value: string, meta?: Record<string, any>) => void) | null = null;
    let pendingFiletype = '';
    const VIDEO_MIME: Record<string, string> = { mp4: 'video/mp4', webm: 'video/webm', ogv: 'video/ogg', mov: 'video/quicktime', m4v: 'video/mp4' };
    const AUDIO_MIME: Record<string, string> = { mp3: 'audio/mpeg', ogg: 'audio/ogg', wav: 'audio/wav', m4a: 'audio/mp4', aac: 'audio/aac', flac: 'audio/flac' };
    const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif', 'ico'];
    const extOf = (s: string): string => {
      const clean = s.split(/[?#]/)[0] ?? '';
      const dot = clean.lastIndexOf('.');
      return dot >= 0 ? clean.slice(dot + 1).toLowerCase() : '';
    };
    // 按文件类型生成正文片段：图片 → img，视频/音频 → 与 media 插件一致的 source 写法，其余 → 链接
    const toContentHtml = (editor: Editor, item: { url: string; name?: string }): string => {
      const enc = (v: string) => editor.dom.encode(v);
      const url = enc(item.url);
      const name = enc(item.name ?? item.url);
      const ext = extOf(item.url) || extOf(item.name ?? '');
      if (IMAGE_EXT.includes(ext)) {
        return `<p><img src="${url}" alt="${name}" /></p>`;
      }
      if (VIDEO_MIME[ext]) {
        return `<p><video controls="controls" width="640"><source src="${url}" type="${VIDEO_MIME[ext]}" /></video></p>`;
      }
      if (AUDIO_MIME[ext]) {
        return `<p><audio controls="controls"><source src="${url}" type="${AUDIO_MIME[ext]}" /></audio></p>`;
      }
      return `<p><a href="${url}">${name}</a></p>`;
    };
    const handlePick = (items: any) => {
      const list: any[] = Array.isArray(items) ? items : [items];
      if (pickMode === 'toolbar') {
        const editor = vueEditor.value;
        if (editor) {
          const html = list.filter((it) => it?.url).map((it) => toContentHtml(editor, it)).join('');
          if (html) {
            editor.insertContent(html);
            editor.focus();
          }
        }
      } else {
        const item = list[0];
        if (item?.url && pendingCallback) {
          if (pendingFiletype === 'image') {
            pendingCallback(item.url, { alt: item.name ?? '' });
          } else if (pendingFiletype === 'media') {
            pendingCallback(item.url);
          } else {
            pendingCallback(item.url, { text: item.name ?? item.url });
          }
        }
      }
      pickMode = 'dialog';
      pickMultiple.value = false;
      pendingCallback = null;
      pendingFiletype = '';
    };

    const initWrapper = (): void => {
      let publicPath = import.meta.env.VITE_PUBLIC_PATH;
      if (publicPath.endsWith('/')) {
        publicPath = publicPath.substring(0, publicPath.length - 1);
      }
      // 商业版 T2（功能注册表 editorSettings）：一键排版与格式刷；低于 T2 不加载插件
      const ep2 = currentUser.commercialTier >= 2;
      // 构建里没有插件实现时（社区版源码，见 tier2Tools.ts）同样不加载：未注册的插件名，TinyMCE 会按 URL 去请求插件脚本并报加载失败
      const typesettingOn = ep2 && hasTier2Plugin('typesetting');
      const formatpainterOn = ep2 && hasTier2Plugin('formatpainter');
      // AI 助手（docs/spec/ai/editorial-assist.md §6.1）：商业版T2 + 站点写作助手开关 + ai:assist 权限。
      // 风险筛查不进工具栏，由表单侧「AI 筛查」按钮承担
      const siteAi = useCurrentSiteStore().currentSite?.ai;
      const aiWriting = ep2 && !perm('ai:assist') && siteAi?.writingAssist === true;
      // 用不了的 T2 工具：社区版加载 lockedtools 插件，显示带皇冠的锁定按钮（lockedButtons.ts），点击说明所需版本——与侧栏、设置页签的社区版预览一致；
      // T1 隐藏，付费版本不提示更高级别。锁定的 AI 按钮不看站点开关与权限：社区版两者都无从开启
      const lockedPreview = currentUser.commercialTier === 0;
      const tool = (id: string, usable: boolean): string[] => (usable ? [id] : lockedPreview ? [lockedName(id)] : []);

      // Portions derived from TinyMCE 6 (MIT, Copyright (c) 2022 Ephox Corporation DBA Tiny Technologies, Inc.; see NOTICE):
      // this image upload handler is adapted from the editor core's default upload handler.
      const handleImageUpload: NonNullable<RawEditorOptions['images_upload_handler']> = (blobInfo, progress) =>
        new Promise<string>((resolve, reject) => {
          const fileSizeLimitByte = sysConfig.upload.imageLimitByte;
          if (fileSizeLimitByte > 0 && blobInfo.blob().size > fileSizeLimitByte) {
            reject({ message: t('error.fileMaxSize', { size: `${fileSizeLimitByte / 1024 / 1024}MB` }), remove: true });
            return;
          }
          const xhr = new XMLHttpRequest();
          xhr.open('POST', imageUploadUrl);

          xhr.upload.onprogress = (e) => {
            progress((e.loaded / e.total) * 100);
          };

          xhr.onload = () => {
            if (xhr.status === 403) {
              reject({ message: `HTTP Error: ${xhr.status}`, remove: true });
              return;
            }

            if (xhr.status < 200 || xhr.status >= 300) {
              reject({ message: `HTTP Error: ${xhr.status}`, remove: true });
              return;
            }

            const json = JSON.parse(xhr.responseText);

            if (!json || typeof json.url !== 'string') {
              reject({ message: `Invalid JSON: ${xhr.responseText}`, remove: true });
              return;
            }
            resolve(json.url);
          };

          xhr.onerror = () => {
            reject({ message: `Image upload failed due to a XHR Transport error. Code: ${xhr.status}`, remove: true });
          };

          const formData = new FormData();
          formData.append('file', blobInfo.blob(), blobInfo.filename());
          // 需要水印
          formData.append('isWatermark', 'true');

          Object.entries(getAuthHeaders() as Record<string, string>).forEach(([key, value]) => xhr.setRequestHeader(key, value));
          Object.entries({ ...getSiteHeaders(), ...getLocaleHeaders() } as Record<string, string>).forEach(([key, value]) => xhr.setRequestHeader(key, value));
          xhr.send(formData);
        });

      // “从资源库选择”：file_picker_callback 打开共享 FilePicker，选中后回填 url 到 TinyMCE 对话框。
      // images_upload_handler（粘贴/拖拽 XHR 上传）保持不变，二者互补，不破坏既有上传路径。
      const handleFilePicker: NonNullable<RawEditorOptions['file_picker_callback']> = (callback, _val, meta) => {
        pickMode = 'dialog';
        pickMultiple.value = false;
        pendingCallback = callback;
        pendingFiletype = meta.filetype;
        pickAccept.value = meta.filetype === 'image' ? 'image' : meta.filetype === 'media' ? 'media' : 'file';
        pickerVisible.value = true;
      };

      const handleSetup: NonNullable<RawEditorOptions['setup']> = (editor) => {
        vueEditor.value = editor;
        // 工具栏“从资源库选择”：不必先打开插入图片/媒体对话框再点“浏览”，可多选并按类型直接插入
        editor.ui.registry.addButton('resourcelib', {
          icon: 'gallery',
          tooltip: t('resource.selectFromLibrary'),
          onAction: () => {
            pickMode = 'toolbar';
            pickAccept.value = 'any';
            pickMultiple.value = true;
            pickerVisible.value = true;
          },
        });
        editor.on('init', (event) => initEditor(event, props, ctx, editor, modelValue, formItem));
        if (props.init && typeof props.init.setup === 'function') {
          props.init.setup(editor);
        }

        const replaceString = (content: string, search: string, replace: string): string => {
          let index = 0;
          do {
            index = content.indexOf(search, index);
            if (index !== -1) {
              content = content.substring(0, index) + replace + content.substring(index + search.length);
              index += replace.length - search.length + 1;
            }
          } while (index !== -1);
          return content;
        };
        const transparentSrc = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
        const replaceImageUrl = (content: string, targetUrl: string, replacementUrl: string): string => {
          const replacementString = `src="${replacementUrl}"${replacementUrl === transparentSrc ? ' data-mce-placeholder="1"' : ''}`;
          content = replaceString(content, `src="${targetUrl}"`, replacementString);
          content = replaceString(content, 'data-mce-src="' + targetUrl + '"', 'data-mce-src="' + replacementUrl + '"');
          return content;
        };
        const replaceUrlInUndoStack = (targetUrl: string, replacementUrl: string) => {
          editor.undoManager.data.forEach((level: UndoLevel) => {
            if (level.type === 'fragmented') {
              level.fragments = level.fragments.map((fragment) => replaceImageUrl(fragment, targetUrl, replacementUrl));
            } else {
              level.content = replaceImageUrl(level.content, targetUrl, replacementUrl);
            }
          });
        };
        editor.on('SetContent', ({ content, format, paste }) => {
          if (format === 'html' && paste && typeof content === 'string' && content.includes('src="')) {
            const allImages: HTMLImageElement[] = Array.from(editor.getBody().getElementsByTagName('img'));
            const images = allImages.filter((img: HTMLImageElement) => {
              const src = img.src;
              if (src.startsWith(sysConfig.base.uploadUrlPrefix)) {
                return false;
              }
              if (img.hasAttribute('data-mce-bogus')) {
                return false;
              }
              if (img.hasAttribute('data-mce-placeholder')) {
                return false;
              }
              if (!src || src === transparentSrc) {
                return false;
              }
              if (src.indexOf('blob:') === 0) {
                return false;
              }
              if (src.indexOf('data:') === 0) {
                return false;
              }
              // src 可能为相对路径（如 "/foo.png"），new URL 会抛错。相对路径不是 SSRF 外部目标，跳过。
              let host: string;
              try {
                host = new URL(src).host;
              } catch {
                return false;
              }
              for (const domain of sysConfig.security.ssrfList) {
                if (domain === '*' || host === domain || host.endsWith('.' + domain)) {
                  return true;
                }
              }
              return false;
            });
            images.forEach(async (image) => {
              const data = await fetchImage(image.src);
              if (data.status === -1) {
                console.warn(data.message);
                return;
              }
              const resultUri = data.result.url;
              const src = editor.convertURL(resultUri, 'src');
              replaceUrlInUndoStack(image.src, resultUri);
              editor.dom.setAttribs(image, {
                src: resultUri,
                'data-mce-src': src,
              });
            });
          }
        });
      };

      const finalInit = {
        base_url: `${publicPath}/tinymce`,
        // 控制台为中文时加载中文语言包（TinyMCE 6 社区语言包 zh-Hans，与编辑器同为 MIT；见 NOTICE）；
        // 其余语言用 TinyMCE 内置英文界面（public/tinymce/langs 仅有 zh-Hans）
        ...(locale.value.startsWith('zh') ? { language_url: `${publicPath}/tinymce/langs/zh-Hans.js`, language: 'zh-Hans' } : {}),
        skin: 'oxide',
        skin_url: `${publicPath}/tinymce/skins/ui/oxide`,
        // 必须添加 '/tinymce/skins/content/default/content.min.css'。否则 fontfamily 默认不显示“系统字体”。
        content_css: [`${publicPath}/tinymce/skins/ui/oxide/content.min.css`, `${publicPath}/tinymce/skins/content/default/content.min.css`],
        // 设置编辑器默认字体
        content_style: 'body { font-size: 14px; }',
        menubar: false,
        // 工具栏模式，默认 'floating'，超出一行的工具栏会隐藏，点击 '...' 按钮展开。设置为 'warp' 则全部显示。
        // toolbar_mode: 'wrap',
        plugins: [
          'advlist', 'autoresize', ...(props.draftKey ? ['autosave'] : []), 'charmap', 'code', 'codesample', 'directionality',
          'emoticons', 'fullscreen', 'image', 'lists', 'link', 'media', 'pagebreak', 'preview', 'quickbars',
          'searchreplace', 'table', 'visualblocks', 'visualchars', 'indent2em',
          ...(aiWriting ? ['ai'] : []),
          ...(typesettingOn ? ['typesetting'] : []), ...(formatpainterOn ? ['formatpainter'] : []),
          ...(lockedPreview ? [LOCKED_PLUGIN] : []),
        ].join(' '),
        // 工具栏分组用 '|' 分隔；分组内按钮用空格分隔。
        toolbar: [
          ['fullscreen', 'code', ...tool('ai', aiWriting), ...tool('typesetting', typesettingOn)].join(' '),
          [...tool('formatpainter', formatpainterOn), 'selectall', 'removeformat'].join(' '),
          'bold italic underline strikethrough',
          'alignleft aligncenter alignright alignjustify',
          'image media link resourcelib',
          'blockquote codesample table',
          'bullist numlist indent2em',
          'outdent indent lineheight',
          'forecolor backcolor',
          'fontfamily fontsize blocks',
          'superscript subscript hr',
          'charmap emoticons',
          'ltr rtl visualblocks visualchars',
          [...(props.draftKey ? ['restoredraft'] : []), 'undo redo'].join(' '),
          'preview searchreplace',
        ].join(' | '),
        font_family_formats:
          '宋体=SimSun; 仿宋GB2312=FangSong_GB2312,FangSong,STFangsong; 微软雅黑=Microsoft YaHei; 楷体=SimKai,KaiTi; 黑体=SimHei; 隶书=SimLi,LiSu; Andale Mono=andale mono,times;Arial=arial,helvetica,sans-serif;' +
          'Arial Black=arial black,avant garde;Comic Sans MS=comic sans ms,sans-serif;Helvetica=helvetica;Impact=impact,chicago;Times New Roman=times new roman,times',
        font_size_formats: '8px 10px 12px 14px 16px 18px 24px 36px 48px 64px 72px 96px',
        // quickbars_selection_toolbar: 'bold italic | h2 h3 blockquote | link',
        // 选中文字时的快捷工具栏；AI 按钮仅含选区模式（润色/扩写/校对），与主工具栏的 ai 同一开关
        quickbars_selection_toolbar: ['bold italic', 'quicklink h2 h3 blockquote quickimage quicktable', ...(aiWriting ? ['aiselection'] : [])].join(' | '),
        quickbars_insert_toolbar: false,
        paste_data_images: true,
        image_uploadtab: false,
        image_advtab: true,
        image_caption: true,
        images_file_types: sysConfig.upload.imageTypes,
        min_height: 300,
        max_height: 500,
        convert_urls: false,
        autosave_ask_before_unload: false,
        // 草稿留一天；默认 20 分钟，隔夜回来就没了
        ...(props.draftKey ? { autosave_prefix: editorDraftPrefix(props.draftKey), autosave_retention: '1440m', autosave_interval: '15s' } : {}),
        ai_writing_assist: aiWriting,
        ...props.init,
        readonly: props.disabled,
        selector: `#${elementId}`,
        inline: inlineEditor,
        images_upload_handler: handleImageUpload,
        file_picker_callback: handleFilePicker,
        setup: handleSetup,
        branding: false,
      };
      if (isTextarea(element.value)) {
        element.value.style.visibility = '';
      }
      tinymce.init({ toolbar_mode: 'sliding', ...finalInit });
      mounting = false;
    };
    // 换了一条数据（上一条、下一条、新增保存后有了 ID）：草稿换到新的键下。新数据的正文随后载入，
    // 载入不算修改——清掉 dirty，免得把没动过的正文当草稿存下
    watch(draftKey, () => {
      const editor = vueEditor.value;
      if (editor != null && draftKey.value) {
        editor.options.set('autosave_prefix', editorDraftPrefix(draftKey.value));
        editor.setDirty(false);
      }
    });
    watch(disabled, () => {
      // v6 起 editor.setMode(...) 已废弃，改用 editor.mode.set(...)。
      // 与官方 @tinymce/tinymce-vue 包行为保持一致。
      if (vueEditor.value != null) {
        vueEditor.value.mode.set(disabled.value ? 'readonly' : 'design');
      }
    });
    onMounted(() => {
      initWrapper();
    });
    // toRaw：ref 里取出的是响应式代理，TinyMCE 按对象本身从它的编辑器列表里摘除——传代理进去，编辑器销毁了，
    // 列表里却一直留着这一项
    // 草稿被清除时（保存成功、确认放弃、退出登录），“有改动”标记一并清掉，见 draft.ts
    const unregisterDraft = registerDraftEditor(
      () => draftKey.value,
      () => toRaw(vueEditor.value)?.setDirty(false),
    );
    onBeforeUnmount(() => {
      unregisterDraft();
      if (vueEditor.value) tinymce.remove(toRaw(vueEditor.value));
    });
    if (!inlineEditor) {
      onActivated(() => {
        if (!mounting) {
          initWrapper();
        }
      });
      onDeactivated(() => {
        if (vueEditor.value) tinymce.remove(toRaw(vueEditor.value));
      });
    }
    return { element, elementId, editor: vueEditor, pickerVisible, pickAccept, pickMultiple, handlePick };
  },
});
</script>

<template>
  <div>
    <!-- 初始隐藏 textarea，避免 TinyMCE 初始化前的短暂闪烁；init 完成后由 initWrapper 重置可见性。-->
    <textarea :id="elementId" ref="element" style="visibility: hidden"></textarea>
    <!-- z-index 高于 TinyMCE 对话框层（.tox-tinymce-aux=1300），否则选择器会被“插入图片/媒体/链接”弹窗盖住 -->
    <file-picker v-model="pickerVisible" :accept="pickAccept" :multiple="pickMultiple" :z-index="2000" @pick="handlePick" />
  </div>
</template>

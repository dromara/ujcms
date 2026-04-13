<script lang="ts">
import { defineComponent, ref, toRefs, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated, PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { useFormItem } from 'element-plus';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { currentUser } from '@/stores/useCurrentUser';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import { imageUploadUrl, fileUploadUrl, mediaUploadUrl, fetchImage } from '@/api/config';

// 参考：https://www.tiny.cloud/docs/advanced/usage-with-module-loaders/webpack/webpack_es6_npm/
// 参考：https://github.com/tinymce/tinymce-vue/blob/main/src/main/ts/components/Editor.ts
// Import TinyMCE
import tinymce from 'tinymce';
// Default icons are required for TinyMCE 5.3 or above
import 'tinymce/icons/default';
// A theme is also required
import 'tinymce/themes/silver';
// Any plugins you want to use has to be imported
import 'tinymce/plugins/advlist';
// import 'tinymce/plugins/anchor';
// import 'tinymce/plugins/autolink';
import 'tinymce/plugins/autoresize';
import 'tinymce/plugins/autosave';
import 'tinymce/plugins/charmap';
import 'tinymce/plugins/code';
import 'tinymce/plugins/codesample';
import 'tinymce/plugins/directionality';
import 'tinymce/plugins/fullscreen';
import 'tinymce/plugins/hr';
// import 'tinymce/plugins/insertdatetime';
import 'tinymce/plugins/image';
import 'tinymce/plugins/imagetools';
import 'tinymce/plugins/link';
import 'tinymce/plugins/lists';
import 'tinymce/plugins/media';
// import 'tinymce/plugins/nonbreaking';
// import 'tinymce/plugins/noneditable';
import 'tinymce/plugins/pagebreak';
import 'tinymce/plugins/paste';
import 'tinymce/plugins/preview';
// import 'tinymce/plugins/print';
import 'tinymce/plugins/quickbars';
// import 'tinymce/plugins/save';
import 'tinymce/plugins/searchreplace';
// import 'tinymce/plugins/spellchecker';
// import 'tinymce/plugins/tabfocus';
import 'tinymce/plugins/table';
// import 'tinymce/plugins/template';
// import 'tinymce/plugins/textpattern';
// import 'tinymce/plugins/toc';
import 'tinymce/plugins/visualblocks';
import 'tinymce/plugins/visualchars';
// import 'tinymce/plugins/wordcount';

import './plugins/indent2em';
import './plugins/typesetting';

import { isTextarea, uuid, initEditor } from './utils';

export default defineComponent({
  name: 'TinymceEditor',
  props: {
    id: { type: String, default: null },
    modelValue: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
    inline: { type: Boolean },
    init: { type: Object, default: null },
    modelEvents: { type: [String, Array], default: null },
    plugins: { type: [String, Array] as PropType<string | string[]>, default: null },
    toolbar: { type: [String, Array], default: null },
    outputFormat: {
      type: String as PropType<'html' | 'text'>,
      default: 'html',
      validator: (prop: string) => prop === 'html' || prop === 'text',
    },
  },
  emits: ['update:modelValue', 'input', 'change', 'blur', 'keydown'],
  setup(props, ctx) {
    const { disabled, modelValue } = toRefs(props);
    const { t } = useI18n();
    const sysConfig = useSysConfigStore();
    const element = ref<any>();
    const vueEditor = ref<any>();
    const elementId: string = props.id || uuid('tiny-vue');
    const inlineEditor: boolean = (props.init && props.init.inline) || props.inline;
    let mounting = true;
    const { formItem } = useFormItem();

    const initWrapper = (): void => {
      let publicPath = import.meta.env.VITE_PUBLIC_PATH;
      if (publicPath.endsWith('/')) {
        publicPath = publicPath.substring(0, publicPath.length - 1);
      }
      const ep2 = currentUser.epRank >= 2;
      const finalInit = {
        base_url: '/tinymce',
        language_url: `${publicPath}/tinymce/langs/zh_CN.js`,
        language: 'zh_CN',
        skin: 'oxide',
        skin_url: `${publicPath}/tinymce/skins/ui/oxide`,
        // 必须添加 '/tinymce/skins/content/default/content.min.css'。否则 fontselect 默认不显示“系统字体”。
        content_css: [`${publicPath}/tinymce/skins/ui/oxide/content.min.css`, `${publicPath}/tinymce/skins/content/default/content.min.css`],
        // 设置编辑器默认字体
        content_style: 'body { font-size: 14px; }',
        menubar: false,
        // 工具栏模式，默认 'floating'，超出一行的工具栏会隐藏，点击 '...' 按钮展开。设置为 'warp' 则全部显示。
        // toolbar_mode: 'wrap',
        plugins:
          'advlist autoresize autosave charmap code codesample directionality fullscreen hr image imagetools lists link media pagebreak paste preview quickbars ' +
          `searchreplace table visualblocks visualchars indent2em ${ep2 ? 'typesetting' : ''}`,
        toolbar:
          `fullscreen code ${
            ep2 ? '| typesetting' : ''
          } | bold italic underline strikethrough | alignleft aligncenter alignright alignjustify | selectall removeformat pastetext | ` +
          'quickimage media | blockquote codesample table | bullist numlist | indent2em outdent indent lineheight | forecolor backcolor | fontselect fontsizeselect formatselect | ' +
          'superscript subscript charmap | hr | ltr rtl | visualblocks visualchars | restoredraft undo redo | preview searchreplace',
        font_formats:
          '宋体=SimSun; 微软雅黑=Microsoft YaHei; 楷体=SimKai,KaiTi; 黑体=SimHei; 隶书=SimLi,LiSu; Andale Mono=andale mono,times;Arial=arial,helvetica,sans-serif;' +
          'Arial Black=arial black,avant garde;Comic Sans MS=comic sans ms,sans-serif;Helvetica=helvetica;Impact=impact,chicago;Times New Roman=times new roman,times',
        fontsize_formats: '8px 10px 12px 14px 16px 18px 24px 36px 48px 64px 72px 96px',
        quickbars_selection_toolbar: 'bold italic | h2 h3 blockquote | link',
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
        ...props.init,
        images_upload_handler(blobInfo: any, success: any, failure: any, progress: any) {
          const fileSizeLimitByte = sysConfig.upload.imageLimitByte;
          if (fileSizeLimitByte > 0 && blobInfo.blob().size > fileSizeLimitByte) {
            failure(t('error.fileMaxSize', { size: `${fileSizeLimitByte / 1024 / 1024}MB` }), { remove: true });
            return;
          }
          const xhr = new XMLHttpRequest();
          xhr.open('POST', imageUploadUrl);

          xhr.upload.onprogress = (e) => {
            progress((e.loaded / e.total) * 100);
          };

          xhr.onload = () => {
            if (xhr.status === 403) {
              failure(`HTTP Error: ${xhr.status}`, { remove: true });
              return;
            }

            if (xhr.status < 200 || xhr.status >= 300) {
              failure(`HTTP Error: ${xhr.status}`, { remove: true });
              return;
            }

            const json = JSON.parse(xhr.responseText);

            if (!json || typeof json.url !== 'string') {
              failure(`Invalid JSON: ${xhr.responseText}`, { remove: true });
              return;
            }
            success(json.url);
          };

          xhr.onerror = () => {
            failure(`Image upload failed due to a XHR Transport error. Code: ${xhr.status}`, { remove: true });
          };

          const formData = new FormData();
          formData.append('file', blobInfo.blob(), blobInfo.filename());
          // 需要水印
          formData.append('isWatermark', 'true');

          Object.entries(getAuthHeaders()).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));
          Object.entries(getSiteHeaders()).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));
          xhr.send(formData);
        },

        file_picker_callback(callback: any, val: any, meta: any) {
          const input = document.createElement('input');
          input.setAttribute('type', 'file');

          let fileSizeLimtByte = 0;
          let uploadUrl: string;
          if (meta.filetype === 'image') {
            fileSizeLimtByte = sysConfig.upload.imageLimitByte;
            input.setAttribute('accept', sysConfig.upload.imageInputAccept);
            uploadUrl = imageUploadUrl;
            // input.setAttribute('accept', 'image/*');
          } else if (meta.filetype === 'media') {
            fileSizeLimtByte = sysConfig.upload.mediaLimitByte;
            input.setAttribute('accept', sysConfig.upload.mediaInputAccept);
            uploadUrl = mediaUploadUrl;
            // input.setAttribute('accept', 'video/*');
          } else {
            fileSizeLimtByte = sysConfig.upload.fileLimitByte;
            input.setAttribute('accept', sysConfig.upload.fileInputAccept);
            uploadUrl = fileUploadUrl;
          }

          /*
            Note: In modern browsers input[type="file"] is functional without
            even adding it to the DOM, but that might not be the case in some older
            or quirky browsers like IE, so you might want to add it to the DOM
            just in case, and visually hide it. And do not forget do remove it
            once you do not need it anymore.
          */

          input.onchange = (event: Event) => {
            const { files } = event.target as HTMLInputElement;
            const file = files?.item(0);
            if (!file) return;
            if (fileSizeLimtByte > 0 && file.size > fileSizeLimtByte) {
              tinymce.activeEditor.windowManager.alert(t('error.fileMaxSize', { size: `${fileSizeLimtByte / 1024 / 1024}MB` }));
              return;
            }
            const xhr = new XMLHttpRequest();
            xhr.open('POST', uploadUrl);

            // xhr.upload.onprogress = (e) => {
            //   progress((e.loaded / e.total) * 100);
            // };

            xhr.onload = () => {
              if (xhr.status === 403) {
                tinymce.activeEditor.windowManager.alert(`HTTP Error: ${xhr.status}`);
                return;
              }

              if (xhr.status < 200 || xhr.status >= 300) {
                tinymce.activeEditor.windowManager.alert(`HTTP Error: ${xhr.status}`);
                return;
              }

              const json = JSON.parse(xhr.responseText);

              if (!json || typeof json.url !== 'string') {
                tinymce.activeEditor.windowManager.alert(`Invalid JSON: ${xhr.responseText}`);
                return;
              }

              if (meta.filetype === 'image') {
                callback(json.url, { alt: '' });
              } else if (meta.filetype === 'media') {
                callback(json.url);
                // callback('movie.mp4', { source2: 'alt.ogg', poster: 'image.jpg' });
              } else {
                callback(json.url, { text: json.name });
              }
            };

            xhr.onerror = () => {
              tinymce.activeEditor.windowManager.alert(`Image upload failed due to a XHR Transport error. Code: ${xhr.status}`);
            };

            const formData = new FormData();
            formData.append('file', file, file.name);

            Object.entries(getAuthHeaders()).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));
            xhr.send(formData);
          };

          input.click();
        },

        readonly: props.disabled,
        selector: `#${elementId}`,
        // plugins: mergePlugins(props.init && props.init.plugins, props.plugins),
        // toolbar: props.toolbar || (props.init && props.init.toolbar),
        inline: inlineEditor,
        setup: (editor: any) => {
          vueEditor.value = editor;
          editor.on('init', (event: Event) => initEditor(event, props, ctx, editor, modelValue, formItem));
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
            editor.undoManager.data.forEach((level: any) => {
              if (level.type === 'fragmented') {
                level.fragments = level.fragments.map((fragment: any) => replaceImageUrl(fragment, targetUrl, replacementUrl));
              } else {
                level.content = replaceImageUrl(level.content, targetUrl, replacementUrl);
              }
            });
          };
          editor.on('SetContent', ({ content, format, paste }: { content: string; format?: string; paste?: boolean; selection?: boolean }) => {
            if (format === 'html' && paste && content.includes('src="')) {
              const images = Array.from(editor.getBody().getElementsByTagName('img')).filter((img: any) => {
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
                const host = new URL(src).host;
                for (const domain of sysConfig.security.ssrfList) {
                  if (domain === '*' || host === domain || host.endsWith('.' + domain)) {
                    return true;
                  }
                }
                return false;
              });
              images.forEach(async (image: any) => {
                const data = await fetchImage(image.src);
                if (data.status === -1) {
                  console.warn(data.message);
                  return;
                }
                const resultUri = data.result.url;
                const src = editor.convertURL(resultUri, 'src');
                replaceUrlInUndoStack(image.src, resultUri);
                editor.$(image).attr({
                  src: resultUri,
                  'data-mce-src': src,
                });
              });
            }
          });
        },
        branding: false,
      };
      if (isTextarea(element.value)) {
        element.value.style.visibility = '';
      }
      tinymce.init({ toolbar_mode: 'sliding', ...finalInit });
      mounting = false;
    };
    watch(disabled, () => {
      if (vueEditor.value != null) {
        vueEditor.value.setMode(disabled.value ? 'readonly' : 'design');
      }
    });
    onMounted(async () => {
      initWrapper();
    });
    onBeforeUnmount(() => {
      tinymce.remove(vueEditor.value);
    });
    if (!inlineEditor) {
      onActivated(() => {
        if (!mounting) {
          initWrapper();
        }
      });
      onDeactivated(() => {
        tinymce.remove(vueEditor.value);
      });
    }
    return { element, elementId, editor: vueEditor };
  },
});
</script>

<template>
  <div>
    <textarea :id="elementId" ref="element"></textarea>
  </div>
</template>

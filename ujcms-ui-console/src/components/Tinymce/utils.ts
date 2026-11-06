/**
 * Copyright (c) 2018-present, Ephox, Inc.
 *
 * This source code is licensed under the Apache 2 license found in the
 * LICENSE file in the root directory of this source tree.
 *
 */
/*
 * Modified for UJCMS by 南昌蓝智科技有限公司 (https://www.ujcms.com). Derived from tinymce-vue: src/main/ts/Utils.ts
 * (Apache License 2.0, licenses/Apache-2.0.txt). First modified before 2026-04-19.
 */
import { type Ref, watch } from 'vue';
import type { Editor, EditorEvent } from 'tinymce';

const validEvents = [
  'onActivate',
  'onAddUndo',
  'onBeforeAddUndo',
  'onBeforeExecCommand',
  'onBeforeGetContent',
  'onBeforeRenderUI',
  'onBeforeSetContent',
  'onBeforePaste',
  'onBlur',
  'onChange',
  'onClearUndos',
  'onClick',
  'onContextMenu',
  'onCopy',
  'onCut',
  'onDblclick',
  'onDeactivate',
  'onDirty',
  'onDrag',
  'onDragDrop',
  'onDragEnd',
  'onDragGesture',
  'onDragOver',
  'onDrop',
  'onExecCommand',
  'onFocus',
  'onFocusIn',
  'onFocusOut',
  'onGetContent',
  'onHide',
  'onInit',
  'onKeyDown',
  'onKeyPress',
  'onKeyUp',
  'onLoadContent',
  'onMouseDown',
  'onMouseEnter',
  'onMouseLeave',
  'onMouseMove',
  'onMouseOut',
  'onMouseOver',
  'onMouseUp',
  'onNodeChange',
  'onObjectResizeStart',
  'onObjectResized',
  'onObjectSelected',
  'onPaste',
  'onPostProcess',
  'onPostRender',
  'onPreProcess',
  'onProgressState',
  'onRedo',
  'onRemove',
  'onReset',
  'onSaveContent',
  'onSelectionChange',
  'onSetAttrib',
  'onSetContent',
  'onShow',
  'onSubmit',
  'onUndo',
  'onVisualAid',
];

const isValidKey = (key: string): boolean => validEvents.map((event) => event.toLowerCase()).indexOf(key.toLowerCase()) !== -1;

const bindHandlers = (initEvent: EditorEvent<unknown>, listeners: Record<string, unknown>, editor: Editor): void => {
  Object.keys(listeners)
    .filter(isValidKey)
    .forEach((key: string) => {
      const handler = listeners[key];
      if (typeof handler === 'function') {
        if (key === 'onInit') {
          handler(initEvent, editor);
        } else {
          editor.on(key.substring(2), (e) => handler(e, editor));
        }
      }
    });
};

const bindModelHandlers = (props: any, ctx: any, editor: Editor, modelValue: Ref<string>, formItem: any): void => {
  const modelEvents = props.modelEvents ? props.modelEvents : null;
  const normalizedEvents = Array.isArray(modelEvents) ? modelEvents.join(' ') : modelEvents;

  // outputFormat 永远是 'html' 或 'text'，editor.getContent 在这两种格式下返回 string；
  // 但官方类型签名退化为 `string | AstNode`，需要在比较前断言为 string。
  const currentContent = (): string => editor.getContent({ format: props.outputFormat }) as unknown as string;
  // 最近一次从外部载入的正文：raw 是载入的原串（服务端存的是净化器的序列化），canonical 是编辑器对同一正文的序列化，
  // 两者几乎从不逐字相同。编辑器只在有事件时才回传，回传的又是 canonical，于是回传一次表单就再也等不回基线——
  // 输入一个字再删掉、甚至只在正文里按一下方向键（keyup），表单就成了“未保存”。所以正文回到载入时的样子就回传原串
  let loaded: { raw: string; canonical: string } | undefined;
  const loadContent = (val: string): void => {
    editor.setContent(val);
    loaded = { raw: val, canonical: currentContent() };
  };
  // 模型值与编辑器已经一致：值就是编辑器现在的序列化；或者值是载入的原串，而编辑器仍停在载入时的样子
  const inSync = (val: string): boolean => {
    const content = currentContent();
    return val === content || (loaded != null && val === loaded.raw && content === loaded.canonical);
  };
  loadContent(modelValue.value ?? '');

  watch(modelValue, (val: string, prevVal: string) => {
    if (editor && typeof val === 'string' && val !== prevVal && !inSync(val)) {
      loadContent(val);
    }
  });

  // 要加上 paste 事件，否则首次粘贴时内容会为空。使用 'change input paste undo redo' 在快速剪切、粘贴的情况下，有可能还是会出现 '必填字段' 的错误提示。
  editor.on(normalizedEvents ?? 'change keyup undo redo', () => {
    const canonical = currentContent();
    // 正文仍是（或又回到）载入时的样子：回传原串；离开过就回传编辑器的序列化
    const restored = loaded != null && canonical === loaded.canonical ? loaded : undefined;
    // “有改动”标记随正文走：在载入时的样子上没有未保存的内容，autosave 插件不再把它当草稿存下（见 draft.ts）；
    // 离开了就标回来——TinyMCE 只在一段连续输入的第一个字上标记，删掉再打的字它不会再标
    if (editor.isDirty() === (restored != null)) {
      editor.setDirty(restored == null);
    }
    const content = restored != null ? restored.raw : canonical;
    ctx.emit('update:modelValue', content);
    ctx.emit('input', content);
    ctx.emit('change', content);
    formItem?.validate?.('change')?.catch((err: unknown) => {
      if (import.meta.env.MODE !== 'production') {
        console.warn(err);
      }
    });
  });

  editor.on('blur', (event) => {
    ctx.emit('blur', event);
  });
  editor.on('keydown', (event) => {
    ctx.emit('keydown', event);
  });
};

const initEditor = (initEvent: EditorEvent<unknown>, props: any, ctx: any, editor: Editor, modelValue: Ref<string>, formItem: any): void => {
  bindModelHandlers(props, ctx, editor, modelValue, formItem);
  bindHandlers(initEvent, ctx.attrs, editor);
};

let unique = 0;
const uuid = (prefix: string): string => {
  const time = Date.now();
  const random = Math.floor(Math.random() * 1000000000);
  unique += 1;
  return `${prefix}_${random + unique}${String(time)}`;
};

const isTextarea = (element: Element | null | undefined): element is HTMLTextAreaElement => element != null && element.tagName.toLowerCase() === 'textarea';

const normalizePluginArray = (plugins?: string | string[]): string[] => {
  if (typeof plugins === 'undefined' || plugins === '') {
    return [];
  }

  return Array.isArray(plugins) ? plugins : plugins.split(' ');
};

const mergePlugins = (initPlugins: string | string[], inputPlugins?: string | string[]): string[] => normalizePluginArray(initPlugins).concat(normalizePluginArray(inputPlugins));

export { bindHandlers, bindModelHandlers, initEditor, isValidKey, uuid, isTextarea, mergePlugins };

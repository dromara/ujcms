/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue';
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

declare module 'bpmn-js/lib/Modeler';
declare module 'bpmn-js/lib/Viewer';
declare module 'bpmn-js/lib/util/ModelUtil';
declare module '@/components/bpmnjs/descriptors/flowable';
declare module '@/components/bpmnjs/palette';
declare module '@/components/bpmnjs/context-pad';
// highlight.js 的 exports 未给子路径提供类型声明；lib/common 与主入口 API 一致（仅注册的语言不同）
declare module 'highlight.js/lib/common' {
  import type { HLJSApi } from 'highlight.js';
  const hljs: HLJSApi;
  export default hljs;
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE?: string;
  readonly VITE_APP_NAME: string;
  readonly VITE_BASE_API: string;
  readonly VITE_PROXY_API: string;
  readonly VITE_PUBLIC_PATH: string;
  readonly VITE_I18N_LOCALE: string;
  readonly VITE_I18N_FALLBACK_LOCALE: string;
  readonly MODE: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

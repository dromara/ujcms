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

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string;
  readonly VITE_APP_NAME: string;
  readonly VITE_BASE_API: string;
  readonly VITE_PUBLIC_PATH: string;
  readonly VITE_I18N_LOCALE: string;
  readonly VITE_I18N_FALLBACK_LOCALE: string;
  readonly VITE_USE_MOCK: string;
  readonly MODE: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

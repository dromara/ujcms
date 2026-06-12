import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { ElRow } from 'element-plus';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';
import { initRefreshInterval } from '@/stores/useCurrentUser';
import { useSysConfigStore } from '@/stores/sysConfigStore';
import App from './App.vue';
import router from './router';
import i18n from './i18n';
import '@/permission';

import '@/styles/tailwind.scss';
import '@/styles/index.scss';

// 初始化RefreshToken自动刷新
initRefreshInterval();

const pinia = createPinia().use(piniaPluginPersistedstate);
const app = createApp(App)
  .use(router)
  .use(pinia)
  // tinymce 对话框的层级太低，必须调低 ElementPlus 的 对话框层级（默认为2000）
  // .use(ElementPlus, { zIndex: 500 })
  .use(i18n);
// draggable 的 tag 属性使用了 ElRow，属于动态加载组件，在按需加载的情况下，必须全局注册才可使用
app.component('ElRow', ElRow);
app.mount('#app');

// 初始化系统配置
useSysConfigStore().initConfig();

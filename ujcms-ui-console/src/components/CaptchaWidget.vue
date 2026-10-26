<script setup lang="ts">
// 人机验证组件：加载与前台主题共用的 /static/captcha.js（docs/spec/captcha/plan.md），按全局设置渲染滑块 / 字符 /
// 第三方厂商。脚本地址由 VITE_BASE_API 推出（/api → /static/captcha.js；生产的 ../api → ../static/captcha.js），
// 与后台同源，不受控制台 CSP 限制；厂商域名由服务端按当前选中的厂商写进 CSP。颜色跟随 Element Plus 主色。
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';

const props = defineProps<{
  /** 互动场景（静默直至升级）；登录等始终可见的场景不传 */
  scope?: string;
  /** auto | slider | char */
  type?: string;
}>();
const emit = defineEmits<{ (e: 'change', state: { type: string | null; solved: boolean; visible: boolean }): void }>();

const { locale } = useI18n();
const el = ref<HTMLElement>();
let instance: any = null;

const apiBase = (import.meta.env.VITE_BASE_API || '/api').replace(/\/+$/, '');
const scriptUrl = apiBase.replace(/\/api$/, '') + '/static/captcha.js';

const loadScript = (): Promise<void> => {
  const w = window as any;
  if (w.UjcmsCaptcha) return Promise.resolve();
  if (!w.__ujcmsCaptchaLoading) {
    w.__ujcmsCaptchaLoading = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = scriptUrl;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        delete w.__ujcmsCaptchaLoading;
        reject(new Error('captcha.js failed to load'));
      };
      document.head.appendChild(script);
    });
  }
  return w.__ujcmsCaptchaLoading;
};

onMounted(async () => {
  await loadScript();
  if (!el.value) return;
  instance = (window as any).UjcmsCaptcha.mount(el.value, {
    api: apiBase,
    scope: props.scope,
    type: props.type ?? 'auto',
    lang: String(locale.value).toLowerCase().startsWith('en') ? 'en' : 'zh',
    onChange: (state: { type: string | null; solved: boolean; visible: boolean }) => emit('change', state),
  });
});

onBeforeUnmount(() => {
  instance?.destroy();
  instance = null;
});

defineExpose({
  /** 当前答案；可见题未完成时 reject（message 为可直接展示的提示） */
  answer: (): Promise<{ captcha: string; captchaToken: string }> => {
    if (!instance) return Promise.reject(new Error('captcha not ready'));
    return instance.answer();
  },
  refresh: () => instance?.refresh(),
  solved: () => !!instance?.solved,
});
</script>

<template>
  <div ref="el" class="captcha-widget"></div>
</template>

<style scoped>
.captcha-widget {
  --ujc-primary: var(--el-color-primary);
  --ujc-line: var(--el-border-color);
  --ujc-text: var(--el-text-color-regular);
  --ujc-muted: var(--el-text-color-secondary);
  --ujc-radius: var(--el-border-radius-base);
  width: 100%;
}
</style>

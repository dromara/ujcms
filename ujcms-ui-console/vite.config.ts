import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv, ConfigEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import vueDevTools from 'vite-plugin-vue-devtools';
import legacy from '@vitejs/plugin-legacy';
import vueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';
import { viteMockServe } from 'vite-plugin-mock';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig(({ mode }: ConfigEnv) => {
  // 加载 .env 文件
  const env = loadEnv(mode, process.cwd());
  return {
    base: env.VITE_PUBLIC_PATH,
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler',
        },
      },
    },
    server: {
      port: Number(env.VITE_PORT),
      proxy: {
        [env.VITE_BASE_API]: {
          target: env.VITE_PROXY_API,
          changeOrigin: true,
        },
        [env.VITE_BASE_UPLOADS]: {
          target: env.VITE_PROXY_UPLOADS,
          changeOrigin: true,
        },
        [env.VITE_BASE_TEMPLATES]: {
          target: env.VITE_PROXY_TEMPLATES,
          changeOrigin: true,
        },
      },
    },
    build: {
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        onwarn(warning, warn) {
          // Suppress type-only import warnings from Rollup strict named export checking.
          // These are TypeScript types (PropType, RouteRecordRaw, etc.) that have no runtime value.
          if (warning.code === 'MISSING_EXPORT') return;
          warn(warning);
        },
      },
    },
    plugins: [
      vue(),
      vueDevTools(),
      legacy({
        targets: ['defaults', 'not IE 11'],
      }),
      vueI18nPlugin({
        include: [fileURLToPath(new URL('./locales/**', import.meta.url))],
      }),
      viteMockServe({
        ignore: /^_/,
        mockPath: 'mock',
        enable: env.VITE_USE_MOCK === 'true',
      }),
      AutoImport({
        resolvers: [ElementPlusResolver()],
      }),
      Components({
        resolvers: [ElementPlusResolver()],
      }),
    ],
  };
});

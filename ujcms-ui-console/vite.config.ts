import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv, ConfigEnv, ProxyOptions, UserConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import vueDevTools from 'vite-plugin-vue-devtools';
import vueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';
import { viteStaticCopy } from 'vite-plugin-static-copy';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import license from 'rollup-plugin-license';
import type { Dependency } from 'rollup-plugin-license';
import type { Plugin } from 'vite';

// 前端第三方依赖声明（docs/spec/open-source-compliance/plan.md）：构建时把打进产物的每个 npm 包的名称、版本、许可证、
// 作者与许可证全文写成 THIRD-PARTY-LICENSES.txt，随 dist / webapp/cp 分发。许可证不在 ALLOWED_LICENSES 内即构建失败；
// bpmn-js 按名放行（bpmn.io 许可：MIT 加“水印不得去除”条款，见 NOTICE）。scripts/check-licenses.mjs 在 lint 阶段
// 对整棵生产依赖树做同样的检查，这里守的是实际打进产物的部分。输出走 emitFile，故 --outDir 改到哪里文件就落到哪里。
const ALLOWED_LICENSES = new Set(['MIT', 'MIT-0', 'ISC', '0BSD', 'BSD-2-Clause', 'BSD-3-Clause', 'Apache-2.0', 'CC0-1.0']);
const LICENSE_EXCEPTIONS: Record<string, string> = {
  'bpmn-js': 'bpmn.io licence: MIT plus the clause that the watermark stays (licenses/bpmn-io.txt, NOTICE)',
};
function thirdPartyNotices(): Plugin[] {
  let dependencies: Dependency[] = [];
  return [
    license({
      thirdParty: {
        includePrivate: false,
        allow: {
          test: (d) => (d.license !== null && ALLOWED_LICENSES.has(d.license)) || (d.name !== null && d.name in LICENSE_EXCEPTIONS),
          failOnUnlicensed: true,
          failOnViolation: true,
        },
        output: (list) => {
          dependencies = list;
        },
      },
    }),
    {
      name: 'ujcms-third-party-notices',
      generateBundle() {
        const text = dependencies.map((d) => d.text()).join('\n\n---\n\n');
        this.emitFile({ type: 'asset', fileName: 'THIRD-PARTY-LICENSES.txt', source: `${text}\n` });
      },
    },
  ];
}

export default defineConfig(({ mode }: ConfigEnv): UserConfig => {
  // 加载 .env 文件
  const env = loadEnv(mode, process.cwd());
  const proxy: Record<string, ProxyOptions> = {
    [env.VITE_BASE_API]: { target: env.VITE_PROXY_API, changeOrigin: true },
    [env.VITE_BASE_UPLOADS]: { target: env.VITE_PROXY_UPLOADS, changeOrigin: true },
    [env.VITE_BASE_THEMES]: { target: env.VITE_PROXY_THEMES, changeOrigin: true },
    // 插槽预览（Phase 7 / §7.0）：把前台页面经此前缀代理到后端，使预览 iframe 与控制台同源，
    // 浏览器可读取 iframe.contentDocument（开发期 :5173 与后端 :8080 跨域，否则被同源策略拦截）。
    // 生产环境控制台与前台同源（:8080/cp 与 :8080/），iframe 直接指向真实路径，无需此代理。
    '/_ujcms_preview': {
      target: env.VITE_PROXY_API,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/_ujcms_preview/, '') || '/',
    },
  };
  // 实时预览 iframe 内的「前台页面」以根相对路径引用后端的资源/接口。开发期控制台(:5173)与后端(:8080)
  // 分立，这些前缀必须转发到后端（VITE_PROXY_API），否则落到 Vite 的 SPA 兜底返回控制台 index.html——
  // 轻则 404（如 visit.js），重则把 index.html 文本注入页面（顶栏用户块撑高、页脚访问量变 HTML）。
  // 仅需「渲染期」用到的资源/片段前缀：页面本身与点击穿透的整页导航统一走 /_ujcms_preview 代理
  // （桥上报 navigate → usePreviewBridge 校验、BuilderPage.onNavigate 补 _preview=draft&_ptoken 后经此前缀路由），故无需逐一代理
  // /channel、/article 等前台路由。生产环境控制台与前台同源，全部走真实路径，无需任何此类代理。
  const frontendAssetPrefixes = [
    '/static', // Spring 静态资源（${ctx}/static/visit.js 访问统计脚本等）
    '/app', // ${dy}/app/*：顶栏用户块、文章翻页等 axios 取回的 HTML 片段
    '/frontend', // 主题公共 API ${api}=UrlConstants.FRONTEND_API（页脚在线访客/访问量等）
    '/_ujcms', // 预览桥 IIFE（visual-builder §3：后端在草稿预览响应注入 /_ujcms/preview-bridge.js）
  ];
  frontendAssetPrefixes.forEach((prefix) => {
    proxy[prefix] = { target: env.VITE_PROXY_API, changeOrigin: true };
  });
  return {
    base: env.VITE_PUBLIC_PATH,
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    // 注：Vite 7 已默认使用 Sass 现代编译器（modern-compiler），旧的 css.preprocessorOptions.scss.api
    // 选项已移除（其类型在 Vite 7 中不再存在），故此处不再显式声明。
    server: {
      port: Number(env.VITE_PORT),
      proxy,
    },
    build: {
      // 语法下限。此前由 @vitejs/plugin-legacy 隐式施加于现代构建（其默认覆盖 build.target 为此组值），
      // 移除该插件后显式声明，保证现有浏览器拿到的产物语法不变；legacy 分包只服务于不支持 ES Module 的
      // 浏览器（Chrome < 64 等，均已在 browserslist "not dead" 之外），却让产物体积翻倍，故不再生成。
      // 若日后上调（如 chrome89+ 可用顶层 await），属于有意的浏览器支持范围变更，需单独决策。
      target: ['es2020', 'edge79', 'firefox67', 'chrome64', 'safari12'],
      // 按需加载的第三方大包（TinyMCE 约 1.5 MB、CodeMirror、bytemd、echarts）本身就超过默认 500 kB 阈值，
      // 阈值放宽到 2000 kB 只为屏蔽这些已知项；入口 chunk 若接近该值仍应视为回归。
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        onwarn(warning, warn) {
          // Suppress type-only import warnings from Rollup strict named export checking.
          // These are TypeScript types (PropType, RouteRecordRaw, etc.) that have no runtime value.
          if (warning.code === 'MISSING_EXPORT') return;
          warn(warning);
        },
        plugins: thirdPartyNotices(),
      },
    },
    plugins: [
      vue(),
      vueDevTools(),
      vueI18nPlugin({
        // 不预编译 src/locales 下的 JSON（保持原始字符串、运行时 JIT 编译；预编译成 AST 后体积反而是原字符串的数倍）。
        // 此项不能省略：缺省时插件会把项目里所有 json/ts/js 都当作语言资源处理。保留插件是为了让 vue-i18n
        // 走 runtime 构建并关闭 legacy API（__VUE_I18N_LEGACY_API__=false）。
        include: [],
      }),
      viteStaticCopy({
        // vite-plugin-static-copy 4 起始终保留匹配路径的目录结构（此前 src 为目录时只复制该目录本身），
        // 故改为文件 glob 并用 stripBase 去掉 node_modules/tinymce/skins/{ui,content} 这 5 段前缀，
        // 使皮肤仍落在 tinymce/skins/ui/oxide 与 tinymce/skins/content/default（Tinymce.vue 的 skin_url/content_css）。
        targets: [
          {
            src: 'node_modules/tinymce/skins/ui/oxide/**/*',
            dest: 'tinymce/skins/ui/oxide',
            rename: { stripBase: 5 },
          },
          {
            src: 'node_modules/tinymce/skins/content/default/**/*',
            dest: 'tinymce/skins/content/default',
            rename: { stripBase: 5 },
          },
        ],
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

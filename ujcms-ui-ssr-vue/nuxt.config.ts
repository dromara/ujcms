import tailwindcss from '@tailwindcss/vite';

/**
 * Spring backend origin. Nuxt renders HTML; Spring stays the origin for
 * /api/**, /uploads/**, /rss, /sitemap*.xml and /download-file/**.
 *
 * Override via UJCMS_API_BASE env (e.g. https://demo.ujcms.com). The same
 * variable is read at runtime by the app-side fetchers (`app/api/http.ts`),
 * so a built `.output` honours it too.
 */
const backendOrigin = process.env.UJCMS_API_BASE ?? 'http://localhost:8080';

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  modules: ['@nuxt/eslint'],
  css: ['~/assets/css/main.css'],

  // Subdirectories group components by feature (article/, channel/, sidebar/,
  // auth/, ...) without prefixing the component name — filenames are unique
  // and mirror the React track's component names.
  components: [{ path: '~/components', pathPrefix: false }],

  app: {
    head: {
      htmlAttrs: { lang: 'zh-CN' },
    },
  },

  vite: {
    plugins: [tailwindcss()],
  },

  nitro: {
    routeRules: {
      '/api/**': { proxy: `${backendOrigin}/api/**` },
      '/uploads/**': { proxy: `${backendOrigin}/uploads/**` },
      '/uploads_test/**': { proxy: `${backendOrigin}/uploads_test/**` },
      '/rss': { proxy: `${backendOrigin}/rss` },
      '/sitemap.xml': { proxy: `${backendOrigin}/sitemap.xml` },
      '/sitemap-channel.xml': { proxy: `${backendOrigin}/sitemap-channel.xml` },
      // `/sitemap-article{n}.xml` needs a partial-segment wildcard which the
      // radix router can't express — handled by server/middleware/spring-passthrough.ts.
      '/download-file/**': { proxy: `${backendOrigin}/download-file/**` },
    },
  },

  typescript: {
    strict: true,
  },

  eslint: {
    config: {
      stylistic: false,
    },
  },
});

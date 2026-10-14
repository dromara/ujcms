import { defineEventHandler, proxyRequest } from 'h3';

/**
 * Proxies the numbered article sitemaps (`/sitemap-article1.xml`,
 * `/sitemap-article2.xml`, …) to Spring. The other passthrough endpoints are
 * declared as nitro routeRules in nuxt.config.ts, but the radix router can't
 * express a partial-segment wildcard like `sitemap-article*.xml`, so this
 * one lives in middleware.
 */
const SITEMAP_ARTICLE = /^\/sitemap-article\d+\.xml$/;

export default defineEventHandler((event) => {
  if (!SITEMAP_ARTICLE.test(event.path)) return;
  const backendOrigin = process.env.UJCMS_API_BASE ?? 'http://localhost:8080';
  return proxyRequest(event, `${backendOrigin}${event.path}`);
});

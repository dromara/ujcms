import type { Site, UjcmsConfig } from '#shared/types/api';

import { apiFetch, ApiFetchError } from './http';

/**
 * Helpers for the env+site bootstrap fetched on every request.
 *
 * These never throw: they degrade to null on any upstream failure (HTTP
 * 4xx/5xx, network error, JSON parse error). Rationale: the SSR shell must
 * always render something — Baidu must always see HTML, and a Spring outage
 * shouldn't 500 the whole site. Consumers check for null and render a
 * fallback.
 *
 * Routes that genuinely require an entity (article detail, channel detail)
 * call apiFetch directly and surface a 404 / error page.
 *
 * Request-scoped dedup (the React track's React.cache()) is handled by the
 * caller: app.vue fetches site/user/config once per request into the shared
 * session state, and pages read from there instead of re-fetching.
 */

export async function safeFetch<T>(label: string, fetcher: () => Promise<T>): Promise<T | null> {
  try {
    return await fetcher();
  } catch (error) {
    if (error instanceof ApiFetchError) {
      // 4xx/5xx — log and return null. Most common case during dev: 404 on
      // /api/site/current when the seeded site doesn't match the dev hostname.
      console.warn(`[ujcms] ${label} upstream ${error.status} ${error.statusText}`);
      return null;
    }
    // Network failure (ECONNREFUSED, DNS, timeout). Not actionable from the
    // page; render the SSR fallback. Logged so operators notice.
    console.warn(`[ujcms] ${label} failed:`, error instanceof Error ? error.message : error);
    return null;
  }
}

export function fetchCurrentSite(subDir?: string): Promise<Site | null> {
  return safeFetch('fetchCurrentSite', () =>
    apiFetch<Site>('/api/site/current', {
      searchParams: subDir ? { subDir } : undefined,
    }),
  );
}

export function fetchConfig(): Promise<UjcmsConfig | null> {
  return safeFetch('fetchConfig', () => apiFetch<UjcmsConfig>('/api/env/config'));
}

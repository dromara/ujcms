/**
 * Universal fetch wrapper for the PUBLIC Spring endpoints — the Nuxt
 * counterpart of the React track's `serverFetch(..., { skipCredentials:
 * true })` calls.
 *
 * - During SSR it talks to Spring directly (UJCMS_API_BASE, default
 *   http://localhost:8080) without forwarding cookies (all consumed
 *   endpoints are anonymous-callable).
 * - In the browser it uses relative URLs, which flow through the Nitro
 *   proxy rules in nuxt.config.ts.
 *
 * Reads process.env directly instead of useRuntimeConfig() so callers don't
 * need a live Nuxt instance context (fetchers are routinely invoked after
 * `await` boundaries inside useAsyncData handlers).
 */

export type SearchParams = Record<string, string | number | boolean | undefined | null>;

export interface ApiFetchOptions {
  searchParams?: SearchParams;
}

const apiOrigin = (): string => (import.meta.server ? (process.env.UJCMS_API_BASE ?? 'http://localhost:8080') : '');

const buildUrl = (path: string, searchParams?: SearchParams): string => {
  const target = path.startsWith('/') ? path : `/${path}`;
  const qs = new URLSearchParams();
  if (searchParams) {
    for (const [key, value] of Object.entries(searchParams)) {
      if (value === undefined || value === null) continue;
      qs.set(key, String(value));
    }
  }
  const query = qs.toString();
  return `${apiOrigin()}${target}${query ? `?${query}` : ''}`;
};

export class ApiFetchError extends Error {
  constructor(
    public readonly status: number,
    public readonly statusText: string,
    public readonly url: string,
    public readonly body: string,
  ) {
    super(`Upstream ${status} ${statusText} for ${url}`);
    this.name = 'ApiFetchError';
  }
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const url = buildUrl(path, options.searchParams);
  const res = await fetch(url, { headers: { accept: 'application/json' } });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new ApiFetchError(res.status, res.statusText, url, body);
  }
  const contentType = res.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) return (await res.text()) as unknown as T;
  return (await res.json()) as T;
}

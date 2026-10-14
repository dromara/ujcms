import type { Article, PagedModel } from '#shared/types/api';

import { apiFetch, ApiFetchError, type SearchParams } from './http';

export interface ArticleListParams {
  siteId?: string | number;
  channel?: string;
  channelId?: string | number;
  tagId?: string | number;
  isIncludeSubChannel?: boolean;
  isWithImage?: boolean;
  limit?: number;
  offset?: number;
  orderBy?: string;
  isAllSite?: boolean;
  /**
   * Escape-hatch raw query-string params for `QueryUtils` filters that the
   * typed surface doesn't expose (e.g. `Q_EQ_userId_Long` for "articles by
   * this author"). Keys are passed through verbatim.
   */
  query?: SearchParams;
}

export interface ArticlePageParams extends Omit<ArticleListParams, 'limit' | 'offset'> {
  page?: number;
  pageSize?: number;
}

const buildSearchParams = (params: ArticleListParams | ArticlePageParams): SearchParams => {
  const out: SearchParams = {};
  for (const [key, value] of Object.entries(params)) {
    if (key === 'query') continue;
    if (value !== undefined && value !== null) out[key] = value as string | number | boolean;
  }
  if (params.query) {
    for (const [key, value] of Object.entries(params.query)) {
      if (value !== undefined && value !== null) out[key] = value;
    }
  }
  return out;
};

export async function fetchArticles(params: ArticleListParams = {}): Promise<Article[]> {
  try {
    return await apiFetch<Article[]>('/api/article', { searchParams: buildSearchParams(params) });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchArticles upstream ${error.status}`);
      return [];
    }
    console.warn('[ujcms] fetchArticles failed:', error instanceof Error ? error.message : error);
    return [];
  }
}

export async function fetchArticlePage(params: ArticlePageParams = {}): Promise<PagedModel<Article>> {
  try {
    return await apiFetch<PagedModel<Article>>('/api/article/page', { searchParams: buildSearchParams(params) });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchArticlePage upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchArticlePage failed:', error instanceof Error ? error.message : error);
    }
    return { content: [] };
  }
}

/**
 * Topic landing-page article list. Ordering comes from the curator
 * (`ujcms_article_topic.order_` asc), not the article's own sticky/order.
 * Backed by `GET /api/article/by-topic`.
 */
export async function fetchTopicArticlePage(topicId: string | number, page: number = 1, pageSize: number = 20): Promise<PagedModel<Article>> {
  try {
    return await apiFetch<PagedModel<Article>>('/api/article/by-topic', { searchParams: { topicId, page, pageSize } });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchTopicArticlePage upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchTopicArticlePage failed:', error instanceof Error ? error.message : error);
    }
    return { content: [] };
  }
}

/**
 * Resolve a single article. Returns null on 404 so the page can surface a
 * 404; non-404 errors propagate so an upstream outage on a real article
 * request doesn't masquerade as a missing article.
 *
 * `/api/article/{id}` returns the full Whole view (text, plainText, markdown,
 * destList) — Views.List would omit those.
 */
export async function fetchArticle(id: string | number): Promise<Article | null> {
  try {
    return await apiFetch<Article>(`/api/article/${encodeURIComponent(String(id))}`);
  } catch (error) {
    if (error instanceof ApiFetchError && error.status === 404) return null;
    throw error;
  }
}

/**
 * Resolve an article by its slug (`GET /api/article/slug/{slug}`, site-scoped
 * the same way as the other public endpoints). Returns null on 404 so the
 * page can surface a 404; non-404 errors propagate — mirrors
 * `fetchChannelBySlug`.
 */
export async function fetchArticleBySlug(slug: string, siteId?: string | number): Promise<Article | null> {
  try {
    return await apiFetch<Article>(`/api/article/slug/${encodeURIComponent(slug)}`, {
      searchParams: siteId ? { siteId } : undefined,
    });
  } catch (error) {
    if (error instanceof ApiFetchError && error.status === 404) return null;
    throw error;
  }
}

/**
 * Related articles — hand-picked rows first (curated in the console), the
 * remainder auto-filled by title similarity / shared tags / same channel.
 * Backed by `GET /api/article/{id}/related`; mirrors the FreeMarker
 * `[@ArticleRelatedList]` directive. Returns [] on any upstream error so a
 * hiccup never blocks the article body from rendering.
 */
export async function fetchRelatedArticles(id: string | number, limit: number = 5, channelId?: string | number): Promise<Article[]> {
  try {
    return await apiFetch<Article[]>(`/api/article/${encodeURIComponent(String(id))}/related`, {
      searchParams: { limit, ...(channelId != null ? { channelId } : {}) },
    });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchRelatedArticles upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchRelatedArticles failed:', error instanceof Error ? error.message : error);
    }
    return [];
  }
}

export interface NeighbourParams {
  id: string;
  order?: string;
  channelId?: string;
  isDesc?: boolean;
}

const fetchNeighbour = async (path: '/api/article/next' | '/api/article/prev', params: NeighbourParams): Promise<Article | null> => {
  try {
    const result = await apiFetch<Article | null | ''>(path, {
      searchParams: {
        id: params.id,
        order: params.order,
        channelId: params.channelId,
        isDesc: params.isDesc,
      },
    });
    if (!result || typeof result !== 'object') return null;
    return result;
  } catch {
    return null;
  }
};

export const fetchArticleNext = (params: NeighbourParams) => fetchNeighbour('/api/article/next', params);
export const fetchArticlePrev = (params: NeighbourParams) => fetchNeighbour('/api/article/prev', params);

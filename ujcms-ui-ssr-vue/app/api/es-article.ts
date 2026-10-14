import type { EsArticle, PagedModel } from '#shared/types/api';

import { apiFetch, ApiFetchError } from './http';

export interface EsArticlePageParams {
  siteId?: string | number;
  q?: string;
  channelId?: string | number;
  beginPublishDate?: string;
  endPublishDate?: string;
  /** Exclude these article ids (comma-separated for the API). */
  excludeId?: string | number;
  /** AND vs OR between query terms; matches template-theme baseline default of true. */
  isOperatorAnd?: boolean;
  /** Highlight fragment size in chars; matches template-theme baseline default. */
  fragmentSize?: number;
  /** Recency-decay scale in days; matches template-theme baseline default. */
  dateExpScale?: number;
  page?: number;
  pageSize?: number;
}

export interface EsArticleListParams {
  siteId?: string | number;
  q?: string;
  channelId?: string | number;
  excludeId?: string | number;
  isOperatorAnd?: boolean;
  limit?: number;
}

/**
 * Full-text search via Elasticsearch / Lucene. Endpoint is /api/article/es/page
 * (not /api/es-article — note the path).
 *
 * Returns an empty page on any upstream error so the search page renders the
 * form even when the search backend is down (degraded but usable).
 */
export async function fetchEsArticlePage(params: EsArticlePageParams): Promise<PagedModel<EsArticle>> {
  try {
    return await apiFetch<PagedModel<EsArticle>>('/api/article/es/page', {
      searchParams: {
        siteId: params.siteId,
        q: params.q,
        channelId: params.channelId,
        beginPublishDate: params.beginPublishDate,
        endPublishDate: params.endPublishDate,
        excludeId: params.excludeId,
        isOperatorAnd: params.isOperatorAnd,
        fragmentSize: params.fragmentSize,
        dateExpScale: params.dateExpScale,
        page: params.page,
        pageSize: params.pageSize,
      },
    });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchEsArticlePage upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchEsArticlePage failed:', error instanceof Error ? error.message : error);
    }
    return { content: [] };
  }
}

/**
 * "Related articles" list — used by article variants (product / download /
 * wenku) under the `相关 …` heading. Returns [] on any upstream error so a
 * search-backend hiccup never blocks the article body from rendering.
 */
export async function fetchEsArticleList(params: EsArticleListParams): Promise<EsArticle[]> {
  try {
    return await apiFetch<EsArticle[]>('/api/article/es', {
      searchParams: {
        siteId: params.siteId,
        q: params.q,
        channelId: params.channelId,
        excludeId: params.excludeId,
        isOperatorAnd: params.isOperatorAnd,
        limit: params.limit,
      },
    });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchEsArticleList upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchEsArticleList failed:', error instanceof Error ? error.message : error);
    }
    return [];
  }
}

import type { Block, BlockItem } from '#shared/types/api';

import { apiFetch, ApiFetchError, type SearchParams } from './http';

export interface BlockItemListParams {
  siteId?: string | number;
  /** Block alias (e.g. `'focus'` for the homepage carousel). */
  block?: string;
  blockId?: string | number;
  isAllSite?: boolean;
  isEnabled?: boolean | 'all';
  limit?: number;
  offset?: number;
}

export async function fetchBlockItems(params: BlockItemListParams = {}): Promise<BlockItem[]> {
  const search: SearchParams = {};
  if (params.siteId !== undefined) search.siteId = params.siteId;
  if (params.block !== undefined) search.block = params.block;
  if (params.blockId !== undefined) search.blockId = params.blockId;
  if (params.isAllSite !== undefined) search.isAllSite = params.isAllSite;
  if (params.isEnabled !== undefined) search.isEnabled = params.isEnabled;
  if (params.limit !== undefined) search.limit = params.limit;
  if (params.offset !== undefined) search.offset = params.offset;
  try {
    return await apiFetch<BlockItem[]>('/api/block-item', { searchParams: search });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchBlockItems upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchBlockItems failed:', error instanceof Error ? error.message : error);
    }
    return [];
  }
}

export interface BlockListParams {
  siteId?: string | number;
  /** Block category (matches `Block.category` exactly). */
  category?: string;
  limit?: number;
  offset?: number;
}

export async function fetchBlocks(params: BlockListParams = {}): Promise<Block[]> {
  const search: SearchParams = {};
  if (params.siteId !== undefined) search.siteId = params.siteId;
  if (params.category !== undefined) search.category = params.category;
  if (params.limit !== undefined) search.limit = params.limit;
  if (params.offset !== undefined) search.offset = params.offset;
  try {
    return await apiFetch<Block[]>('/api/block', { searchParams: search });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchBlocks upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchBlocks failed:', error instanceof Error ? error.message : error);
    }
    return [];
  }
}

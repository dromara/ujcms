import type { DictEntry } from '#shared/types/api';

import { apiFetch, ApiFetchError } from './http';

/**
 * Dictionary entries by type id. Drives facet-filter chip groups for
 * `select`/`radio`/`checkbox`/`multipleSelect` fields.
 *
 * Returns [] on any upstream error so a missing/disabled dict can't break
 * a channel page render. (`fetchChannels` uses the same convention.)
 */
export async function fetchDictList(typeId: string | number): Promise<DictEntry[]> {
  try {
    return await apiFetch<DictEntry[]>('/api/dict', { searchParams: { typeId } });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchDictList(${typeId}) upstream ${error.status}`);
    } else {
      console.warn('[ujcms] fetchDictList failed:', error instanceof Error ? error.message : error);
    }
    return [];
  }
}

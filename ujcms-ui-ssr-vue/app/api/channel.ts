import type { Channel, SiteViewBuffer } from '#shared/types/api';

import { apiFetch, ApiFetchError } from './http';

export interface ChannelListParams {
  siteId?: string | number;
  parentId?: string | number;
  isNav?: boolean;
  isAllSite?: boolean;
}

/**
 * Channel list. Returns [] on any error so the header chrome renders even
 * when the channel API is hiccuping. Real channel detail pages call apiFetch
 * directly so a missing channel becomes a 404 not a silently empty page.
 */
export async function fetchChannels(params: ChannelListParams = {}): Promise<Channel[]> {
  try {
    return await apiFetch<Channel[]>('/api/channel', {
      searchParams: {
        siteId: params.siteId,
        parentId: params.parentId,
        isNav: params.isNav,
        isAllSite: params.isAllSite,
      },
    });
  } catch (error) {
    if (error instanceof ApiFetchError) {
      console.warn(`[ujcms] fetchChannels upstream ${error.status}`);
      return [];
    }
    console.warn('[ujcms] fetchChannels failed:', error instanceof Error ? error.message : error);
    return [];
  }
}

/**
 * Site view-count buffer (today / yesterday / total). The footer renders
 * this client-side post-mount per visit-tracking.md guidance (server-rendered
 * counters go stale instantly).
 */
export async function fetchSiteViewBuffer(siteId: string | number): Promise<SiteViewBuffer | null> {
  try {
    return await apiFetch<SiteViewBuffer>(`/api/site/buffer/${siteId}`);
  } catch {
    return null;
  }
}

/**
 * Resolve a channel by its numeric id. Returns null on 404. Used when only
 * the article-side reference (`article.channelId`) is known but a full
 * Channel record is needed (e.g. reading the V11.3 `channel.articleLayout`
 * which the `ChannelRef` shape doesn't expose).
 */
export async function fetchChannel(id: string | number): Promise<Channel | null> {
  try {
    return await apiFetch<Channel>(`/api/channel/${encodeURIComponent(String(id))}`);
  } catch (error) {
    if (error instanceof ApiFetchError && error.status === 404) return null;
    throw error;
  }
}

/**
 * Resolve a channel by its slug. Returns null on 404 so pages can surface a
 * 404; non-404 errors propagate so an upstream outage on a real entity
 * request doesn't silently render an empty channel.
 */
export async function fetchChannelBySlug(slug: string, siteId?: string | number): Promise<Channel | null> {
  try {
    return await apiFetch<Channel>(`/api/channel/slug/${encodeURIComponent(slug)}`, {
      searchParams: siteId ? { siteId } : undefined,
    });
  } catch (error) {
    if (error instanceof ApiFetchError && error.status === 404) return null;
    throw error;
  }
}

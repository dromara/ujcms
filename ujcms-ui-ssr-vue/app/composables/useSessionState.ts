import type { Site, UjcmsConfig } from '#shared/types/api';

/**
 * Site / config session state.
 *
 * Seeded once per request by app.vue's `useAsyncData` (so the values are in
 * the SSR payload and available synchronously on the client — the initial
 * HTML is self-sufficient for Baidu). Components read it via this
 * composable; pages should NOT re-fetch site/config.
 */

export interface SessionState {
  site: Site | null;
  config: UjcmsConfig | null;
}

export function useSessionState() {
  const session = useState<SessionState>('ujcms-session', () => ({ site: null, config: null }));
  return { session };
}

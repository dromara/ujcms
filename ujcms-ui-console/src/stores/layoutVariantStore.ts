import { defineStore } from 'pinia';
import { queryLayoutVariantBySurface } from '@/api/system';

// Surfaces match LayoutVariantCatalog.SURFACE_* on the backend.
type Surface = 'channel' | 'article' | 'topic';

interface VariantRow {
  surface: Surface;
  value: string;
  label: string;
  order: number;
  // Theme variant's template binding — used to annotate template dropdowns
  // with variant labels (template-track-variants Q3).
  template?: string | null;
}

/**
 * Caches per-surface variant lists fetched from
 * /api/backend/core/layout-variant/by-surface/{surface} — the active-theme-declared
 * catalog (labels from the theme manifest), consumed by the builder's variant
 * switcher and TemplateSelect's option annotation.
 *
 * The variant catalog is read-only, served straight from the composition
 * snapshot. It changes on theme switch; invalidate on explicit site switch.
 */
export const useLayoutVariantStore = defineStore('ujcmsLayoutVariantStore', {
  state: () => ({
    optionsByKey: {} as Record<string, VariantRow[] | undefined>,
    inflight: {} as Record<string, Promise<VariantRow[]> | undefined>,
  }),
  actions: {
    optionsFor(surface: Surface): VariantRow[] {
      return this.optionsByKey[surface] ?? [];
    },
    async fetchBySurface(surface: Surface): Promise<VariantRow[]> {
      if (this.optionsByKey[surface]) return this.optionsByKey[surface] as VariantRow[];
      let pending = this.inflight[surface];
      if (!pending) {
        pending = (async () => {
          const rows = (await queryLayoutVariantBySurface(surface)) as VariantRow[];
          this.optionsByKey[surface] = rows;
          return rows;
        })();
        this.inflight[surface] = pending;
      }
      try {
        return await pending;
      } finally {
        this.inflight[surface] = undefined;
      }
    },
    invalidateSurface(surface?: Surface) {
      if (surface) {
        this.optionsByKey[surface] = undefined;
      } else {
        this.optionsByKey = {} as Record<string, VariantRow[] | undefined>;
      }
    },
  },
});

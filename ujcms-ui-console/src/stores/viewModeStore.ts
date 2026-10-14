import { defineStore } from 'pinia';

export type ViewMode = 'list' | 'grid';

export const useViewModeStore = defineStore('ujcmsViewMode', {
  state: () => ({
    modes: {} as Record<string, ViewMode>,
  }),
  actions: {
    get(name: string, fallback: ViewMode = 'list'): ViewMode {
      return this.modes[name] ?? fallback;
    },
    set(name: string, mode: ViewMode) {
      this.modes[name] = mode;
    },
  },
  persist: {
    pick: ['modes'],
  },
});

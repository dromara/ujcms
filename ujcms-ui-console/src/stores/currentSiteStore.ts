import { defineStore } from 'pinia';

export const useCurrentSiteStore = defineStore('ujcmsCurrentSiteStore', {
  state: () => ({
    currentSiteId: null as string | null,
    currentSite: null as any,
  }),
  actions: {
    getCurrentSiteId() {
      return this.currentSiteId;
    },
    setCurrentSiteId(currentSiteId: string | null) {
      this.currentSiteId = currentSiteId;
    },
  },
  persist: {
    paths: ['currentSiteId'],
    storage: sessionStorage,
  },
});

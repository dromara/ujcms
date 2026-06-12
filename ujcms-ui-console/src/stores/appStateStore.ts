import { defineStore } from 'pinia';

export const useAppStateStore = defineStore('ujcmsAppStateStore', {
  state: () => ({
    sidebar: true,
    messageBoxDisplay: false,
    loginBoxDisplay: false,
  }),
  actions: {
    setSidebar(sidebar: boolean) {
      this.sidebar = sidebar;
    },
    closeSidebar() {
      this.sidebar = false;
    },
    toggleSidebar() {
      this.sidebar = !this.sidebar;
    },
    setMessageBoxDisplay(messageBoxDisplay: boolean) {
      this.messageBoxDisplay = messageBoxDisplay;
    },
    setLoginBoxDisplay(loginBoxDisplay: boolean) {
      this.loginBoxDisplay = loginBoxDisplay;
    },
  },
  persist: {
    paths: ['sidebar'],
    storage: sessionStorage,
  },
});

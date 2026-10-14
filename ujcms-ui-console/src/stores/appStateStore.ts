import { defineStore } from 'pinia';

export const useAppStateStore = defineStore('ujcmsAppStateStore', {
  state: () => ({
    sidebar: true,
    messageBoxDisplay: false,
    loginBoxDisplay: false,
    // 命令面板（console-shell D31）：页头胶囊与 ⌘K 共用一个开关，不持久化
    commandPaletteDisplay: false,
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
    setCommandPaletteDisplay(commandPaletteDisplay: boolean) {
      this.commandPaletteDisplay = commandPaletteDisplay;
    },
  },
  persist: {
    pick: ['sidebar'],
    storage: sessionStorage,
  },
});

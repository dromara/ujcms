import { defineStore } from 'pinia';

export interface ColumnState {
  title: string;
  display: boolean;
}

export const mergeColumns = (settings: ColumnState[], origins: ColumnState[]) => {
  // 去除不存在的列
  for (let i = 0, len = settings.length; i < len; ) {
    if (origins.findIndex((column) => column.title === settings[i].title) === -1) {
      settings.splice(i, 1);
      len -= 1;
    } else {
      i += 1;
    }
  }
  // 增加未记录的列
  origins.forEach((column, index) => {
    if (settings.findIndex((item) => item.title === column.title) === -1) {
      settings.splice(index, 0, { ...column });
    }
  });
  return settings;
};

export const useColumnSettingsStore = defineStore('ujcmsColumnSettings', {
  state: () => ({
    originSettings: {} as Record<string, ColumnState[]>,
    crrrentSettings: {} as Record<string, ColumnState[]>,
  }),
  actions: {
    getCurrentSettings(name: string) {
      if (!this.crrrentSettings[name]) this.crrrentSettings[name] = [];
      return this.crrrentSettings[name];
    },
    setCurrentSettings(name: string, origins: ColumnState[]) {
      this.crrrentSettings[name] = origins;
    },
    getOriginSettings(name: string) {
      if (!this.originSettings[name]) this.originSettings[name] = [];
      return this.originSettings[name];
    },
    setOriginSettings(name: string, origins: ColumnState[]) {
      this.originSettings[name] = origins;
    },
  },
  persist: {
    paths: ['crrrentSettings'],
  },
});

import { defineStore } from 'pinia';

export const useImportDataStore = defineStore('ujcmsImportDataStore', {
  state: () => ({
    datasource: {} as any,
    channel: {} as any,
    article: {} as any,
  }),
  persist: true,
});

export const useImportDataPasswordStore = defineStore('ujcmsImportDataStore', {
  state: () => ({
    password: '',
  }),
  persist: {
    storage: sessionStorage,
  },
});

import { reactive, toRef } from 'vue';

export interface ColumnState {
  title: string;
  display: boolean;
}

const COLUMN_SETTINGS = 'ujcms_column_settings';

function fetchColumnSettings(): Record<string, ColumnState[]> {
  const settings = localStorage.getItem(COLUMN_SETTINGS);
  return settings ? JSON.parse(settings) : {};
}

const originStore: Record<string, ColumnState[]> = reactive({});
const settingStore: Record<string, ColumnState[]> = reactive(fetchColumnSettings());

export function storeColumnSettings() {
  localStorage.setItem(COLUMN_SETTINGS, JSON.stringify(settingStore));
}
export const getColumnOrigins = (name: string) => {
  if (!originStore[name]) originStore[name] = [];
  return toRef(originStore, name);
};
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
  origins.forEach((column) => {
    if (settings.findIndex((item) => item.title === column.title) === -1) {
      settings.push({ ...column });
    }
  });
  return settings;
};
export const setColumnOrigins = (name: string, origins: ColumnState[]) => {
  originStore[name] = origins;
  if (!settingStore[name]) settingStore[name] = [];
  const settings = settingStore[name];
  mergeColumns(settings, origins);
};
export const getColumnSettings = (name: string) => {
  if (!settingStore[name]) settingStore[name] = [];
  return toRef(settingStore, name);
};
// export const setColumnSettings = (name: string, settings: ColumnState[]) => {
//   settingStore[name] = settings;
// };

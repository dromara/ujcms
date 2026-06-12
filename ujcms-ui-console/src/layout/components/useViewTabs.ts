import { reactive, readonly } from 'vue';

export interface ViewTab {
  name: string | number;
  label: string;
  path: string;
  component: string;
  noCache?: boolean;
}

const viewTabsState: ViewTab[] = reactive([]);

export const viewTabs = readonly(viewTabsState);

export const addViewTab = (viewTab: ViewTab): void => {
  viewTabsState.push(viewTab);
};

export const removeViewTab = (name: string | number): void => {
  const index = viewTabsState.findIndex((it) => it.name === name);
  viewTabsState.splice(index, 1);
};

export const removeLeftViewTab = (name: string | number): void => {
  const index = viewTabsState.findIndex((it) => it.name === name);
  viewTabsState.splice(0, index);
};

export const removeRightViewTab = (name: string | number): void => {
  const index = viewTabsState.findIndex((it) => it.name === name);
  viewTabsState.splice(index + 1, viewTabsState.length - index - 1);
};

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

// 同名页签重访时同步最新 fullPath：以 query 定身份/状态的视图（如主题详情 site/theme/tab）再次点击页签须回到最近一次地址
export const updateViewTabPath = (name: string | number, path: string): void => {
  const tab = viewTabsState.find((it) => it.name === name);
  if (tab) {
    tab.path = path;
  }
};

/**
 * 视图的“回到”地址（docs/spec/console-view-state C5）：该视图的页签仍开着，就回到页签记录的地址（fullPath，含 query 中的
 * 导航上下文——目录、选中的树节点、公众号、状态行等）；没有页签则为裸地址，即全新打开，因此关闭页签就是“忘记”该视图的状态。
 * 页签以路由标题为键（见 AppTab）。侧栏菜单与视图内的“返回”按钮共用。
 */
export const resumePath = (title: unknown, barePath: string): string => viewTabsState.find((it) => it.name === title)?.path ?? barePath;

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

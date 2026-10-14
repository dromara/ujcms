import { computed } from 'vue';
import { hasPermission } from '@/stores/useCurrentUser';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { listMenuLeaves } from './menuLeaves';
import { TODO_QUEUES, isQueueAllowed } from './todoQueues';

/**
 * 首页区块注册表（docs/spec/console-home）。区块键是稳定字符串，写入偏好 home.hidden；
 * 一个区块只在“当前用户有东西可看”时可用（visible），用户再从可用区块里选择隐藏哪些。数组顺序即页面顺序。
 */
export type HomeSectionId = 'todo' | 'shortcuts' | 'overview' | 'traffic' | 'recent';

export interface HomeSection {
  id: HomeSectionId;
  titleKey: string;
  descKey: string;
  visible: () => boolean;
}

export const HOME_SECTIONS: HomeSection[] = [
  { id: 'todo', titleKey: 'home.todo', descKey: 'home.customize.todo', visible: () => TODO_QUEUES.some(isQueueAllowed) },
  { id: 'shortcuts', titleKey: 'home.shortcuts', descKey: 'home.customize.shortcuts', visible: () => listMenuLeaves().length > 0 },
  { id: 'overview', titleKey: 'home.overview', descKey: 'home.customize.overview', visible: () => true },
  // 访问统计接口接受 backend 权限，人人可见；只受用户自己的隐藏设置控制
  { id: 'traffic', titleKey: 'home.traffic', descKey: 'home.customize.traffic', visible: () => true },
  { id: 'recent', titleKey: 'home.recent', descKey: 'home.customize.recent', visible: () => hasPermission('article:list') },
];

export const isHomeSectionId = (value: unknown): value is HomeSectionId => HOME_SECTIONS.some((section) => section.id === value);

export const useHomeSections = () => {
  const preferences = usePreferencesStore();
  const hidden = computed(() => new Set(preferences.home.hidden.filter(isHomeSectionId)));
  const availableSections = computed(() => HOME_SECTIONS.filter((section) => section.visible()));
  const isShown = (id: HomeSectionId): boolean => !hidden.value.has(id) && (HOME_SECTIONS.find((section) => section.id === id)?.visible() ?? false);
  return { hidden, availableSections, isShown };
};

// 左侧导航“行块”的外观（docs/spec/console-aside）：卡片内缩 8px 的圆角块，选中态只用底色 + 文字色，不画贴边的竖线。
// 不可通栏：悬停灰与页面底色同为 gray-100，通栏的高亮看上去像白卡片被挖掉一条。高度由使用处给（根行 h-8、实体行 h-9）
export const asideRowClass = (active: boolean): string[] => [
  'flex items-center px-2 text-sm rounded cursor-pointer outline-none',
  active ? 'text-primary bg-primary-lighter' : 'text-gray-regular hover:bg-gray-100 focus-visible:bg-gray-100',
];

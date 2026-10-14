import { onMounted, ref, shallowRef, type Ref, type ShallowRef } from 'vue';

/**
 * 引用选择器（blockRef/channelRef/voteRef）目录的模块级缓存：先回显、后刷新（stale-while-revalidate）。
 *
 * 检查器每换一个形状不同的面板就重新挂载选择器；目录从空起步时 el-select 找不到匹配选项、把裸ID当标签
 * 显示，目录到达后才换成名称——每选中一个面板都先闪一下ID。有缓存则挂载即回显上次目录并在后台刷新替换；
 * 首次挂载 loaded 为 false，选择器据此暂不下发值（空框而非裸ID），加载失败也置 loaded 放行裸ID兜底。
 * 站点切换整页重载（AppHeader.changeSiteId），模块级缓存随之重置，不会串站。
 */
const catalogs = new Map<string, unknown>();

export const useRefCatalog = <T>(key: string, load: () => Promise<T>): { catalog: ShallowRef<T | undefined>; loaded: Ref<boolean> } => {
  const catalog = shallowRef<T | undefined>(catalogs.get(key) as T | undefined);
  const loaded = ref<boolean>(catalogs.has(key));
  onMounted(async () => {
    try {
      const result = await load();
      catalogs.set(key, result);
      catalog.value = result;
    } finally {
      loaded.value = true;
    }
  });
  return { catalog, loaded };
};

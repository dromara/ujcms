import { computed, onBeforeUnmount, onMounted, shallowRef, toValue, type ComputedRef, type MaybeRefOrGetter } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, type RouteLocationNormalized } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { cloneDeep, isEqual } from 'lodash-es';
import { viewKey } from './useRouteState';

/**
 * 整页表单的未保存守卫（docs/spec/console-view-state C13）。
 *
 * 控制台的 keep-alive 名单为空：F5、页签刷新按钮、关闭页签、点侧栏别的菜单都会重建视图，表单里没保存的改动随之
 * 丢失，而页面上没有任何提示。DialogForm 早已按“基线副本 + isEqual”判定未保存并在关闭、刷新前确认；本组合式函数把
 * 同一套规则给到整页表单（全局设置、站点设置、主题设置）：
 * - unsaved：当前值与基线不等（加载中不算），视图据此在保存按钮旁显示“未保存”标签；
 * - 离开路由（侧栏、关闭页签、页签刷新按钮、退出登录）先问一句，“继续编辑”则取消导航；
 * - 同一路由的 query 改写：视图自己写入状态 query（useRouteState）的回声放行；外部改写（命令面板的 push、前进/后退、
 *   手改地址）会重建视图（C6），视图 key（身份 query）变化亦然，都与离开同样确认；
 * - F5、关闭浏览器页签：浏览器自己的确认框（beforeunload）。
 * 视图内自己的切换（左侧设置页签、页内分段按钮）不经过路由守卫，视图须先 confirmDiscard() 再切换，并在数据加载、
 * 页签切换、保存成功后 markSaved() 重置基线。基线是独立副本，视图给 values 赋值时也应赋副本（cloneDeep）：否则
 * “放弃修改”后改动仍留在原对象里，切回来时既复现又不算未保存。
 */
export interface UnsavedGuardOptions {
  /** 加载中不算未保存：基线尚未就绪 */
  loading?: MaybeRefOrGetter<boolean>;
  /**
   * 同一路由的 query 改写是否会丢掉本视图的编辑（外部改写状态 query → 视图重建）。典型写法：按目标地址解析出的
   * 状态值与 ref 当前值不等即为外部改写——视图自己的写入落地时两者已相等。不给则只在视图 key 变化时确认。
   */
  isExternalUpdate?: (to: RouteLocationNormalized) => boolean;
}

export interface UnsavedGuard {
  /** 当前值与基线不等 */
  unsaved: ComputedRef<boolean>;
  /** 把当前值记为基线：数据加载、页签切换、保存成功之后调用 */
  markSaved: () => void;
  /** 有未保存的修改时先问一句。放弃（或本无修改）返回 true，继续编辑返回 false */
  confirmDiscard: () => Promise<boolean>;
}

type Translate = ReturnType<typeof useI18n>['t'];

/**
 * 确认框本身：DialogForm 与整页表单共用同一段文案与按钮。
 * closeOnHashChange 必须关掉：前进/后退、手改地址触发的导航先发 popstate（路由守卫在此弹框）再发 hashchange，
 * ElMessageBox 默认随 hashchange 自行关闭，且未点按钮就不回调——Promise 永不落定，导航就此挂起
 */
export const confirmDiscardChanges = async (t: Translate): Promise<boolean> => {
  try {
    await ElMessageBox.confirm(t('form.unsaved.confirm'), t('form.unsaved'), {
      type: 'warning',
      confirmButtonText: t('form.unsaved.discard'),
      cancelButtonText: t('form.unsaved.keep'),
      closeOnHashChange: false,
    });
    return true;
  } catch {
    return false;
  }
};

export const useUnsavedGuard = (values: MaybeRefOrGetter<unknown>, options: UnsavedGuardOptions = {}): UnsavedGuard => {
  const { t } = useI18n();
  const baseline = shallowRef<unknown>(cloneDeep(toValue(values)));
  const unsaved = computed<boolean>(() => !toValue(options.loading) && !isEqual(baseline.value, toValue(values)));
  const markSaved = (): void => {
    baseline.value = cloneDeep(toValue(values));
  };
  const confirmDiscard = async (): Promise<boolean> => !unsaved.value || confirmDiscardChanges(t);

  onBeforeRouteLeave(() => confirmDiscard());
  onBeforeRouteUpdate((to, from) => {
    if (viewKey(to) === viewKey(from) && !options.isExternalUpdate?.(to)) return true;
    return confirmDiscard();
  });
  // 刷新、关闭页签：浏览器自己的确认框
  const handleBeforeUnload = (event: BeforeUnloadEvent): void => {
    if (unsaved.value) {
      event.preventDefault();
      event.returnValue = '';
    }
  };
  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload));

  return { unsaved, markSaved, confirmDiscard };
};

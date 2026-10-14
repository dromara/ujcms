import { onMounted, onScopeDispose, ref, toValue, watch, type MaybeRefOrGetter, type Ref } from 'vue';
import { stringifyQuery, useRoute, useRouter, type LocationQuery, type LocationQueryRaw, type LocationQueryValue, type RouteLocationNormalizedLoaded } from 'vue-router';

/**
 * 视图导航状态与路由 query 的双向同步（契约见 docs/spec/console-view-state/plan.md）。
 *
 * 控制台的 keep-alive 名单为空（AppMain：缓存会让页签间数据陈旧），视图在 F5、页签刷新、乃至切到别的页签
 * 再切回来时都会重建。视图内的“导航上下文”——面包屑目录、树的选中节点、左侧选择器、页签/单选按钮行、
 * 全局数据复选框——因此存放在路由 query：AppTab 记录页签的 fullPath、RefreshPage 回跳 fullPath，
 * query 里的状态天然随之恢复。查询表单、排序、分页、勾选、对话框等工作状态不在此列。
 *
 * 用法：每个路由视图至多调用一次，声明全部键，返回普通 ref（既有的命令式处理函数照常赋值即可）：
 *   const { channelId, status } = useRouteState({ channelId: stringParam(), status: numberParam(-1, [0, 1]) });
 * 这些键必须同时声明在路由的 meta.stateQuery（AppMain 据此把它们从视图重建键中剔除，改写不重建视图）；
 * scripts/check-view-state.mjs 校验两处一致。
 *
 * 约定：
 * - 以 router.replace 写入（不产生历史记录）；值等于默认值时省略该键，裸地址保持为裸地址。
 * - 同一 tick 内多个键的变化合并为一次整体写入（如「全局数据」同时改写 global 与 dir）；query 中其它键原样保留。
 * - 外部改写（手改地址、前进/后退、指向已打开视图的站内链接）不做就地同步，而是递增 viewEpoch 让
 *   AppMain 重建视图、从 URL 重新初始化——与 F5 走同一条恢复路径，视图无需各自处理“外部变化”。
 * - 恢复出的值不可信（实体已删除、权限变化、手改地址）：视图须在数据加载后自行校验，不合法即回落默认值
 *   （同时也就清理了地址）。切换站点时由 AppHeader 先从地址中清除这些键再整页重载——导航上下文属于原站点。
 * - 若将来重新启用 AppMain 的 keep-alive 名单，这里需补 onActivated/onDeactivated 处理。
 */

export interface RouteParam<T> {
  // raw 为 query 中的原始值（缺省、空串、数组均已归一为 string | undefined）；非法值须回落默认值
  parse: (raw: string | undefined) => T;
  // 返回 undefined 表示省略该键（值为默认值）
  format: (value: T) => string | undefined;
}

/** 字符串（如实体 ID、目录）。默认值可为 getter：默认值取自异步加载的列表首项时，列表就绪后地址自动回到规范形式。 */
export const stringParam = (def?: MaybeRefOrGetter<string | undefined>): RouteParam<string | undefined> => ({
  parse: (raw) => raw ?? toValue(def),
  format: (value) => (value == null || value === toValue(def) ? undefined : value),
});

/** 整数；给出 allowed 时只接受其中的值（及默认值），其余回落默认值。 */
export function numberParam(def: number, allowed?: readonly number[]): RouteParam<number>;
export function numberParam(def?: undefined, allowed?: readonly number[]): RouteParam<number | undefined>;
export function numberParam(def?: number, allowed?: readonly number[]): RouteParam<any> {
  return {
    parse: (raw: string | undefined): number | undefined => {
      if (raw == null) return def;
      const value = Number(raw);
      return Number.isInteger(value) && (allowed == null || allowed.includes(value) || value === def) ? value : def;
    },
    format: (value: number | undefined): string | undefined => (value == null || value === def ? undefined : String(value)),
  };
}

/** 枚举字符串（如视图内页签）。 */
export const enumParam = <const T extends string>(def: T, allowed: readonly T[]): RouteParam<T> => ({
  parse: (raw) => (raw != null && (allowed as readonly string[]).includes(raw) ? (raw as T) : def),
  format: (value) => (value === def ? undefined : value),
});

/** 布尔开关，以 `=1` 表示开启（如 ?global=1）。permitted 为 false 时恒为关闭（无权限者手改地址无效）。 */
export const flagParam = (permitted: MaybeRefOrGetter<boolean> = true): RouteParam<boolean> => ({
  parse: (raw) => raw === '1' && toValue(permitted),
  format: (value) => (value ? '1' : undefined),
});

/** 视图重建序号：useRouteState 发现外部改写了状态 query 时递增，AppMain 把它并入视图 key 触发重建。 */
export const viewEpoch = ref<number>(0);

/**
 * 视图重建键：默认 fullPath（身份类 query 变化须重建，如 ThemeDetail 的 site/theme）；
 * 路由以 meta.stateQuery 声明的 UI 状态 query（如 tab）从键中剔除，视图改写它们不重建、不触发过渡。
 */
export const viewKey = (route: Pick<RouteLocationNormalizedLoaded, 'path' | 'fullPath' | 'query' | 'hash' | 'meta'>): string => {
  const stateQuery = route.meta.stateQuery;
  if (!stateQuery?.length) return route.fullPath;
  const query = Object.fromEntries(Object.entries(route.query).filter(([key]) => !stateQuery.includes(key)));
  const search = stringifyQuery(query);
  return route.path + (search ? `?${search}` : '') + route.hash;
};

type StateRefs<S> = { [K in keyof S]: S[K] extends RouteParam<infer T> ? Ref<T> : never };

const firstValue = (value: LocationQueryValue | LocationQueryValue[] | undefined): string | undefined => {
  const first = Array.isArray(value) ? value[0] : value;
  return first == null || first === '' ? undefined : first;
};

export function useRouteState<S extends Record<string, RouteParam<any>>>(schema: S): StateRefs<S> {
  const route = useRoute();
  const router = useRouter();
  // useRoute() 是全局当前路由；旧视图在导航后还会存活片刻（异步回调仍可能改写 ref），故一切读写都以本视图的键为界
  const ownKey = viewKey(route);
  const keys = Object.keys(schema);
  const declared = route.meta.stateQuery ?? [];
  const missing = keys.filter((key) => !declared.includes(key));
  if (missing.length > 0 && import.meta.env.DEV) {
    throw new Error(`[useRouteState] 路由 ${String(route.name)} 的 meta.stateQuery 未声明：${missing.join(', ')}`);
  }
  // 生产环境兜底：未声明的键只驻内存、不写地址——写入未声明的键会改变视图 key，导致视图反复重建
  const synced = keys.filter((key) => declared.includes(key));

  const param = (key: string): RouteParam<unknown> => schema[key] as RouteParam<unknown>;
  const state: Record<string, Ref<unknown>> = {};
  keys.forEach((key) => {
    state[key] = ref(param(key).parse(firstValue(route.query[key])));
  });
  const valueOf = (key: string): unknown => state[key]?.value;

  // 状态快照（规范形式）：用于按值判断“地址里的状态”与“ref 里的状态”是否一致
  const snapshot = (read: (key: string) => unknown): string => JSON.stringify(synced.map((key) => param(key).format(read(key)) ?? null));
  const ofRefs = (): string => snapshot(valueOf);
  const ofQuery = (query: LocationQuery): string => snapshot((key) => param(key).parse(firstValue(query[key])));

  // 最近一次写入的目标快照：写入落地前 ref 可能又变了，落地时按它识别“自己的回声”
  let issued: string | undefined;
  let leaving = false;
  let stopped = false;
  const isOwn = (): boolean => !stopped && viewKey(router.currentRoute.value) === ownKey;

  const write = (): void => {
    // 离开途中不写：路由组件按需加载，目标 chunk 下载期间的一次 replace 会取消用户尚未完成的导航
    if (!isOwn() || leaving) return;
    const current = router.currentRoute.value;
    const query: LocationQueryRaw = { ...current.query };
    synced.forEach((key) => {
      const value = param(key).format(valueOf(key));
      if (value === undefined) {
        delete query[key];
      } else {
        query[key] = value;
      }
    });
    if (stringifyQuery(query) === stringifyQuery(current.query)) return;
    const target = ofRefs();
    issued = target;
    // 显式带上当前 path：仅给 query 时 vue-router 会按路由记录重新生成 path，丢掉侧栏地址末尾的斜杠
    // （/content/article/ → /content/article），视图 key 随之变化，首次写入就会重建视图。
    // replace 的 Promise 在 afterEach 之后才完成，届时回声已被识别，可以清除标记
    router
      .replace({ path: current.path, query, hash: current.hash })
      .catch(() => undefined)
      .then(() => {
        if (issued === target) issued = undefined;
      });
  };

  const stops: Array<() => void> = [];
  const stop = (): void => {
    stopped = true;
    stops.forEach((fn) => fn());
  };
  stops.push(
    // 默认 pre 刷新：同一 tick 内多个 ref 的变化只触发一次写入；默认值为 getter 时亦随其变化回到规范形式
    watch(ofRefs, write),
    router.beforeEach((to) => {
      leaving = viewKey(to) !== ownKey;
    }),
    router.afterEach((to, _from, failure) => {
      leaving = false;
      if (failure) {
        // 离开本视图的导航失败（被中止/取消）：补上期间跳过的写入。自身的写入失败不重试，避免循环
        if (viewKey(to) !== ownKey) write();
        return;
      }
      if (!isOwn()) return;
      const landed = ofQuery(to.query);
      if (landed === issued || landed === ofRefs()) return;
      // 外部改写（手改地址、前进/后退、指向本视图的站内链接）：停用本实例并重建视图，由新实例从 URL 初始化
      stop();
      viewEpoch.value += 1;
    }),
    router.onError(() => {
      leaving = false;
    }),
  );
  onScopeDispose(stop);
  // 挂载后规范化地址：清除非法值与等于默认值的键
  onMounted(write);

  return state as StateRefs<S>;
}

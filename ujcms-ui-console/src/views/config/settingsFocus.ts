import { onScopeDispose, ref, watch, type Ref } from 'vue';
import { useI18n } from 'vue-i18n';

/**
 * 命令面板 → 设置视图的“定位到设置项”信号（docs/spec/console-shell D8）。
 * 面板选中某个设置项后先登记目标、再 push 设置路由（?type=）；设置视图挂载后（或已开着的视图切到目标页签、
 * 或本就停在目标页签），在表单里找到带该标签的 el-form-item，滚入视野并高亮一下，然后清除目标。
 * 目标不入路由 query：它是一次性动作，不是 F5 / 页签往返时要恢复的导航上下文（console-view-state）。
 */
export interface SettingsFieldTarget {
  /** 设置视图的路由名（GlobalSettings / SiteSettings） */
  name: string;
  /** 设置页（?type=） */
  type: string;
  /** 设置项的标签键（label-tip 的 message，或 el-form-item 的 :label） */
  key: string;
}

// 导航被中止时目标会留下来，下一次打开设置视图才触发：登记十秒后作废
const TTL_MS = 10_000;
// 设置页切换后表单项可能异步出现（短信、邮件、存储点先请求再渲染）：按帧重试约一秒
const MAX_TRIES = 60;
const HIGHLIGHT_MS = 2000;

const pending = ref<(SettingsFieldTarget & { until: number }) | undefined>();

export const requestSettingsField = (target: SettingsFieldTarget): void => {
  pending.value = { ...target, until: Date.now() + TTL_MS };
};

/**
 * 设置视图调用一次：目标属于本视图且页签一致、数据加载完毕后开始定位。
 * 先按 label-tip 打在标签上的 data-label 找，其次按标签文字（:label="$t()" 的项）；找不到（被取值条件隐藏，
 * 如存储点类型不同）就停在页签上。
 */
export const useSettingsFieldFocus = (name: string, type: Ref<string>, loading: Ref<boolean>, form: Ref<{ $el?: HTMLElement } | undefined>): void => {
  const { t } = useI18n();
  let frame = 0;

  const locate = (key: string): HTMLElement | undefined => {
    const root = form.value?.$el;
    if (!root) return undefined;
    const anchor = root.querySelector<HTMLElement>(`[data-label="${CSS.escape(key)}"]`);
    if (anchor) return anchor.closest<HTMLElement>('.el-form-item') ?? undefined;
    const text = t(key);
    for (const label of root.querySelectorAll<HTMLElement>('.el-form-item__label')) {
      if (label.textContent?.trim() === text) return label.closest<HTMLElement>('.el-form-item') ?? undefined;
    }
    return undefined;
  };

  watch(
    [pending, type, loading],
    ([target]) => {
      cancelAnimationFrame(frame);
      if (!target || target.name !== name) return;
      if (target.until < Date.now()) {
        pending.value = undefined;
        return;
      }
      if (target.type !== type.value || loading.value) return;
      let tries = 0;
      const tick = (): void => {
        const item = locate(target.key);
        if (item) {
          pending.value = undefined;
          item.scrollIntoView({ block: 'center', behavior: 'smooth' });
          item.classList.add('is-located');
          window.setTimeout(() => item.classList.remove('is-located'), HIGHLIGHT_MS);
          return;
        }
        tries += 1;
        if (tries < MAX_TRIES) frame = requestAnimationFrame(tick);
        else pending.value = undefined;
      };
      frame = requestAnimationFrame(tick);
    },
    { immediate: true, flush: 'post' },
  );
  onScopeDispose(() => cancelAnimationFrame(frame));
};

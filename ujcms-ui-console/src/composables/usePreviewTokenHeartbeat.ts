import { onBeforeUnmount } from 'vue';

/**
 * 预览令牌心跳（design-review-2026-07 P1-2 / visual-builder P8 已知问题）。预览令牌随 iframe URL
 * 传递（_ptoken——「令牌绝不过桥」的安全不变量，无法向已装载文档推送新令牌），后端 PreviewAuthFilter
 * 逐请求校验：令牌到期后，画布的区域刷新自取与整页导航会静默退回已发布内容（模板轨道有 previewLost
 * 兜底但代价是保存瞬间整页重建）。本心跳在到期前主动重铸：到达 renewAt
 * （到期时刻减提前量）且画布空闲、标签可见时调用 renew()（宿主的 buildSrc——铸新令牌 + 重建画布 URL，
 * 这是不违背不变量前提下侵扰最小的应用方式）。
 *
 * 后台标签会暂停/节流定时器（rAF 全停）：故不累计间隔，一律与 Date.now() 比较绝对时刻，并在
 * visibilitychange/focus 回归时立即补检——休眠越过到期点也能即时恢复。隐藏期间不重建（画布不可见，
 * 重建无益且定时器不可靠）；busy（拖拽/放置/对话框/保存中）时跳过，下一拍重试。renew 静默失败
 * （令牌接口不可达等，buildSrc 自行提示）按 retry 间隔退避，避免每拍弹错。
 */
export function usePreviewTokenHeartbeat(options: { isBusy: () => boolean; renew: () => Promise<void> }) {
  const CHECK_MS = 5_000;
  const RETRY_MS = 60_000;
  /** 令牌绝对到期时刻（ms）；0 = 尚未武装。 */
  let expiresAt = 0;
  /** 重铸时刻（到期前提前量）；0 = 尚未武装。 */
  let renewAt = 0;
  let renewing = false;

  const check = (): void => {
    if (renewAt === 0 || Date.now() < renewAt || renewing || document.hidden || options.isBusy()) return;
    renewing = true;
    // 先按退避推后 renewAt：renew 成功会经 arm() 重置；静默失败则 RETRY_MS 后再试
    renewAt = Date.now() + RETRY_MS;
    void options.renew().finally(() => {
      renewing = false;
    });
  };

  /** 每次铸新令牌后调用：按绝对到期时刻武装。提前量 = TTL 的 20%，钳制在 [10s, 60s]。 */
  const arm = (expiresInSeconds: number): void => {
    const ttlMs = expiresInSeconds * 1000;
    const lead = Math.min(60_000, Math.max(10_000, ttlMs * 0.2));
    expiresAt = Date.now() + ttlMs;
    renewAt = Date.now() + Math.max(0, ttlMs - lead);
  };

  /** 当前令牌是否仍可用（含安全余量）：导航重写复用旧令牌前的检查——过期令牌拼出的地址会落到已发布页 + 死桥。 */
  const isFresh = (safetyMs = 10_000): boolean => expiresAt !== 0 && Date.now() < expiresAt - safetyMs;

  const timer = window.setInterval(check, CHECK_MS);
  document.addEventListener('visibilitychange', check);
  window.addEventListener('focus', check);

  const dispose = (): void => {
    window.clearInterval(timer);
    document.removeEventListener('visibilitychange', check);
    window.removeEventListener('focus', check);
  };
  onBeforeUnmount(dispose);

  return { arm, isFresh, dispose };
}

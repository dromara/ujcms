package com.ujcms.cms.core.composition;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 面板解析缓存（§19.1）。键为 {@code (siteId, pageRef, region#panel:<id>, themeKey, ctxKey,
 * snapshotHash)}——{@code ctxKey} 涵盖 {@code $ctx} 参数值，使按频道而异的结果不互相覆盖；快照哈希在
 * 注册表重载后自然作废全部旧键。LRU 上限 1 万、写后过期 TTL 可配置
 * （{@code ujcms.composition.resolve-cache-ttl-seconds}，默认 60 秒——显式作废已随
 * composition-invalidation 计划扩展到跨节点，TTL 仅剩职责：查询条目的文章 / 栏目 / 区块项内容漂移
 * 刻意不挂作废钩子（频度过高），以 TTL 界定新鲜度）；投放 / 主题设置 / 区块项保存时
 * 经 {@link CompositionCacheSync} 显式作废（本地 + 集群戳记——后台编辑即时可见，
 * 不受 TTL 影响）。
 *
 * <p>预览（草稿可见、样例 {@code $ctx}）从不进入缓存（composition-invalidation 计划 §4）。
 *
 * <p>指标（§19.2）：经 Micrometer 标准 Caffeine 绑定器暴露为 {@code cache.*{cache=ujcms.panel.resolve}}
 * （gets 命中/未命中、size、evictions——命中率由 hit/miss 派生；未按计划字面造 {@code hit_ratio}
 * 比值 gauge，比值型 gauge 不便聚合，为记录在案的偏差）。
 *
 * @author PONY
 */
@Component
public class PanelResolveCache {
    /**
     * 指标缓存名（{@code cache} 标签值）
     */
    public static final String METRIC_CACHE_NAME = "ujcms.panel.resolve";

    private final Cache<String, Object> cache;
    private final long ttlSeconds;

    public PanelResolveCache(MeterRegistry meterRegistry,
                                 @Value("${ujcms.composition.resolve-cache-ttl-seconds:60}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, METRIC_CACHE_NAME);
    }

    /**
     * 写后过期秒数（配置值）
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 取缓存（区域面板，component-settings §4.2）；未命中则执行 {@code loader} 并缓存其结果。
     * 面板键的插槽名分量带 {@code #panel:<id>} 后缀（历史键形，与区域名区分）。
     */
    public ResolvedRegion.Panel getPanel(String key, Supplier<ResolvedRegion.Panel> loader) {
        return (ResolvedRegion.Panel) cache.get(key, ignored -> loader.get());
    }

    /**
     * 作废全部缓存（本地）。业务侧请勿直接调用——构成内容变更走 {@link CompositionCacheSync#invalidateAll}
     * （本地 + 集群戳记）；本方法另由集群轮询与提交后钩子调用。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 构造缓存键（§19.1）。在 §19.1 的 {@code (siteId, pageRef, slotName, ctxKey, snapshotHash)}
     * 之上额外纳入 {@code themeKey}：桌面 / 移动主题各自解析清单与 Shape，
     * 不纳入会相互覆盖（决策日志记录此偏差）。{@code themeKey} 为 {@code null} 时取空串。
     * 分量以 {@code |} 连接（页面名 / 面板键 / 主题键 / 哈希均不含该字符）。
     *
     * @param siteId       站点ID
     * @param pageRef      页面清单名
     * @param slotName     面板键（{@code <region>#panel:<id>}）
     * @param themeKey     主题命名空间
     * @param ctxKey       {@code $ctx} 参数规范化串
     * @param snapshotHash 注册表快照内容哈希
     * @return 缓存键
     */
    public static String key(Long siteId, String pageRef, String slotName,
                             @Nullable String themeKey, String ctxKey, String snapshotHash) {
        return String.join("|", String.valueOf(siteId), pageRef, slotName,
                themeKey != null ? themeKey : "", ctxKey, snapshotHash);
    }
}

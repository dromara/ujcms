package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.mapper.CacheStampMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 构成缓存跨节点同步门面（composition-invalidation 计划 §2）。构成内容变更（卡片 / 投放 /
 * 主题设置 / 区块项）统一经 {@link #invalidateAll(String)} 作废——它在本地立即清空
 * {@link PanelResolveCache}（沿袭既有行为），在<b>业务事务内</b>递增 {@code ujcms_cache_stamp}
 * 戳记（提交即与数据变更原子可见，不存在「先感知作废、后见到新数据」的窗口），并注册提交后钩子
 * 本地再作废一次——封堵「方法内作废后、提交前，并发解析把旧数据重新灌入缓存」的既有竞态。
 *
 * <p>其余节点经 {@link #poll()} 定时轮询同一行（默认 3 秒，单行索引查询），值变则本地全量作废
 * （不回递增——那是源节点职责）。首次观测只记基线不作废（启动时缓存本就为空）。
 * 单调计数器幂等：漏读多次递增与读到一次效果相同。注册表重载（主题安装等）不经此路——快照哈希
 * 在缓存键内，各节点重载后旧键自然作废。
 *
 * <p>{@code ujcms.composition.cache-sync-enabled=false}（单节点部署可选）仅停用戳记递增与轮询；
 * 本地作废不受影响。轮询数据库故障采用状态翻转日志（首次失败 warn、恢复 info），
 * 期间继续用既有缓存服务，绝不中断调度。
 *
 * @author PONY
 */
@Component
public class CompositionCacheSync {
    private static final Logger logger = LoggerFactory.getLogger(CompositionCacheSync.class);

    /**
     * 投放解析缓存的戳记名
     */
    public static final String STAMP_COMPOSITION = "composition";

    private final PanelResolveCache resolveCache;
    private final CacheStampMapper stampMapper;
    private final boolean syncEnabled;
    private final Counter bumpCounter;
    private final Counter pollCounter;
    /**
     * 最后观测到的戳记值。{@code -1} 表示尚未建立基线（首次轮询只记录不作废）；
     * 源节点提交后以自身递增值预置，避免下次轮询对自己的变更重复作废。
     */
    private final AtomicLong lastSeen = new AtomicLong(-1L);
    private volatile boolean pollFailing = false;

    public CompositionCacheSync(PanelResolveCache resolveCache, CacheStampMapper stampMapper,
                                @Value("${ujcms.composition.cache-sync-enabled:true}") boolean syncEnabled,
                                MeterRegistry meterRegistry) {
        this.resolveCache = resolveCache;
        this.stampMapper = stampMapper;
        this.syncEnabled = syncEnabled;
        this.bumpCounter = Counter.builder("ujcms.composition.cache.sync.count").tag("action", "bump")
                .register(meterRegistry);
        this.pollCounter = Counter.builder("ujcms.composition.cache.sync.count").tag("action", "poll")
                .register(meterRegistry);
    }

    /**
     * 作废全部投放解析缓存（本地 + 集群）。在业务事务内调用；无事务环境（防御）则即时执行
     * 提交后动作。
     *
     * @param reason 触发原因（placement / theme-settings / block-item / page-region / admin）
     * @return 递增后的戳记值（同步停用时为 0）
     */
    public long invalidateAll(String reason) {
        resolveCache.invalidateAll();
        long seq = 0L;
        if (syncEnabled) {
            seq = bump();
            bumpCounter.increment();
        }
        long observed = seq;
        Runnable postCommit = () -> {
            resolveCache.invalidateAll();
            if (observed > 0L) {
                lastSeen.accumulateAndGet(observed, Math::max);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    postCommit.run();
                }
            });
        } else {
            postCommit.run();
        }
        return seq;
    }

    /**
     * 戳记递增。种子行由 changelog 预置；行缺失（人工删除）时补插，并发补插撞唯一键则重试递增
     * （该兜底路径在 PostgreSQL 上会因事务中止而失败——仅人工删行后并发保存才可能触达，接受）。
     */
    private long bump() {
        OffsetDateTime now = OffsetDateTime.now();
        if (stampMapper.increment(STAMP_COMPOSITION, now) == 0) {
            try {
                stampMapper.insert(STAMP_COMPOSITION, 1L, now);
                return 1L;
            } catch (DataIntegrityViolationException e) {
                stampMapper.increment(STAMP_COMPOSITION, now);
            }
        }
        Long value = stampMapper.selectValue(STAMP_COMPOSITION);
        return value != null ? value : 1L;
    }

    /**
     * 集群戳记轮询（每节点执行）。值变则本地全量作废；首次观测仅建立基线。
     */
    @Scheduled(fixedDelayString = "${ujcms.composition.cache-sync-poll-millis:3000}")
    public void poll() {
        if (!syncEnabled) {
            return;
        }
        try {
            Long value = stampMapper.selectValue(STAMP_COMPOSITION);
            long current = value != null ? value : 0L;
            if (pollFailing) {
                pollFailing = false;
                logger.info("Cache stamp poll recovered (value={})", current);
            }
            long prev = lastSeen.get();
            if (prev < 0L) {
                lastSeen.compareAndSet(prev, current);
                return;
            }
            if (current != prev) {
                lastSeen.set(current);
                resolveCache.invalidateAll();
                pollCounter.increment();
                logger.debug("Composition resolve cache invalidated by cluster stamp change ({} -> {})",
                        prev, current);
            }
        } catch (RuntimeException e) {
            if (!pollFailing) {
                pollFailing = true;
                logger.warn("Cache stamp poll failing, serving existing cache until recovery: {}", e.toString());
            }
        }
    }
}

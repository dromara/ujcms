package com.ujcms.cms.core.composition;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 主题生命周期互斥（§12.5）。每站点一把可重入锁，由主题安装/卸载/激活/更新与文件监听器重扫共用：
 * 安装期间监听到的变更被锁挡在外、合并到安装后的重扫；半完成的解包目录不会被监听器读到。
 *
 * <p>按站点加锁——不同站点可并发操作。文件监听器在重扫前对受影响站点加锁
 * （路径 {@code templates/{siteId}/...} 推断站点）。
 *
 * <p><b>进程内锁，刻意不做分布式</b>（design-review P3-2 / H4）：多节点共享模板存储时，主题
 * 生命周期操作是文档化的单写者约束——注册表重扫本就是各节点本地行为（远端存储无文件监听），
 * 只加分布式锁是安全剧场。决策与触发条件见 {@code docs/decisions/theme-marketplace/05}，操作规程见
 * {@code docs/guide/theme-ops.zh.md}。
 *
 * @author PONY
 */
@Component
public class ThemeLifecycleLock {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(Long siteId) {
        return locks.computeIfAbsent(siteId, k -> new ReentrantLock());
    }

    /**
     * 在站点锁内执行（有返回值）。
     */
    public <T> T withSiteLock(Long siteId, Supplier<T> action) {
        ReentrantLock lock = lockFor(siteId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在站点锁内执行（无返回值）。
     */
    public void withSiteLock(Long siteId, Runnable action) {
        withSiteLock(siteId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 在多个站点锁内执行（按站点ID升序加锁、逆序释放，避免死锁）。文件监听器重扫时使用：
     * 锁住本轮变更涉及的站点，与各站点的安装互斥。空集合则直接执行。
     *
     * @param siteIds 受影响站点ID集合
     * @param action  执行体
     */
    public void withSiteLocks(Collection<Long> siteIds, Runnable action) {
        List<Long> ordered = new ArrayList<>(new TreeSet<>(siteIds));
        List<ReentrantLock> acquired = new ArrayList<>(ordered.size());
        try {
            for (Long siteId : ordered) {
                ReentrantLock lock = lockFor(siteId);
                lock.lock();
                acquired.add(lock);
            }
            action.run();
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).unlock();
            }
        }
    }
}

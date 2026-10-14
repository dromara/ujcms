package com.ujcms.cms.core.component;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 首页「概览」统计缓存的失效器（console-home H6）：文章 / 栏目 / 用户 / 附件新增、删除后使对应桶失效，
 * 操作员发完文章回到首页就能看到数字动，不必等 {@link ContentStatCache#EXPIRES_MINUTE} 分钟过期。
 * <p>
 * 与 {@link ContentStatCache} 分开成两个 bean：Service 要调用失效器，而统计缓存又依赖这些 Service，
 * 合在一起就是循环依赖。键与 {@code @Cacheable} 的 SpEL（{@code 'article'+#siteId}）拼出的字符串一致。
 * 在事务内调用时推迟到提交之后再失效——否则提交前恰好来的一次首页请求会把旧数重新缓存起来。
 *
 * @author PONY
 */
@Component
public class ContentStatEvictor {
    private final CacheManager cacheManager;

    public ContentStatEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void article(Long siteId) {
        evict("article" + siteId);
    }

    public void channel(Long siteId) {
        evict("channel" + siteId);
    }

    public void user() {
        evict("user");
    }

    public void attachment(Long siteId) {
        evict("attachment" + siteId);
    }

    private void evict(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict(key);
                }
            });
        } else {
            doEvict(key);
        }
    }

    private void doEvict(String key) {
        Cache cache = cacheManager.getCache(ContentStatCache.CACHE_NAME);
        if (cache != null) {
            cache.evict(key);
        }
    }
}

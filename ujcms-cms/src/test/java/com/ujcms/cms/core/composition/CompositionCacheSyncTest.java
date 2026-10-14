package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.mapper.CacheStampMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CompositionCacheSync} 单元测试（composition-invalidation 计划 §7）：
 * 事务内递增 + 提交后通知、无事务即时通知、同步停用仅跳过戳记、轮询基线 / 变更作废 / 数据库故障容错、
 * 源节点自增不被自身轮询重复作废。
 *
 * @author PONY
 */
class CompositionCacheSyncTest {
    private PanelResolveCache resolveCache;
    private CacheStampMapper stampMapper;

    @BeforeEach
    void setUp() {
        resolveCache = mock(PanelResolveCache.class);
        stampMapper = mock(CacheStampMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private CompositionCacheSync sync(boolean enabled) {
        return new CompositionCacheSync(resolveCache, stampMapper, enabled, new SimpleMeterRegistry());
    }

    @Test
    void invalidateAllWithoutTxBumpsAndNotifiesImmediately() {
        when(stampMapper.increment(anyString(), any(OffsetDateTime.class))).thenReturn(1);
        when(stampMapper.selectValue(CompositionCacheSync.STAMP_COMPOSITION)).thenReturn(5L);

        long seq = sync(true).invalidateAll("panel");

        assertEquals(5L, seq);
        // 方法内一次 + 提交后（无事务→即时）一次
        verify(resolveCache, times(2)).invalidateAll();
    }

    @Test
    void invalidateAllInTxDefersNotifyAndSecondInvalidateUntilCommit() {
        when(stampMapper.increment(anyString(), any(OffsetDateTime.class))).thenReturn(1);
        when(stampMapper.selectValue(CompositionCacheSync.STAMP_COMPOSITION)).thenReturn(7L);
        TransactionSynchronizationManager.initSynchronization();

        long seq = sync(true).invalidateAll("placement");

        assertEquals(7L, seq);
        verify(resolveCache, times(1)).invalidateAll();
        // 递增已在事务内发生
        verify(stampMapper).increment(eq(CompositionCacheSync.STAMP_COMPOSITION), any(OffsetDateTime.class));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(resolveCache, times(2)).invalidateAll();
    }

    @Test
    void syncDisabledSkipsStampButKeepsLocalInvalidateAndPurge() {
        long seq = sync(false).invalidateAll("theme-settings");

        assertEquals(0L, seq);
        verifyNoInteractions(stampMapper);
        verify(resolveCache, times(2)).invalidateAll();
    }

    @Test
    void missingStampRowIsReinserted() {
        when(stampMapper.increment(anyString(), any(OffsetDateTime.class))).thenReturn(0);
        when(stampMapper.insert(anyString(), anyLong(), any(OffsetDateTime.class))).thenReturn(1);

        long seq = sync(true).invalidateAll("panel");

        assertEquals(1L, seq);
        verify(stampMapper).insert(eq(CompositionCacheSync.STAMP_COMPOSITION), eq(1L), any(OffsetDateTime.class));
    }

    @Test
    void pollBaselinesFirstThenInvalidatesOnChangeOnly() {
        CompositionCacheSync sync = sync(true);
        when(stampMapper.selectValue(CompositionCacheSync.STAMP_COMPOSITION)).thenReturn(3L, 3L, 9L);

        sync.poll();
        verify(resolveCache, never()).invalidateAll();
        sync.poll();
        verify(resolveCache, never()).invalidateAll();
        sync.poll();
        verify(resolveCache, times(1)).invalidateAll();
    }

    @Test
    void pollDisabledIsNoOp() {
        sync(false).poll();
        verifyNoInteractions(stampMapper);
    }

    @Test
    void pollSurvivesDbErrorsAndRecovers() {
        CompositionCacheSync sync = sync(true);
        when(stampMapper.selectValue(CompositionCacheSync.STAMP_COMPOSITION))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(2L, 4L);

        assertDoesNotThrow(sync::poll);
        // 恢复后首次观测建立基线，再次变更才作废
        sync.poll();
        verify(resolveCache, never()).invalidateAll();
        sync.poll();
        verify(resolveCache, times(1)).invalidateAll();
    }

    @Test
    void ownBumpIsNotReInvalidatedByNextPoll() {
        when(stampMapper.increment(anyString(), any(OffsetDateTime.class))).thenReturn(1);
        when(stampMapper.selectValue(CompositionCacheSync.STAMP_COMPOSITION)).thenReturn(6L);
        CompositionCacheSync sync = sync(true);

        sync.invalidateAll("panel");
        verify(resolveCache, times(2)).invalidateAll();

        // 轮询读到的值等于自身递增后的值 → 不再作废
        sync.poll();
        verify(resolveCache, times(2)).invalidateAll();
    }
}

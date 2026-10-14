package com.ujcms.common.db.identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.ujcms.common.db.identifier.SnowflakeSequence.MAX_DATACENTER_ID;
import static com.ujcms.common.db.identifier.SnowflakeSequence.MAX_WORKER_ID;
import static com.ujcms.common.db.identifier.SnowflakeSequence.parseIdTimestamp;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SnowflakeSequence
 *
 * @author PONY
 */
@ExtendWith(OutputCaptureExtension.class)
class SnowflakeSequenceTest {
    /**
     * 时间起始点：ID 为 0 时反解出的时间戳
     */
    private static final long EPOCH = parseIdTimestamp(0);
    private static final long SEQUENCE_BITS = 11;
    private static final long WORKER_ID_BITS = 5;
    private static final long DATACENTER_ID_BITS = 5;
    private static final long TIMESTAMP_BITS = 42;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /**
     * 可控时钟
     */
    private static class FakeClockSequence extends SnowflakeSequence {
        long clock;

        FakeClockSequence(long datacenterId, long workerId, long clock) {
            super(datacenterId, workerId);
            this.clock = clock;
        }

        @Override
        protected long now() {
            return clock;
        }
    }

    @Test
    void layoutIs42TimestampBits5And5NodeBitsAnd11SequenceBits() {
        assertEquals(31, MAX_DATACENTER_ID);
        assertEquals(31, MAX_WORKER_ID);
        assertEquals(63, TIMESTAMP_BITS + TIMESTAMP_SHIFT, "layout must fill the 63 non-sign bits");
        assertEquals(1601481600000L, EPOCH, "epoch is 2020-10-01 00:00:00 +08:00");

        long elapsed = 123_456_789L;
        FakeClockSequence sequence = new FakeClockSequence(5, 7, EPOCH + elapsed);
        long id = sequence.nextId();
        assertEquals(elapsed, id >>> TIMESTAMP_SHIFT);
        assertEquals(5, (id >>> (SEQUENCE_BITS + WORKER_ID_BITS)) & MAX_DATACENTER_ID);
        assertEquals(7, (id >>> SEQUENCE_BITS) & MAX_WORKER_ID);
        long seq = id & ((1L << SEQUENCE_BITS) - 1);
        assertTrue(seq == 1 || seq == 2, "a fresh millisecond starts at sequence 1 or 2, got " + seq);
        assertEquals(EPOCH + elapsed, parseIdTimestamp(id));
    }

    @Test
    void lifespanEndsInYear2160AndOverflowIsRejected() {
        long maxElapsed = (1L << TIMESTAMP_BITS) - 1;
        assertEquals(2160, Instant.ofEpochMilli(EPOCH + maxElapsed).atOffset(ZoneOffset.UTC).getYear());

        FakeClockSequence sequence = new FakeClockSequence(31, 31, EPOCH + maxElapsed);
        long id = sequence.nextId();
        assertTrue(id > 0, "the sign bit must stay clear at the end of the lifespan");
        assertEquals(EPOCH + maxElapsed, parseIdTimestamp(id));

        sequence.clock = EPOCH + maxElapsed + 1;
        assertThrows(IllegalStateException.class, sequence::nextId);
    }

    @Test
    void sequenceExhaustionAtEndOfLifespanIsRejected() {
        long end = EPOCH + (1L << TIMESTAMP_BITS) - 1;
        FakeClockSequence sequence = new FakeClockSequence(31, 31, end);
        long id = sequence.nextId();
        long remainingIds = 2047L - (id & 2047L);
        for (int i = 0; i < remainingIds; i++) {
            long next = sequence.nextId();
            assertTrue(next > id);
            id = next;
        }

        assertEquals(Long.MAX_VALUE, id);
        assertEquals(end, parseIdTimestamp(id));
        assertThrows(IllegalStateException.class, sequence::nextId);
    }

    @Test
    void clockBeforeEpochIsRejected() {
        FakeClockSequence sequence = new FakeClockSequence(0, 0, EPOCH - 1);
        assertThrows(IllegalStateException.class, sequence::nextId);
    }

    @Test
    void sequenceExhaustionBorrowsTheNextMillisecondWithoutWaiting() {
        long start = EPOCH + 1_000;
        FakeClockSequence sequence = new FakeClockSequence(1, 1, start);
        long previous = -1;
        for (int i = 0; i < 8_000; i++) {
            long id = sequence.nextId();
            assertTrue(id > previous, "ids must be strictly increasing");
            previous = id;
        }
        // 每毫秒最多 2048 个：8000 个 ID 落在第 4 个毫秒，而时钟从未前进
        assertEquals(start + 3, parseIdTimestamp(previous));
    }

    @Test
    void clockRegressionKeepsIdsIncreasingWithoutThrowing() {
        long start = EPOCH + 5_000_000;
        FakeClockSequence sequence = new FakeClockSequence(2, 3, start);
        long previous = sequence.nextId();

        // 回拨一分钟：沿用逻辑时钟
        sequence.clock = start - 60_000;
        for (int i = 0; i < 100; i++) {
            long id = sequence.nextId();
            assertTrue(id > previous);
            assertEquals(start, parseIdTimestamp(id));
            previous = id;
        }

        // 时钟恢复：回到系统时间
        sequence.clock = start + 10;
        long id = sequence.nextId();
        assertTrue(id > previous);
        assertEquals(start + 10, parseIdTimestamp(id));
    }

    @Test
    void invalidClockReadingDoesNotPoisonTheLogicalClock() {
        long start = EPOCH + 1_000;
        FakeClockSequence sequence = new FakeClockSequence(1, 1, start);
        long previous = sequence.nextId();

        // 一次远超 2160 年的异常读数被拒绝，且不得写入逻辑时钟
        sequence.clock = EPOCH + (1L << TIMESTAMP_BITS) + 5_000;
        assertThrows(IllegalStateException.class, sequence::nextId);

        // 被拒绝的调用不改变任何状态：回到同一毫秒继续生成，序列号紧接上一个 ID
        sequence.clock = start;
        long resumed = sequence.nextId();
        assertEquals(previous + 1, resumed);

        // 时钟修正后按系统时间生成，不再受异常读数影响
        sequence.clock = start + 1_000;
        long id = sequence.nextId();
        assertTrue(id > resumed);
        assertEquals(start + 1_000, parseIdTimestamp(id));
    }

    @Test
    void clockBackwardsWarningIsRateLimitedAndRecoveryIsLogged(CapturedOutput output) {
        long start = EPOCH + 5_000_000;
        FakeClockSequence sequence = new FakeClockSequence(2, 3, start);
        sequence.nextId();

        // 回拨不足 1 秒：不记录
        sequence.clock = start - 500;
        sequence.nextId();
        assertEquals(0, count(output, "Clock moved backwards by"));

        // 回拨 1 秒并持续扩大 100 毫秒：只记录一条
        for (int i = 0; i < 100; i++) {
            sequence.clock = start - 1_000 - i;
            sequence.nextId();
        }
        assertEquals(1, count(output, "Clock moved backwards by"));

        // 回拨幅度再扩大 1 秒：记录第二条
        sequence.clock = start - 2_000;
        sequence.nextId();
        assertEquals(2, count(output, "Clock moved backwards by"));
        assertEquals(0, count(output, "System clock caught up"));

        // 系统时间追上逻辑时钟：记录一条恢复日志，之后不再重复
        sequence.clock = start + 1;
        sequence.nextId();
        sequence.nextId();
        assertEquals(1, count(output, "System clock caught up"));
        assertEquals(2, count(output, "Clock moved backwards by"));
    }

    private static int count(CapturedOutput output, String needle) {
        return (int) output.getAll().lines().filter(line -> line.contains(needle)).count();
    }

    @Test
    void idsAreUniqueAndPerThreadIncreasingUnderContention() throws Exception {
        SnowflakeSequence sequence = new SnowflakeSequence(31, 31);
        int threads = 8;
        int perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<long[]>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    long[] ids = new long[perThread];
                    long previous = -1;
                    for (int i = 0; i < perThread; i++) {
                        ids[i] = sequence.nextId();
                        assertTrue(ids[i] > previous);
                        previous = ids[i];
                    }
                    return ids;
                }));
            }
            Set<Long> all = new HashSet<>(threads * perThread * 2);
            for (Future<long[]> future : futures) {
                for (long id : future.get()) {
                    assertTrue(all.add(id), "duplicate id " + id);
                }
            }
            assertEquals(threads * perThread, all.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void newLayoutIdsSortAfterLegacyIdsAtTheUpgradeTimestamp() {
        // 旧布局左移 19 位，新布局左移 21 位，升级时同一时刻的新 ID 大于旧 ID
        long elapsed = 187_516_800_000L;
        FakeClockSequence sequence = new FakeClockSequence(9, 21, EPOCH + elapsed);
        long id = sequence.nextId();
        long legacyId = (elapsed << 19) | (9L << 14) | (21L << 9) | 511L;
        long expectedWithoutSequence = (elapsed << 21) | (9L << 16) | (21L << 11);
        assertEquals(expectedWithoutSequence, id & ~2047L);
        assertTrue(id > legacyId);
    }

    @Test
    void autoDerivedNodeIdIsInRangeAndStableWithinAProcess() {
        SnowflakeSequence first = new SnowflakeSequence();
        SnowflakeSequence second = new SnowflakeSequence();
        assertTrue(first.getDatacenterId() >= 0 && first.getDatacenterId() <= MAX_DATACENTER_ID);
        assertTrue(first.getWorkerId() >= 0 && first.getWorkerId() <= MAX_WORKER_ID);
        assertEquals(first.getDatacenterId(), second.getDatacenterId());
        assertEquals(first.getWorkerId(), second.getWorkerId());
        assertTrue(first.nextId() > 0);
    }

    @Test
    void explicitNodeIdIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeSequence(MAX_DATACENTER_ID + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeSequence(0, -1));
        assertDoesNotThrow(() -> new SnowflakeSequence(MAX_DATACENTER_ID, MAX_WORKER_ID));
    }
}

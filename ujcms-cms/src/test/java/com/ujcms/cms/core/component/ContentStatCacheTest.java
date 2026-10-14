package com.ujcms.cms.core.component;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 概览按日序列：由 30 次「自某日 0 点起累计」相减得出，last30day 等于序列之和（console-home D10）。
 */
class ContentStatCacheTest {
    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final int DAYS = ContentStatCache.DAYS;

    private static OffsetDateTime dayStart(int back) {
        return TODAY.minusDays(back).atStartOfDay(ZONE).toOffsetDateTime();
    }

    @Test
    void dailySeriesIsTheDifferenceOfCumulativeCounts_oldestFirst_todayLast() {
        // 期望的按日新增（最早在前、今天在后），据此构造“自某日 0 点起的累计”
        int[] expected = new int[DAYS];
        expected[0] = 3;
        expected[DAYS - 5] = 5;
        expected[DAYS - 3] = 4;
        expected[DAYS - 1] = 2;
        Map<OffsetDateTime, Integer> cumulative = new HashMap<>();
        int sum = 0;
        for (int back = 0; back < DAYS; back++) {
            sum += expected[DAYS - 1 - back];
            cumulative.put(dayStart(back), sum);
        }
        List<OffsetDateTime> asked = new ArrayList<>();
        ContentStatCache.StatBucket bucket = ContentStatCache.bucket(100, TODAY, ZONE, since -> {
            asked.add(since);
            return cumulative.get(since);
        });
        assertEquals(100, bucket.total());
        assertArrayEquals(expected, bucket.daily());
        assertEquals(14, bucket.last30day(), "30 天合计 = 自 29 天前 0 点起的累计 = 序列之和");
        assertEquals(DAYS, asked.size(), "每桶恰好 30 次自某日起计数");
        assertEquals(dayStart(0), asked.get(0), "从今天 0 点起问，问到 29 天前 0 点");
        assertEquals(dayStart(DAYS - 1), asked.get(DAYS - 1));
    }

    @Test
    void neverNegative_whenCountsShiftBetweenQueries() {
        // 两次查询之间有数据被删除（累计反而变小）时，某日不得为负
        int[] answers = {5, 3};
        int[] cursor = {0};
        ContentStatCache.StatBucket bucket = ContentStatCache.bucket(9, TODAY, ZONE,
                since -> cursor[0] < answers.length ? answers[cursor[0]++] : 3);
        int[] expected = new int[DAYS];
        expected[DAYS - 1] = 5;
        assertArrayEquals(expected, bucket.daily());
        assertEquals(3, bucket.last30day());
    }
}

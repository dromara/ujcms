package com.ujcms.cms.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ujcms.cms.core.component.WordScreeningService.WordHit;
import com.ujcms.cms.core.component.WordScreeningService.WordType;
import com.ujcms.cms.core.domain.ErrorWord;
import com.ujcms.cms.core.domain.SensitiveWord;
import com.ujcms.cms.core.service.ErrorWordService;
import com.ujcms.cms.core.service.SensitiveWordService;
import com.ujcms.cms.core.service.args.ErrorWordArgs;
import com.ujcms.cms.core.service.args.SensitiveWordArgs;

/**
 * 词库筛查引擎测试（docs/spec/ai/screening.md §8.2、docs/spec/word-dictionaries/plan.md D3）
 *
 * @author PONY
 */
class WordScreeningServiceTest {
    private SensitiveWordService sensitiveWordService;
    private ErrorWordService errorWordService;
    private WordScreeningService service;

    private List<String> sensitiveWords = List.of();
    private List<String> errorWords = List.of();
    private Map<String, String> corrections = Map.of();
    private boolean failNextSensitiveSelect;

    @BeforeEach
    void setUp() {
        sensitiveWordService = mock(SensitiveWordService.class);
        errorWordService = mock(ErrorWordService.class);
        when(sensitiveWordService.selectList(any(SensitiveWordArgs.class)))
                .thenAnswer(invocation -> {
                    if (failNextSensitiveSelect) {
                        failNextSensitiveSelect = false;
                        throw new RuntimeException("simulated db failure");
                    }
                    return sensitiveWords.stream().map(name -> {
                        SensitiveWord word = new SensitiveWord();
                        word.setName(name);
                        return word;
                    }).toList();
                });
        when(errorWordService.selectList(any(ErrorWordArgs.class)))
                .thenAnswer(invocation -> errorWords.stream().map(name -> {
                    ErrorWord word = new ErrorWord();
                    word.setName(name);
                    word.setCorrect(corrections.getOrDefault(name, ""));
                    return word;
                }).toList());
        service = new WordScreeningService(sensitiveWordService, errorWordService);
    }

    private void words(List<String> sensitive, List<String> error) {
        this.sensitiveWords = sensitive;
        this.errorWords = error;
        this.corrections = Map.of();
        service.refresh();
    }

    private void errorWords(Map<String, String> corrections) {
        this.sensitiveWords = List.of();
        this.errorWords = List.copyOf(corrections.keySet());
        this.corrections = corrections;
        service.refresh();
    }

    @Test
    void screenReportsOverlappingAndAdjacentHits() {
        words(List.of("他妈的", "妈的", "傻子"), List.of());
        // 重叠命中："他妈的" 与 "妈的"；相邻命中："傻子" 紧随其后
        List<WordHit> hits = service.screen("他妈的傻子");
        assertEquals(3, hits.size());
        assertTrue(hits.contains(new WordHit("他妈的", WordType.SENSITIVE, 0, 3)));
        assertTrue(hits.contains(new WordHit("妈的", WordType.SENSITIVE, 1, 3)));
        assertTrue(hits.contains(new WordHit("傻子", WordType.SENSITIVE, 3, 5)));
    }

    @Test
    void screenReturnsExactOffsetsInMixedCjkAsciiText() {
        words(List.of("bad词"), List.of("的地得"));
        String text = "abc的地得xyz bad词!";
        List<WordHit> hits = service.screen(text);
        assertEquals(2, hits.size());
        for (WordHit hit : hits) {
            // 偏移契约：text.substring(start, end) 即命中词
            assertEquals(hit.word(), text.substring(hit.start(), hit.end()));
        }
        assertTrue(hits.contains(new WordHit("的地得", WordType.ERROR, 3, 6)));
        assertTrue(hits.contains(new WordHit("bad词", WordType.SENSITIVE, 10, 14)));
    }

    @Test
    void screenReportsBothTypesWhenWordInBothDictionaries() {
        words(List.of("重合词"), List.of("重合词"));
        List<WordHit> hits = service.screen("这是重合词。");
        assertEquals(2, hits.size());
        assertTrue(hits.contains(new WordHit("重合词", WordType.SENSITIVE, 2, 5)));
        assertTrue(hits.contains(new WordHit("重合词", WordType.ERROR, 2, 5)));
    }

    @Test
    void sensitiveWordsMatchIgnoringCase() {
        words(List.of("Bad"), List.of());
        // 命中词报告词库写法；原文写法由偏移量取得
        assertEquals(List.of(new WordHit("Bad", WordType.SENSITIVE, 2, 5)), service.screen("a bAd word"));
        assertEquals(1, service.screen("BAD").size());
    }

    @Test
    void sensitiveEntriesDifferingOnlyInCaseHitOnce() {
        words(List.of("VPN", "vpn"), List.of());
        assertEquals(1, service.screen("use a Vpn").size());
    }

    @Test
    void errorWordsMatchExactCaseAndCarryTheCorrection() {
        errorWords(Map.of("Wechat", "WeChat"));
        // 修正后的写法不得再次命中，否则永远修不完
        assertEquals(List.of(new WordHit("Wechat", WordType.ERROR, 0, 6, "WeChat")), service.screen("Wechat and WeChat"));
    }

    @Test
    void errorWordWithoutCorrectionHasNullCorrect() {
        words(List.of(), List.of("帐号"));
        List<WordHit> hits = service.screen("登录帐号");
        assertEquals(1, hits.size());
        assertNull(hits.get(0).correct());
    }

    @Test
    void errorHitIsSuppressedWhereTextAlreadyReadsCorrect() {
        errorWords(Map.of("台湾", "中国台湾"));
        // 第一处已是正确写法，只报第二处
        assertEquals(List.of(new WordHit("台湾", WordType.ERROR, 5, 7, "中国台湾")), service.screen("中国台湾与台湾"));
        // 正确词中易错词出现在多个位置时，逐个位置比对
        errorWords(Map.of("哈", "哈哈哈"));
        assertTrue(service.screen("哈哈哈").isEmpty());
        assertEquals(2, service.screen("哈哈").size());
    }

    @Test
    void blankEntriesAreSkipped() {
        // 单个空格的词条会命中几乎所有文本
        words(List.of(" ", ""), List.of("　", "\t"));
        assertTrue(service.screen("a b　c\td").isEmpty());
    }

    @Test
    void hitsAreOrderedByPosition() {
        words(List.of("敏感"), List.of("易错"));
        List<WordHit> hits = service.screen("易错在前，敏感在后");
        assertEquals(WordType.ERROR, hits.get(0).type());
        assertEquals(WordType.SENSITIVE, hits.get(1).type());
    }

    @Test
    void screenReturnsEmptyOnEmptyDictionaryOrText() {
        words(List.of(), List.of());
        assertTrue(service.screen("任意文本").isEmpty());
        words(List.of("敏感"), List.of());
        assertTrue(service.screen("").isEmpty());
        assertTrue(service.screen(null).isEmpty());
    }

    @Test
    void refreshTakesEffectOnNextCall() {
        words(List.of("旧词"), List.of());
        assertEquals(1, service.screen("旧词在此").size());
        assertTrue(service.screen("新词在此").isEmpty());
        // 词库新增后 refresh()，下次筛查生效
        words(List.of("旧词", "新词"), List.of());
        assertEquals(1, service.screen("新词在此").size());
    }

    @Test
    void failedRebuildServesStaleSnapshotAndRetriesNextCall() {
        words(List.of("旧词"), List.of());
        assertEquals(1, service.screen("旧词在此").size());
        words(List.of("旧词", "新词"), List.of());
        // 词库变更后的首次重建失败（如数据库瞬时故障）：降级续用旧快照，不上抛
        failNextSensitiveSelect = true;
        assertEquals(1, service.screen("旧词与新词").size());
        // 脏标记已恢复：下次筛查重试重建成功，新词生效
        assertEquals(2, service.screen("旧词与新词").size());
    }

    @Test
    void refreshInsideTransactionDefersDirtyMarkToAfterCommit() {
        words(List.of("旧词"), List.of());
        assertEquals(1, service.screen("旧词在此").size());
        TransactionSynchronizationManager.initSynchronization();
        try {
            sensitiveWords = List.of("旧词", "新词");
            service.refresh();
            // 事务未提交：脏标记不得置位，并发筛查不会消耗掉本次变更
            assertEquals(1, service.screen("旧词与新词").size());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        // 提交后置位：下次筛查重建生效
        assertEquals(2, service.screen("旧词与新词").size());
    }

    @Test
    void maskReplacesSensitiveHitsOnly() {
        words(List.of("敏感"), List.of("易错"));
        assertEquals("这是**词与易错词", service.mask("这是敏感词与易错词", '*'));
    }

    @Test
    void screensFiveThousandCharsAgainstTenThousandWordsQuickly() {
        List<String> dictionary = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            dictionary.add("敏感词条" + i);
        }
        words(dictionary, List.of());
        StringBuilder text = new StringBuilder(5_000);
        while (text.length() < 4_980) {
            text.append("这是一段用于性能测试的普通中文内容，");
        }
        text.append("敏感词条9999");
        String input = text.toString();
        // 预热：触发自动机构建与 JIT
        service.screen(input);
        long start = System.nanoTime();
        List<WordHit> hits = service.screen(input);
        long elapsedMicros = (System.nanoTime() - start) / 1_000;
        long elapsedMillis = elapsedMicros / 1_000;
        System.out.println("screened 5k chars against 10k words in " + elapsedMicros + "µs");
        // "敏感词条9999" 同时命中前缀词条 9/99/999/9999（重叠命中不忽略）
        assertEquals(4, hits.size());
        // 规格要求 <5ms；给CI留余量断言 <50ms，实际耗时随断言消息输出
        assertTrue(elapsedMillis < 50, "screening took " + elapsedMillis + "ms, expected <50ms (spec target <5ms)");
    }
}

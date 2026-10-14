package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.SensitiveWord;
import com.ujcms.cms.core.mapper.SensitiveWordMapper;
import com.ujcms.cms.core.service.SensitiveWordService.ImportOutcome;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 敏感词词库完整性（docs/spec/word-dictionaries/plan.md Phase 3、Phase 7）：规范化、空白与重复校验、
 * 仅校验改动的字段、导入去重。
 */
class SensitiveWordServiceTest {
    private SensitiveWordMapper mapper;
    private WordScreeningService wordScreeningService;
    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SensitiveWordMapper.class);
        wordScreeningService = mock(WordScreeningService.class);
        service = new SensitiveWordService(mapper, mock(SnowflakeSequence.class), wordScreeningService);
    }

    private static SensitiveWord word(long id, String name) {
        SensitiveWord bean = new SensitiveWord();
        bean.setId(id);
        bean.setName(name);
        return bean;
    }

    private void existing(SensitiveWord... words) {
        when(mapper.selectAll(any())).thenReturn(List.of(words));
    }

    @Test
    void insertNormalizesWhitespace() {
        existing();
        SensitiveWord bean = word(0, "  free\t 　 vpn\n");
        service.insert(bean);
        assertEquals("free vpn", bean.getName());
        verify(mapper).insert(bean);
        verify(wordScreeningService).refresh();
    }

    @Test
    void insertRejectsBlank() {
        existing();
        // 单个空格的词条会命中几乎所有文本，拦住每一次文章保存
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.insert(word(0, " 　 ")));
        assertEquals("error.sensitiveWord.blank", e.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void insertRejectsDuplicateIgnoringCase() {
        existing(word(1, "VPN"));
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.insert(word(0, "vpn")));
        assertEquals("error.sensitiveWord.exists", e.getCode());
    }

    @Test
    void updateWithUnchangedNameSkipsValidation() {
        // 存量数据：重复与空白词条。只切换启用状态时不得被新校验挡住
        existing(word(1, "重复"), word(2, "重复"), word(3, " "));
        SensitiveWord duplicate = word(1, "重复");
        duplicate.setEnabled(false);
        service.update(duplicate, "重复");
        SensitiveWord blank = word(3, " ");
        blank.setEnabled(false);
        service.update(blank, " ");
        verify(mapper).update(duplicate);
        verify(mapper).update(blank);
    }

    @Test
    void updateWithChangedNameValidatesAgainstOtherRows() {
        existing(word(1, "Alpha"), word(2, "Beta"));
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.update(word(1, "beta"), "Alpha"));
        assertEquals("error.sensitiveWord.exists", e.getCode());
        // 仅改大小写：与自身不算重复
        SensitiveWord recased = word(1, "ALPHA ");
        service.update(recased, "Alpha");
        assertEquals("ALPHA", recased.getName());
        verify(mapper).update(recased);
    }

    @Test
    void importSkipsDuplicatesAndCountsInvalid() {
        existing(word(1, "VPN"));
        ImportOutcome outcome = service.importWords(Arrays.asList(
                "vpn", "  new   word ", "", null, "x".repeat(31), "dup", "DUP"));
        assertEquals(new ImportOutcome(2, 2, 3), outcome);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SensitiveWord>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(captor.capture());
        assertEquals(List.of("new word", "dup"), captor.getValue().stream().map(SensitiveWord::getName).toList());
        verify(wordScreeningService).refresh();
    }

    @Test
    void importInsertsInChunksAndSkipsRefreshWhenNothingCreated() {
        existing();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 1201; i++) {
            names.add("词条" + i);
        }
        assertEquals(new ImportOutcome(1201, 0, 0), service.importWords(names));
        verify(mapper, times(3)).insertBatch(anyList());

        SensitiveWordMapper emptyMapper = mock(SensitiveWordMapper.class);
        WordScreeningService screening = mock(WordScreeningService.class);
        when(emptyMapper.selectAll(any())).thenReturn(List.of(word(1, "已有")));
        SensitiveWordService other = new SensitiveWordService(emptyMapper, mock(SnowflakeSequence.class), screening);
        assertEquals(new ImportOutcome(0, 1, 0), other.importWords(List.of("已有")));
        verify(emptyMapper, never()).insertBatch(anyList());
        verify(screening, never()).refresh();
    }
}

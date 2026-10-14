package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.ErrorWord;
import com.ujcms.cms.core.mapper.ErrorWordMapper;
import com.ujcms.cms.core.service.ErrorWordService.ImportOutcome;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 易错词词库完整性（docs/spec/word-dictionaries/plan.md Phase 3、Phase 7）：规范化、空白/相同/重复校验、
 * 仅校验改动的字段、导入去重与冲突清单。
 */
class ErrorWordServiceTest {
    private ErrorWordMapper mapper;
    private WordScreeningService wordScreeningService;
    private ErrorWordService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ErrorWordMapper.class);
        wordScreeningService = mock(WordScreeningService.class);
        service = new ErrorWordService(mapper, mock(SnowflakeSequence.class), wordScreeningService);
    }

    private static ErrorWord word(long id, String name, String correct) {
        ErrorWord bean = new ErrorWord();
        bean.setId(id);
        bean.setName(name);
        bean.setCorrect(correct);
        return bean;
    }

    private void existing(ErrorWord... words) {
        when(mapper.selectAll(any())).thenReturn(List.of(words));
    }

    @Test
    void insertNormalizesBothWords() {
        existing();
        ErrorWord bean = word(0, " 帐号 ", "账号\n");
        service.insert(bean);
        assertEquals("帐号", bean.getName());
        assertEquals("账号", bean.getCorrect());
        verify(mapper).insert(bean);
        verify(wordScreeningService).refresh();
    }

    @Test
    void insertRejectsBlankAndSameAsCorrect() {
        existing();
        assertEquals("error.errorWord.blank",
                assertThrows(Http400Exception.class, () -> service.insert(word(0, " ", "账号"))).getCode());
        assertEquals("error.errorWord.blank",
                assertThrows(Http400Exception.class, () -> service.insert(word(0, "帐号", ""))).getCode());
        // 规范化后相同也算相同
        assertEquals("error.errorWord.sameAsCorrect",
                assertThrows(Http400Exception.class, () -> service.insert(word(0, "账号", " 账号 "))).getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void duplicateCheckIsCaseSensitive() {
        existing(word(1, "Wechat", "WeChat"));
        assertEquals("error.errorWord.exists",
                assertThrows(Http400Exception.class, () -> service.insert(word(0, "Wechat", "微信"))).getCode());
        // 易错词精确匹配：仅大小写不同是另一个词条
        ErrorWord lower = word(0, "wechat", "WeChat");
        service.insert(lower);
        verify(mapper).insert(lower);
    }

    @Test
    void updateWithUnchangedWordsSkipsValidation() {
        // 存量数据：重复词条、易错词与正确词相同的词条。只切换启用状态时不得被新校验挡住
        existing(word(1, "重复", "甲"), word(2, "重复", "乙"), word(3, "相同", "相同"));
        ErrorWord duplicate = word(1, "重复", "甲");
        duplicate.setEnabled(false);
        service.update(duplicate, "重复", "甲");
        ErrorWord same = word(3, "相同", "相同");
        same.setEnabled(false);
        service.update(same, "相同", "相同");
        verify(mapper).update(duplicate);
        verify(mapper).update(same);
    }

    @Test
    void updateChecksDuplicatesOnlyWhenNameChanged() {
        existing(word(1, "重复", "甲"), word(2, "重复", "乙"), word(3, "其他", "丙"));
        // 只改正确词：存量重复不拦
        ErrorWord corrected = word(1, "重复", "丁");
        service.update(corrected, "重复", "甲");
        verify(mapper).update(corrected);
        // 改易错词：与他行重复则拒绝
        assertEquals("error.errorWord.exists", assertThrows(Http400Exception.class,
                () -> service.update(word(3, "重复", "丙"), "其他", "丙")).getCode());
        // 改后与正确词相同：拒绝
        assertEquals("error.errorWord.sameAsCorrect", assertThrows(Http400Exception.class,
                () -> service.update(word(3, "其他", "其他"), "其他", "丙")).getCode());
    }

    @Test
    void importSkipsDuplicatesAndReportsConflicts() {
        existing(word(1, "帐号", "账号"), word(2, "登陆", "登录"));
        ImportOutcome outcome = service.importWords(Arrays.asList(
                word(0, "帐号", "账号"),          // 已有，正确词一致：重复
                word(0, "登陆", "登入"),          // 已有，正确词不同：重复 + 冲突
                word(0, " 按装 ", "安装"),        // 新增
                word(0, "按装", "装置"),          // 本批内重复，正确词不同：重复 + 冲突
                word(0, "相同", "相同"),          // 无效
                word(0, "", "空"),               // 无效
                word(0, "超长".repeat(16), "长"), // 无效
                null));                          // 无效
        assertEquals(new ImportOutcome(1, 3, 4, List.of("登陆", "按装")), outcome);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ErrorWord>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("按装", captor.getValue().get(0).getName());
        assertEquals("安装", captor.getValue().get(0).getCorrect());
        verify(wordScreeningService).refresh();
    }
}

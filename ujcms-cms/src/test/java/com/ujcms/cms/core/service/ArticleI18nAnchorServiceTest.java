package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleI18n;
import com.ujcms.cms.core.mapper.ArticleI18nMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArticleI18nAnchorService} 过期锚点维护测试（translation-workflow.md）：
 * 编辑保存分类（原创编辑/追赶保存/非正常状态豁免/内容未变豁免）、显式标记、建链钳制、过期判定同刻不算过期。
 */
class ArticleI18nAnchorServiceTest {
    private static final OffsetDateTime BASE = OffsetDateTime.now().minusDays(30);

    private ArticleI18nMapper mapper;
    private ArticleI18nAnchorService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleI18nMapper.class);
        service = new ArticleI18nAnchorService(mapper);
    }

    private static ArticleI18n row(long articleId, OffsetDateTime contentDate, OffsetDateTime syncedDate) {
        return new ArticleI18n(articleId, 5L, contentDate, syncedDate);
    }

    @Test
    void trackEditorialSaveNoOpWhenUnlinked() {
        when(mapper.select(10L)).thenReturn(null);
        service.trackEditorialSave(10L, true, true, OffsetDateTime.now());
        verify(mapper, never()).updateSyncedDate(anyLong(), any());
        verify(mapper, never()).updateAnchors(anyLong(), any(), any());
    }

    /**
     * 原创编辑：正常状态且未过期 → 双锚点推进
     */
    @Test
    void trackEditorialSaveOriginalEditBumpsBothAnchors() {
        ArticleI18n self = row(10L, BASE, BASE.plusDays(2));
        when(mapper.select(10L)).thenReturn(self);
        when(mapper.listByGroupIds(List.of(5L)))
                .thenReturn(List.of(self, row(20L, BASE.plusDays(1), BASE.plusDays(1))));
        OffsetDateTime saveTime = OffsetDateTime.now();
        service.trackEditorialSave(10L, true, true, saveTime);
        verify(mapper).updateAnchors(10L, saveTime, saveTime);
        verify(mapper, never()).updateSyncedDate(anyLong(), any());
    }

    /**
     * 追赶保存：保存时已过期 → 仅推进 synced_date_（不把追赶误报为原创修改）
     */
    @Test
    void trackEditorialSaveCatchUpBumpsSyncedOnly() {
        ArticleI18n self = row(10L, BASE, BASE);
        when(mapper.select(10L)).thenReturn(self);
        when(mapper.listByGroupIds(List.of(5L)))
                .thenReturn(List.of(self, row(20L, BASE.plusDays(3), BASE.plusDays(3))));
        OffsetDateTime saveTime = OffsetDateTime.now();
        service.trackEditorialSave(10L, true, true, saveTime);
        verify(mapper).updateSyncedDate(10L, saveTime);
        verify(mapper, never()).updateAnchors(anyLong(), any(), any());
    }

    /**
     * 内容未变豁免：正常状态、未过期，但保存内容与库中一致（新增译文后顺手点保存）→ 仅推进 synced_date_，
     * 对端不被标为“可能过期”
     */
    @Test
    void trackEditorialSaveUnchangedContentIsCatchUp() {
        ArticleI18n self = row(10L, BASE, BASE.plusDays(2));
        when(mapper.select(10L)).thenReturn(self);
        when(mapper.listByGroupIds(List.of(5L)))
                .thenReturn(List.of(self, row(20L, BASE.plusDays(1), BASE.plusDays(1))));
        OffsetDateTime saveTime = OffsetDateTime.now();
        service.trackEditorialSave(10L, true, false, saveTime);
        verify(mapper).updateSyncedDate(10L, saveTime);
        verify(mapper, never()).updateAnchors(anyLong(), any(), any());
    }

    /**
     * 非正常状态豁免：草稿保存即使未过期也不产生原创修改（译文草稿的翻译保存不得反向标记源文）
     */
    @Test
    void trackEditorialSaveDraftNeverProducesContent() {
        ArticleI18n self = row(10L, BASE, BASE.plusDays(5));
        when(mapper.select(10L)).thenReturn(self);
        when(mapper.listByGroupIds(List.of(5L)))
                .thenReturn(List.of(self, row(20L, BASE, BASE)));
        OffsetDateTime saveTime = OffsetDateTime.now();
        service.trackEditorialSave(10L, false, true, saveTime);
        verify(mapper).updateSyncedDate(10L, saveTime);
        verify(mapper, never()).updateAnchors(anyLong(), any(), any());
    }

    @Test
    void markSyncedDelegates() {
        OffsetDateTime time = OffsetDateTime.now();
        service.markSynced(10L, time);
        verify(mapper).updateSyncedDate(10L, time);
    }

    @Test
    void clampJoinContentDateNewGroupReturnsOwnModified() {
        OffsetDateTime modified = BASE.plusDays(1);
        assertEquals(modified, service.clampJoinContentDate(null, modified));
        verify(mapper, never()).listByGroupIds(any());
    }

    @Test
    void clampJoinContentDateClampsToMinExistingSynced() {
        when(mapper.listByGroupIds(List.of(5L))).thenReturn(List.of(
                row(10L, BASE, BASE.plusDays(2)), row(20L, BASE, BASE.plusDays(4))));
        assertEquals(BASE.plusDays(2), service.clampJoinContentDate(5L, BASE.plusDays(9)));
    }

    @Test
    void clampJoinContentDateKeepsOwnModifiedWhenOlder() {
        when(mapper.listByGroupIds(List.of(5L))).thenReturn(List.of(row(10L, BASE, BASE.plusDays(2))));
        assertEquals(BASE.plusDays(1), service.clampJoinContentDate(5L, BASE.plusDays(1)));
    }

    /**
     * 过期判定：严格大于——同刻不算过期；对端更晚才过期
     */
    @Test
    void isStaleStrictComparison() {
        ArticleI18n self = row(10L, BASE, BASE.plusDays(1));
        assertFalse(ArticleI18n.isStale(self, List.of(self, row(20L, BASE.plusDays(1), BASE))));
        assertTrue(ArticleI18n.isStale(self, List.of(self, row(20L, BASE.plusDays(2), BASE))));
        // 无对端（单员组）不过期
        assertFalse(ArticleI18n.isStale(self, List.of(self)));
    }
}

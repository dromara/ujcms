package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SiteService#listByLangGroup(Site)} 与 {@link SiteService#findByLangGroupAndLang(Long, String)}
 * 单元测试（multilingual P3，language-surface.md §1.2）
 */
class SiteServiceLangGroupTest {
    private SiteMapper mapper;
    private SiteService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SiteMapper.class);
        service = new SiteService(mock(HtmlSanitizeService.class), mock(AttachmentService.class),
                mock(ModelService.class), mapper, mock(OrgMapper.class));
    }

    private static Site site(long id, String lang, Long langParentId) {
        Site site = new Site();
        site.setId(id);
        site.setLang(lang);
        site.setLangParentId(langParentId);
        return site;
    }

    @Test
    void ungroupedSiteShortCircuitsToSingleton() {
        Site alone = site(1L, "zh-CN", null);
        when(mapper.countByLangParentId(1L)).thenReturn(0);
        List<Site> list = service.listByLangGroup(alone);
        assertEquals(1, list.size());
        assertSame(alone, list.get(0));
        verify(mapper, never()).listByLangGroup(anyLong());
    }

    @Test
    void masterWithVariantsFetchesGroupByOwnId() {
        Site master = site(1L, "zh-CN", null);
        Site variant = site(2L, "en", 1L);
        when(mapper.countByLangParentId(1L)).thenReturn(1);
        when(mapper.listByLangGroup(1L)).thenReturn(List.of(master, variant));
        assertEquals(2, service.listByLangGroup(master).size());
    }

    @Test
    void variantFetchesGroupByMasterIdWithoutCounting() {
        Site variant = site(2L, "en", 1L);
        when(mapper.listByLangGroup(1L)).thenReturn(List.of(site(1L, "zh-CN", null), variant));
        assertEquals(2, service.listByLangGroup(variant).size());
        verify(mapper, never()).countByLangParentId(anyLong());
    }

    @Test
    void findByLangGroupAndLangExactMatchWins() {
        Site en = site(2L, "en", 1L);
        Site enUs = site(3L, "en-US", 1L);
        when(mapper.listByLangGroup(1L)).thenReturn(List.of(site(1L, "zh-CN", null), en, enUs));
        assertSame(enUs, service.findByLangGroupAndLang(1L, "en-US"));
        assertSame(en, service.findByLangGroupAndLang(1L, "EN"));
    }

    /**
     * 主语言前缀匹配：en-GB 无精确项时匹配组内 en（en-US ~ en 同理）
     */
    @Test
    void findByLangGroupAndLangFallsBackToPrimaryLanguage() {
        Site en = site(2L, "en", 1L);
        when(mapper.listByLangGroup(1L)).thenReturn(List.of(site(1L, "zh-CN", null), en));
        assertSame(en, service.findByLangGroupAndLang(1L, "en-GB"));
        Site zh = site(4L, "zh-CN", null);
        when(mapper.listByLangGroup(1L)).thenReturn(List.of(zh, en));
        assertSame(zh, service.findByLangGroupAndLang(1L, "zh-TW"));
    }

    @Test
    void findByLangGroupAndLangMissReturnsNull() {
        when(mapper.listByLangGroup(1L)).thenReturn(List.of(site(1L, "zh-CN", null), site(2L, "en", 1L)));
        assertNull(service.findByLangGroupAndLang(1L, "fr"));
    }
}

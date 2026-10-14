package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SiteService#findBySubDir(String)} 遗留全局查找的歧义裁决测试（multilingual P2）：
 * 0条→null；1条→该站点；多条（跨域名重复子目录）→null 并 WARN——宁可404也不确定性地返回错误站点
 */
class SiteServiceFindBySubDirTest {
    private SiteMapper mapper;
    private SiteService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SiteMapper.class);
        service = new SiteService(mock(HtmlSanitizeService.class), mock(AttachmentService.class),
                mock(ModelService.class), mapper, mock(OrgMapper.class));
    }

    private static Site site(long id) {
        Site site = new Site();
        site.setId(id);
        return site;
    }

    @Test
    void noMatchReturnsNull() {
        when(mapper.listBySubDir("en")).thenReturn(Collections.emptyList());
        assertNull(service.findBySubDir("en"));
    }

    @Test
    void uniqueMatchReturnsSite() {
        Site site = site(2L);
        when(mapper.listBySubDir("en")).thenReturn(List.of(site));
        assertSame(site, service.findBySubDir("en"));
    }

    @Test
    void ambiguousMatchReturnsNull() {
        when(mapper.listBySubDir("en")).thenReturn(List.of(site(2L), site(4L)));
        assertNull(service.findBySubDir("en"));
    }
}

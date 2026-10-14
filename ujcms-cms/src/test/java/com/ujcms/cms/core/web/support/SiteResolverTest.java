package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http404Exception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SiteResolver} 组合寻址查找链测试（multilingual P2，addressing-core.md §2.3 语义表）：
 * 组合命中；组合未中→全局唯一回退命中；歧义/全部未中→404；空白子目录→域名→默认站点回退。
 * 控制器签名与映射模式不变，仅解析顺序变化。
 */
class SiteResolverTest {
    private SiteService siteService;
    private ConfigService configService;
    private SiteResolver resolver;

    @BeforeEach
    void setUp() {
        siteService = mock(SiteService.class);
        configService = mock(ConfigService.class);
        resolver = new SiteResolver(siteService, configService, mock(ThemeInstallService.class));
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
    }

    private static MockHttpServletRequest request(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(host);
        return request;
    }

    private static Site site(long id) {
        Site site = new Site();
        site.setId(id);
        site.setName("site-" + id);
        return site;
    }

    /**
     * 1. 组合寻址 (host, subDir) 权威命中，不再触发全局回退
     */
    @Test
    void compositeHitWins() {
        Site site = site(2L);
        when(siteService.findByDomainAndSubDir("a.example.com", "en")).thenReturn(site);
        assertSame(site, resolver.resolve(request("a.example.com"), "en"));
        assertSame(site, Contexts.getCurrentSite());
        verify(siteService, never()).findBySubDir(anyString());
    }

    /**
     * 2. 组合未中→遗留全局回退命中（存量部署 domain_ 空/过期、IP/localhost 访问的零迁移兼容）
     */
    @Test
    void compositeMissFallsBackToGlobalSubDir() {
        Site site = site(2L);
        when(siteService.findByDomainAndSubDir("localhost", "en")).thenReturn(null);
        when(siteService.findBySubDir("en")).thenReturn(site);
        assertSame(site, resolver.resolve(request("localhost"), "en"));
    }

    /**
     * 3. 全局查找歧义（服务层已裁决为 null）或全部未中→404，与原行为同状态码
     */
    @Test
    void totalMissThrows404() {
        when(siteService.findByDomainAndSubDir("localhost", "en")).thenReturn(null);
        when(siteService.findBySubDir("en")).thenReturn(null);
        Http404Exception e = assertThrows(Http404Exception.class,
                () -> resolver.resolve(request("localhost"), "en"));
        assertEquals("error.siteSubDirNotExist", e.getCode());
    }

    /**
     * 4. 空白子目录→域名解析（原路径不变）
     */
    @Test
    void blankSubDirResolvesByDomain() {
        Site site = site(1L);
        when(siteService.findByDomain("www.example.com")).thenReturn(site);
        assertSame(site, resolver.resolve(request("www.example.com"), (String) null));
        assertSame(site, resolver.resolve(request("www.example.com"), " "));
        verify(siteService, never()).findByDomainAndSubDir(anyString(), anyString());
    }

    /**
     * 5. 空白子目录且域名未中→默认站点回退（永不404）
     */
    @Test
    void blankSubDirDomainMissFallsBackToDefaultSite() {
        Site site = site(1L);
        when(siteService.findByDomain("127.0.0.1")).thenReturn(null);
        Config config = new Config();
        config.setDefaultSiteId(1L);
        when(configService.getUnique()).thenReturn(config);
        when(siteService.select(1L)).thenReturn(site);
        assertSame(site, resolver.resolve(request("127.0.0.1"), (String) null));
    }

    /**
     * 6. 关闭状态的站点在 established 收口处拒绝（组合寻址命中后同样生效）
     */
    @Test
    void disabledSiteRejected() {
        Site site = site(2L);
        site.setStatus(Site.STATUS_DISABLED);
        when(siteService.findByDomainAndSubDir("a.example.com", "en")).thenReturn(site);
        assertThrows(SiteDisabledException.class, () -> resolver.resolve(request("a.example.com"), "en"));
    }

    /**
     * 7. 显式站点ID命中→按ID解析，不查域名
     */
    @Test
    void explicitSiteIdResolvesById() {
        Site site = site(2L);
        when(siteService.select(2L)).thenReturn(site);
        assertSame(site, resolver.resolve(request("www.example.com"), 2L));
        verify(siteService, never()).findByDomain(anyString());
    }

    /**
     * 8. 生成实体默认值 0（*Params 继承实体、客户端省略 siteId）视为未提供→域名解析，不再报“站点ID不存在：0”
     */
    @Test
    void zeroSiteIdTreatedAsAbsent() {
        Site site = site(1L);
        when(siteService.findByDomain("www.example.com")).thenReturn(site);
        assertSame(site, resolver.resolve(request("www.example.com"), 0L));
        assertSame(site, resolver.resolve(request("www.example.com"), (Long) null));
        verify(siteService, never()).select(0L);
    }

    /**
     * 9. 显式站点ID未中→404（error.siteIdNotExist），与原行为一致
     */
    @Test
    void explicitSiteIdMissThrows404() {
        when(siteService.select(99L)).thenReturn(null);
        Http404Exception e = assertThrows(Http404Exception.class,
                () -> resolver.resolve(request("www.example.com"), 99L));
        assertEquals("error.siteIdNotExist", e.getCode());
    }
}

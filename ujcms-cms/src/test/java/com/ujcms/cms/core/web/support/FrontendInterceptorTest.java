package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.SandboxSiteRegistry;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ua_parser.Parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 前台拦截器 X-Robots-Tag 盖章测试（sandbox-preview-site §5 / S3）：站点为注册沙箱时响应头
 * 携带 {@code noindex, nofollow}，普通站点不携带——沙箱是启用的演示站，靠该头把爬虫挡在
 * 演示内容之外并避免生产域名重复内容。
 */
class FrontendInterceptorTest {
    private static final Long SITE_ID = 55L;

    private SandboxSiteRegistry sandboxSiteRegistry;
    private FrontendInterceptor interceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        SiteService siteService = mock(SiteService.class);
        ConfigService configService = mock(ConfigService.class);
        ThemeSettingsService themeSettingsService = mock(ThemeSettingsService.class);
        sandboxSiteRegistry = mock(SandboxSiteRegistry.class);
        Config config = mock(Config.class);
        when(config.getDefaultSiteId()).thenReturn(1L);
        Config.Storage storage = mock(Config.Storage.class);
        when(storage.getUrl()).thenReturn("/templates");
        when(config.getTemplateStorage()).thenReturn(storage);
        when(configService.getUnique()).thenReturn(config);

        Site site = mock(Site.class);
        when(site.getId()).thenReturn(SITE_ID);
        when(site.getDy()).thenReturn("/");
        when(site.getDynamicUrl()).thenReturn("/");
        when(site.getTheme()).thenReturn("default");
        Site defaultSite = mock(Site.class);
        when(defaultSite.getDynamicUrl()).thenReturn("/");
        when(siteService.getDefaultSite(1L)).thenReturn(defaultSite);

        interceptor = new FrontendInterceptor(mock(UserService.class), siteService, configService,
                themeSettingsService, mock(ManifestService.class), sandboxSiteRegistry, new Parser());
        Contexts.setCurrentSite(site);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
    }

    @Test
    void postHandleStampsNoindexOnSandboxSite() {
        when(sandboxSiteRegistry.isSandboxSite(SITE_ID)).thenReturn(true);
        interceptor.postHandle(request, response, new Object(), null);
        assertEquals("noindex, nofollow", response.getHeader("X-Robots-Tag"));
    }

    @Test
    void postHandleLeavesNormalSiteUnstamped() {
        when(sandboxSiteRegistry.isSandboxSite(SITE_ID)).thenReturn(false);
        interceptor.postHandle(request, response, new Object(), null);
        assertNull(response.getHeader("X-Robots-Tag"));
    }

    @Test
    void postHandleWithoutResolvedSiteNeverStamps() {
        // 未解析出站点（如异常路径）：走 templateUrl 兜底分支，绝不盖章
        Contexts.clearCurrentSite();
        interceptor.postHandle(request, response, new Object(), null);
        assertNull(response.getHeader("X-Robots-Tag"));
    }
}

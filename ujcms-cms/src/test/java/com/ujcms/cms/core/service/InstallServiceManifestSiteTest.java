package com.ujcms.cms.core.service;

import com.ujcms.cms.core.composition.ThemeSeedOrchestrator;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.ConfigMapper;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.marketplace.MarketplaceInstallService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.support.InstallTokenFile;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InstallService#ensureManifestSite} / {@link InstallService#configureSite1(Props.Install.SiteEntry)}
 * 单元测试（install-flow D44/D45：清单 langOf 语言变体链接、subDir 寻址、续装收敛）。
 * 语言组完整性规则本体（链/重复语言）在 {@code SiteService.validateLangGroup} 及其测试；
 * 此处覆盖清单侧的解析、装配、fail-loud 与收敛路径的接线。
 */
class InstallServiceManifestSiteTest {
    private SiteService siteService;
    private ConfigService configService;
    private InstallSitePort port;
    private ArticleI18nService articleI18nService;
    private InstallService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        siteService = mock(SiteService.class);
        configService = mock(ConfigService.class);
        port = mock(InstallSitePort.class);
        articleI18nService = mock(ArticleI18nService.class);
        ObjectProvider<InstallSitePort> portProvider = mock(ObjectProvider.class);
        when(portProvider.getIfAvailable()).thenReturn(port);
        ObjectProvider<com.ujcms.cms.core.support.FirstInitSeeder> seederProvider = mock(ObjectProvider.class);
        Config config = new Config();
        config.setMultiDomain(false);
        when(configService.getUnique()).thenReturn(config);
        service = new InstallService(mock(ConfigMapper.class), configService, siteService,
                mock(UserService.class), mock(RoleService.class), mock(UserMapper.class),
                mock(UserExtMapper.class), mock(UserRoleMapper.class), mock(PasswordEncoder.class),
                mock(ThemeInstallService.class), mock(ThemePackageService.class),
                mock(MarketplaceInstallService.class), mock(ThemeSeedOrchestrator.class), articleI18nService,
                portProvider, seederProvider, new FreeMarkerProperties(), mock(InstallTokenFile.class));
    }

    /**
     * 站点名查询打桩：{@code selectList(EQ_name)} 按名返回
     */
    private void stubSitesByName(Map<String, Site> byName) {
        when(siteService.selectList(any(SiteArgs.class))).thenAnswer(invocation -> {
            SiteArgs args = invocation.getArgument(0);
            Object name = args.getQueryMap().get("EQ_name");
            Site hit = byName.get(String.valueOf(name));
            return hit != null ? List.of(hit) : List.of();
        });
    }

    private static Site site(long id, String name, String lang, Long langParentId) {
        Site site = new Site();
        site.setId(id);
        site.setName(name);
        site.setProtocol("https");
        site.setDomain("www.example.com");
        site.setLang(lang);
        site.setLangParentId(langParentId);
        return site;
    }

    private static Props.Install.SiteEntry entry(String name, String lang, String langOf, String subDir) {
        Props.Install.SiteEntry entry = new Props.Install.SiteEntry();
        entry.setName(name);
        entry.setLang(lang);
        entry.setLangOf(langOf);
        entry.setSubDir(subDir);
        return entry;
    }

    @Test
    void variantEntryResolvesLangOfAndAssemblesBean() {
        Site master = site(1L, "主站", "zh-CN", null);
        stubSitesByName(Map.of("主站", master));
        when(port.create(any(Site.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Site.class).setId(20L);
            return 20L;
        });

        InstallService.SiteCreateResult result = service.ensureManifestSite(entry("English", "en", "主站", "en"));

        ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
        verify(port).create(captor.capture());
        Site bean = captor.getValue();
        assertEquals("en", bean.getLang());
        assertEquals(1L, bean.getLangParentId());
        assertEquals("en", bean.getSubDir());
        // 变体缺省继承主站点协议/域名（同域名子目录拓扑零配置即得）
        assertEquals("https", bean.getProtocol());
        assertEquals("www.example.com", bean.getDomain());
        assertEquals(1L, bean.getCopyFromId());
        assertTrue(bean.getCopyData().isEmpty());
        assertFalse(result.existed());
        assertEquals("variant of '主站' [en]", result.linkage());
    }

    @Test
    void langOfPointingAtVariantNormalizesToGroupMaster() {
        Site master = site(1L, "主站", "zh-CN", null);
        Site variant = site(5L, "日本語", "ja", 1L);
        stubSitesByName(Map.of("主站", master, "日本語", variant));
        when(siteService.select(1L)).thenReturn(master);
        when(port.create(any(Site.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Site.class).setId(21L);
            return 21L;
        });

        InstallService.SiteCreateResult result = service.ensureManifestSite(entry("English", "en", "日本語", "en"));

        ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
        verify(port).create(captor.capture());
        // 组仅一层：挂到组主站点，绝不挂到变体（清单永远造不出链）
        assertEquals(1L, captor.getValue().getLangParentId());
        assertEquals("variant of '主站' [en]", result.linkage());
    }

    @Test
    void unknownOrForwardLangOfFailsLoud() {
        stubSitesByName(Map.of());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.ensureManifestSite(entry("English", "en", "Later Site", "en")));
        assertTrue(e.getMessage().contains("forward references fail"));
        verify(port, never()).create(any());
    }

    @Test
    void langWithoutLangOfFailsLoud() {
        assertThrows(IllegalStateException.class,
                () -> service.ensureManifestSite(entry("English", "en", null, null)));
    }

    @Test
    void langOfWithoutLangFailsLoud() {
        assertThrows(IllegalStateException.class,
                () -> service.ensureManifestSite(entry("English", null, "主站", null)));
    }

    @Test
    void invalidLangFormatFailsLoud() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.ensureManifestSite(entry("English", "english_US", "主站", null)));
        assertTrue(e.getMessage().contains("BCP-47"));
    }

    @Test
    void reservedSubDirFailsLoud() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.ensureManifestSite(entry("English", "en", "主站", "api")));
        assertTrue(e.getMessage().contains("subDir"));
    }

    /**
     * 普通条目（无 lang/langOf）也可用 subDir——单域名模式下同域名第二站点的唯一寻址方式
     */
    @Test
    void plainEntryMayUseSubDir() {
        stubSitesByName(Map.of());
        when(port.create(any(Site.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Site.class).setId(22L);
            return 22L;
        });

        InstallService.SiteCreateResult result = service.ensureManifestSite(entry("分站", null, null, "branch"));

        ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
        verify(port).create(captor.capture());
        assertEquals("branch", captor.getValue().getSubDir());
        assertNull(captor.getValue().getLangParentId());
        assertNull(result.linkage());
    }

    @Test
    void existingSiteConvergesDeclaredLangFields() {
        Site master = site(1L, "主站", "zh-CN", null);
        Site existing = site(30L, "English", "zh-CN", null);
        existing.setSubDir(null);
        stubSitesByName(Map.of("主站", master, "English", existing));

        InstallService.SiteCreateResult result = service.ensureManifestSite(entry("English", "en", "主站", "en"));

        assertTrue(result.existed());
        assertEquals(30L, result.siteId());
        assertEquals("variant of '主站' [en] (converged)", result.linkage());
        assertEquals("en", existing.getLang());
        assertEquals(1L, existing.getLangParentId());
        assertEquals("en", existing.getSubDir());
        verify(siteService).validateAddress(eq(existing), anyBoolean());
        verify(siteService).update(existing);
        verify(port, never()).create(any());
    }

    /**
     * 变体收敛的终态须与全新首装一致（不随启动历史漂移）：以普通条目建成的站点（域名为空）
     * 链入语言组时，协议/域名一并收敛到主站点继承值——否则 hreflang 绝对 URL 残缺
     */
    @Test
    void convergenceInheritsMasterAddressWhenEntryDomainBlank() {
        Site master = site(1L, "主站", "zh-CN", null);
        Site existing = site(30L, "English", "zh-CN", null);
        existing.setProtocol("http");
        existing.setDomain("");
        stubSitesByName(Map.of("主站", master, "English", existing));

        service.ensureManifestSite(entry("English", "en", "主站", "en"));

        assertEquals("https", existing.getProtocol());
        assertEquals("www.example.com", existing.getDomain());
        verify(siteService).update(existing);
    }

    @Test
    void existingSiteAlreadyLinkedIsZeroWrite() {
        Site master = site(1L, "主站", "zh-CN", null);
        Site existing = site(30L, "English", "en", 1L);
        existing.setSubDir("en");
        stubSitesByName(Map.of("主站", master, "English", existing));

        InstallService.SiteCreateResult result = service.ensureManifestSite(entry("English", "en", "主站", "en"));

        assertTrue(result.existed());
        assertEquals("variant of '主站' [en]", result.linkage());
        verify(siteService, never()).validateAddress(any(), anyBoolean());
        verify(siteService, never()).update(any(Site.class));
    }

    /**
     * 收敛路径的组校验失败（如组内语言重复）原样上抛——引擎记为该站点条目的失败报告行（D33）
     */
    @Test
    void convergenceValidationFailurePropagates() {
        Site master = site(1L, "主站", "zh-CN", null);
        Site existing = site(30L, "English", "zh-CN", null);
        stubSitesByName(Map.of("主站", master, "English", existing));
        doThrow(new Http400Exception("error.duplicateLang")).when(siteService)
                .validateAddress(any(Site.class), anyBoolean());

        assertThrows(Http400Exception.class,
                () -> service.ensureManifestSite(entry("English", "en", "主站", "en")));
        verify(siteService, never()).update(any(Site.class));
    }

    @Test
    void plainExistingEntryKeepsPureSkip() {
        Site existing = site(30L, "English", "zh-CN", null);
        stubSitesByName(Map.of("English", existing));

        InstallService.SiteCreateResult result = service.ensureManifestSite(entry("English", null, null, null));

        assertTrue(result.existed());
        assertNull(result.linkage());
        verify(siteService, never()).validateAddress(any(), anyBoolean());
        verify(siteService, never()).update(any(Site.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void variantWithoutT2PortFailsLoud() {
        stubSitesByName(Map.of("主站", site(1L, "主站", "zh-CN", null)));
        ObjectProvider<InstallSitePort> absent = mock(ObjectProvider.class);
        when(absent.getIfAvailable()).thenReturn(null);
        InstallService community = new InstallService(mock(ConfigMapper.class), configService, siteService,
                mock(UserService.class), mock(RoleService.class), mock(UserMapper.class),
                mock(UserExtMapper.class), mock(UserRoleMapper.class), mock(PasswordEncoder.class),
                mock(ThemeInstallService.class), mock(ThemePackageService.class),
                mock(MarketplaceInstallService.class), mock(ThemeSeedOrchestrator.class), articleI18nService,
                absent, mock(ObjectProvider.class), new FreeMarkerProperties(), mock(InstallTokenFile.class));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> community.ensureManifestSite(entry("English", "en", "主站", "en")));
        assertTrue(e.getMessage().contains("commercial T2"));
    }

    @Test
    void firstEntryWithLangOfFailsLoud() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.configureSite1(entry(null, "zh-CN", "别站", null)));
        assertTrue(e.getMessage().contains("langOf is not applicable"));
    }

    @Test
    void firstEntryAppliesLangAndSubDirToSite1() {
        Site site1 = site(1L, "主站", "zh-CN", null);
        when(siteService.select(1L)).thenReturn(site1);

        service.configureSite1(entry(null, "fr", null, "fr-root"));

        assertEquals("fr", site1.getLang());
        assertEquals("fr-root", site1.getSubDir());
        verify(siteService).validateAddress(eq(site1), anyBoolean());
        verify(siteService).update(site1);
    }

    @Test
    void firstEntryWithMatchingLangIsZeroWrite() {
        Site site1 = site(1L, "主站", "zh-CN", null);
        when(siteService.select(1L)).thenReturn(site1);

        service.configureSite1(entry(null, "zh-CN", null, null));

        verify(siteService, never()).update(any(Site.class));
    }

    /**
     * D50：变体条目与语言组内其余各站点逐一按种子来源键建链，统计求和；自身不与自身配对
     */
    @Test
    void linkLangVariantArticlesSumsOverGroupPeers() {
        Site master = site(1L, "主站", "zh-CN", null);
        Site english = site(30L, "English", "en", 1L);
        Site japanese = site(31L, "日本語", "ja", 1L);
        stubSitesByName(Map.of("English", english));
        when(siteService.listByLangGroup(english)).thenReturn(List.of(master, english, japanese));
        when(articleI18nService.linkSeedPeers(english, master))
                .thenReturn(new ArticleI18nService.SeedLinkResult(23, 0, 1, 0));
        when(articleI18nService.linkSeedPeers(english, japanese))
                .thenReturn(new ArticleI18nService.SeedLinkResult(2, 5, 0, 1));

        ArticleI18nService.SeedLinkResult result =
                service.linkLangVariantArticles(entry("English", "en", "主站", "en"));

        assertEquals(new ArticleI18nService.SeedLinkResult(25, 5, 1, 1), result);
        verify(articleI18nService, never()).linkSeedPeers(english, english);
    }

    /**
     * 站点不存在（其建站步骤已失败）：返回 null，由引擎记为跳过，不再追加一条失败
     */
    @Test
    void linkLangVariantArticlesReturnsNullWhenSiteAbsent() {
        stubSitesByName(Map.of());

        assertNull(service.linkLangVariantArticles(entry("English", "en", "主站", "en")));
        verify(articleI18nService, never()).linkSeedPeers(any(), any());
    }
}

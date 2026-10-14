package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.UrlConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站点子目录路由：{@link SiteRoutingHandlerMapping} 生成的孪生模式、{@link SiteArgumentResolver} 的站点解析、
 * {@link FrontendRouteWords} 的保留字推导。固定住"字面路径段优先于子目录"这一匹配语义。
 */
class SiteRoutingHandlerMappingTest {
    private SiteResolver siteResolver;
    private FrontendRouteWords routeWords;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Site root = new Site();
        root.setName("root");
        Site en = new Site();
        en.setName("en");
        siteResolver = mock(SiteResolver.class);
        when(siteResolver.resolve(any(), (String) isNull())).thenReturn(root);
        when(siteResolver.resolve(any(), eq("en"))).thenReturn(en);

        Config config = mock(Config.class);
        when(config.getChannelUrl()).thenReturn("/news");
        when(config.getArticleUrl()).thenReturn(UrlConstants.ARTICLE);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);
        routeWords = new FrontendRouteWords(configService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PageController(), new SettingsController(), new LegacyController(),
                        new GlobalFlowController(), new PlainController())
                .setCustomHandlerMapping(() -> new SiteRoutingHandlerMapping(routeWords))
                .setCustomArgumentResolvers(new SiteArgumentResolver(siteResolver))
                .build();
    }

    @Test
    void homepageServesRootAndSubDirSite() throws Exception {
        mockMvc.perform(get("/")).andExpect(content().string("home:root"));
        mockMvc.perform(get("/en")).andExpect(content().string("home:en"));
    }

    @Test
    void routeServesRootAndSubDirSite() throws Exception {
        mockMvc.perform(get("/page/7")).andExpect(content().string("page:root:7"));
        mockMvc.perform(get("/en/page/7")).andExpect(content().string("page:en:7"));
    }

    @Test
    void literalSegmentBeatsSubDir() throws Exception {
        mockMvc.perform(get("/search")).andExpect(content().string("search:root"));
        mockMvc.perform(get("/en/search")).andExpect(content().string("search:en"));
        verify(siteResolver, never()).resolve(any(), eq("search"));
    }

    @Test
    void classLevelMappingGetsTwin() throws Exception {
        mockMvc.perform(get("/settings")).andExpect(content().string("settings:root"));
        mockMvc.perform(get("/en/settings")).andExpect(content().string("settings:en"));
        mockMvc.perform(get("/en/settings/profile")).andExpect(content().string("profile:en"));
    }

    @Test
    void handWrittenTwinIsNotDoubled() throws Exception {
        mockMvc.perform(get("/legacy")).andExpect(content().string("legacy:null"));
        mockMvc.perform(get("/en/legacy")).andExpect(content().string("legacy:en"));
    }

    @Test
    void subDirOptOutGetsNoTwinButStillResolvesSiteByDomain() throws Exception {
        mockMvc.perform(get("/oauth/register")).andExpect(content().string("register:root"));
        mockMvc.perform(get("/en/oauth/register")).andExpect(status().isNotFound());
    }

    @Test
    void plainControllerGetsNoTwin() throws Exception {
        mockMvc.perform(get("/plain")).andExpect(status().isOk());
        mockMvc.perform(get("/en/plain")).andExpect(status().isNotFound());
    }

    @Test
    void routeWordsAreDerivedFromRegisteredRoutesAndConfig() {
        Set<String> words = routeWords.routeWords();
        // 所有 Controller 的路由首段都计入：字面路径段优先于 /{subDir}，与 Controller 类型无关
        assertTrue(words.containsAll(Set.of("page", "search", "settings", "legacy", "plain")), words.toString());
        // 运行时可配置的栏目、文章 URL 前缀
        assertTrue(words.containsAll(Set.of("news", "article")), words.toString());
        // 首段为路径变量、或含子目录不允许的字符，无冲突可能
        assertFalse(words.contains("sitemap.xml"), words.toString());
        assertFalse(words.stream().anyMatch(word -> word.isEmpty() || word.contains("{")), words.toString());
    }

    @FrontendController
    @ResponseBody
    static class PageController {
        @GetMapping("/")
        String home(Site site) {
            return "home:" + site.getName();
        }

        @GetMapping("/page/{id}")
        String page(Site site, @PathVariable Long id) {
            return "page:" + site.getName() + ":" + id;
        }

        @GetMapping("/search")
        String search(Site site) {
            return "search:" + site.getName();
        }

        @GetMapping("/sitemap.xml")
        String sitemap(Site site) {
            return "sitemap:" + site.getName();
        }
    }

    @FrontendController
    @RequestMapping("/settings")
    @ResponseBody
    static class SettingsController {
        @GetMapping
        String settings(Site site) {
            return "settings:" + site.getName();
        }

        @GetMapping("/profile")
        String profile(Site site) {
            return "profile:" + site.getName();
        }
    }

    /**
     * 迁移前的手写孪生模式仍可用，不会被叠加成 {@code /{subDir}/{subDir}/legacy}
     */
    @FrontendController
    @ResponseBody
    static class LegacyController {
        @GetMapping({"/legacy", UrlConstants.SUB_DIR + "/legacy"})
        String legacy(@PathVariable(required = false) String subDir) {
            return "legacy:" + subDir;
        }
    }

    /**
     * 全局流程（第三方登录）：不生成孪生路由
     */
    @FrontendController(subDir = false)
    @ResponseBody
    static class GlobalFlowController {
        @GetMapping("/oauth/register")
        String register(Site site) {
            return "register:" + site.getName();
        }
    }

    @Controller
    @ResponseBody
    static class PlainController {
        @GetMapping("/plain")
        String plain() {
            return "ok";
        }
    }
}

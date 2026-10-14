package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.composition.RegionTemplateSupport;
import com.ujcms.cms.core.composition.ResolvedRegion;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code <@region>} 指令金样测试（§17 golden snapshot / component-settings §4.2 契约）：行→列→面板的
 * Tailwind 栅格、按组件分派 {@code snippets/panels/<Shape>.html} 片段（片段以 {@code items}/{@code settings}/
 * {@code panel} 取得内容），以及空区域 / 未知组件 / 未知行布局 / 禁用面板 / 坏片段的优雅降级。
 */
class RegionDirectiveTest {

    private final RegionTemplateSupport support = mock(RegionTemplateSupport.class);

    /** 渲染：注册 region 指令 + 页面模板（位于 {@code templates/}）+ 面板片段（{@code ../snippets/panels/*.html}）。 */
    private String render(String template) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setSharedVariable("region", new RegionDirective(support));
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("templates/index", template);
        loader.putTemplate("snippets/panels/HeroCarousel.html",
                "<#list items as item><a class=\"hero\" data-pid=\"${panel.id}\" href=\"${item.link.url}\">${item.headline}</a></#list>");
        loader.putTemplate("snippets/panels/ArticleList.html",
                "<#if settings.title??><h2>${settings.title}</h2></#if>"
                        + "<#list items as item><div class=\"article\">${item.headline}</div></#list>");
        loader.putTemplate("snippets/panels/BlockList.html",
                "<#list items as item><div class=\"block\">${item.title}</div></#list>");
        loader.putTemplate("snippets/panels/Broken.html",
                "${undefinedVariable}");
        cfg.setTemplateLoader(loader);
        Template fm = cfg.getTemplate("templates/index");
        StringWriter out = new StringWriter();
        Map<String, Object> model = new HashMap<>();
        model.put("site", new Site());
        model.put("templateName", "index");
        fm.process(model, out);
        return out.toString();
    }

    @SafeVarargs
    private static ResolvedRegion.Panel panel(String id, String component, boolean enabled,
                                              Map<String, Object>... items) {
        return new ResolvedRegion.Panel(id, component, "1.0.0", Map.of(), List.of(items), enabled, "all");
    }

    @SafeVarargs
    private static ResolvedRegion.Panel panelWithSettings(String id, String component,
                                                          Map<String, Object> settings,
                                                          Map<String, Object>... items) {
        return new ResolvedRegion.Panel(id, component, "1.0.0", settings, List.of(items), true, "all");
    }

    private static ResolvedRegion.Row row(String id, String layout, ResolvedRegion.Column... columns) {
        return new ResolvedRegion.Row(id, layout, List.of(columns));
    }

    private static ResolvedRegion region(ResolvedRegion.Row... rows) {
        return new ResolvedRegion("body", PageRegion.SCOPE_DEFAULT, false,
                new ResolvedRegion.Layout(List.of(rows)));
    }

    @Test
    void goldenRender() throws Exception {
        Site site = new Site();
        ResolvedRegion region = region(
                row("r1", "full", new ResolvedRegion.Column(List.of(
                        panel("p1", "HeroCarousel", true,
                                Map.of("headline", "焦点甲", "link", Map.of("url", "/a")))))),
                row("r2", "twoThirdsLeft",
                        new ResolvedRegion.Column(List.of(panel("p2", "ArticleList", true,
                                Map.of("headline", "文章一"), Map.of("headline", "文章二")))),
                        new ResolvedRegion.Column(List.of(panel("p3", "BlockList", true,
                                Map.of("title", "区块一"))))));
        when(support.resolve(eq(site), eq("index"), isNull(), eq("body"), isNull(), isNull(), isNull())).thenReturn(region);
        // golden render uses the default site (model rebuilds a Site); match by any() then assert markup
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region);

        String out = render("<@region name='body'/>");

        assertThat(out).isEqualTo(
                "<div class=\"space-y-8\" data-region=\"body\">"
                        + "<div class=\"grid gap-8 grid-cols-1\" data-row=\"r1\" data-row-layout=\"full\">"
                        + "<div data-col=\"0\"><div class=\"space-y-8\">"
                        + "<div data-panel=\"HeroCarousel\" data-panel-id=\"p1\">"
                        + "<a class=\"hero\" data-pid=\"p1\" href=\"/a\">焦点甲</a></div></div></div></div>"
                        + "<div class=\"grid gap-8 grid-cols-1 md:grid-cols-3\" data-row=\"r2\" data-row-layout=\"twoThirdsLeft\">"
                        + "<div class=\"md:col-span-2\" data-col=\"0\"><div class=\"space-y-8\">"
                        + "<div data-panel=\"ArticleList\" data-panel-id=\"p2\">"
                        + "<div class=\"article\">文章一</div><div class=\"article\">文章二</div></div></div></div>"
                        + "<div class=\"md:col-span-1\" data-col=\"1\"><div class=\"space-y-8\">"
                        + "<div data-panel=\"BlockList\" data-panel-id=\"p3\">"
                        + "<div class=\"block\">区块一</div></div></div></div></div></div>");
    }

    @Test
    void quartersAndSidebarLayoutsRenderTailwindGrid() throws Exception {
        // 目录扩展布局（quarters / sidebarLeft）：Tailwind 栅格与 React rowLayouts.ts 逐键一致
        // （quarters→md:grid-cols-4 等宽；sidebarLeft→md:col-span-1 + md:col-span-3）
        ResolvedRegion region = region(
                row("r1", "quarters",
                        new ResolvedRegion.Column(List.of(panel("p1", "BlockList", true, Map.of("title", "一")))),
                        new ResolvedRegion.Column(List.of(panel("p2", "BlockList", true, Map.of("title", "二")))),
                        new ResolvedRegion.Column(List.of(panel("p3", "BlockList", true, Map.of("title", "三")))),
                        new ResolvedRegion.Column(List.of(panel("p4", "BlockList", true, Map.of("title", "四"))))),
                row("r2", "sidebarLeft",
                        new ResolvedRegion.Column(List.of(panel("p5", "BlockList", true, Map.of("title", "栏")))),
                        new ResolvedRegion.Column(List.of(panel("p6", "ArticleList", true, Map.of("headline", "文"))))));
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region);

        String out = render("<@region name='body'/>");

        assertThat(out).contains("data-row-layout=\"quarters\"").contains("data-row-layout=\"sidebarLeft\"");
        // quarters：等宽四列——行容器 md:grid-cols-4，各列无 col-span class
        String r1 = out.split("data-row=\"r1\"")[0];
        assertThat(r1).contains("grid gap-8 grid-cols-1 md:grid-cols-4");
        assertThat(out.split("data-row=\"r1\"")[1].split("data-row=\"r2\"")[0])
                .containsOnlyOnce("data-col=\"3\"")
                .doesNotContain("col-span");
        // sidebarLeft：md:col-span-1 + md:col-span-3
        String r2 = out.split("data-row=\"r2\"")[1];
        assertThat(r2).contains("<div class=\"md:col-span-1\" data-col=\"0\">")
                .contains("<div class=\"md:col-span-3\" data-col=\"1\">");
    }

    @Test
    void settingsAreVisibleToPanelSnippet() throws Exception {
        // component-settings §4.2：契约设置绑定为片段的 settings 变量——标题等装饰由组件自渲染
        // （框架级标题行已随 sectionData 退役）
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(
                        panelWithSettings("p1", "ArticleList", Map.of("title", "新闻动态"),
                                Map.of("headline", "文章一"))))),
                row("r2", "full", new ResolvedRegion.Column(List.of(
                        panel("p2", "ArticleList", true, Map.of("headline", "文章二")))))));
        String out = render("<@region name='body'/>");
        assertThat(out).contains("<h2>新闻动态</h2>");
        // 未设置标题的面板不输出（渲染器无框架级标题行）
        assertThat(out.split("data-row=\"r2\"")[1]).doesNotContain("<h2>").doesNotContain("data-panel-heading");
    }

    @Test
    void brokenSnippetRendersPlaceholderComment() throws Exception {
        // §9.3 渲染期永不破坏页面：片段渲染异常（未定义变量等）→ 占位注释，页面其余部分照常输出
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(
                        panel("p1", "Broken", true, Map.of("headline", "甲")),
                        panel("p2", "ArticleList", true, Map.of("headline", "文章一")))))));
        String out = render("<@region name='body'/>");
        assertThat(out).contains("<!-- panel snippet failed: Broken -->")
                .contains("<div class=\"article\">文章一</div>");
    }

    @Test
    void emptyRegionRendersBareWrapper() throws Exception {
        // 空区域输出区域壳（可视化搭建器的投放目标，visual-builder P0），不再是空串
        when(support.resolve(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ResolvedRegion.empty("body"));
        assertThat(render("<@region name='body'/>")).isEqualTo("<div class=\"space-y-8\" data-region=\"body\"></div>");
    }

    @Test
    void unknownComponentRendersPlaceholder() throws Exception {
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(panel("p9", "Nope", true))))));
        String out = render("<@region name='body'/>");
        assertThat(out).contains("data-panel-unknown=\"Nope\"").contains("未知面板类型：Nope")
                .doesNotContain("data-panel=\"Nope\"");
    }

    @Test
    void unknownRowLayoutFallsBackToFull() throws Exception {
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("rx", "bogus", new ResolvedRegion.Column(List.of(
                        panel("p1", "HeroCarousel", true, Map.of("headline", "甲", "link", Map.of("url", "/a"))))))));
        String out = render("<@region name='body'/>");
        assertThat(out).contains("data-row-layout=\"full\"").contains("grid gap-8 grid-cols-1");
    }

    @Test
    void disabledPanelIsSkippedInProduction() throws Exception {
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(panel("p1", "HeroCarousel", false,
                        Map.of("headline", "甲", "link", Map.of("url", "/a"))))))));
        String out = render("<@region name='body'/>");
        // 列存在但无面板（禁用被跳过，且不输出占位桩）
        assertThat(out).contains("<div data-col=\"0\"><div class=\"space-y-8\"></div></div>")
                .doesNotContain("data-panel=\"HeroCarousel\"").doesNotContain("data-panel-disabled");
    }

    @Test
    void disabledPanelEmitsStubInSlotPreview() throws Exception {
        // 插槽预览下禁用面板输出 data-panel-disabled 占位桩（visual-builder P0），但不渲染片段内容
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(panel("p1", "HeroCarousel", false,
                        Map.of("headline", "甲", "link", Map.of("url", "/a"))))))));
        Contexts.setSlotPreview(true);
        try {
            String out = render("<@region name='body'/>");
            assertThat(out).contains("<div data-panel-disabled=\"p1\" data-panel=\"HeroCarousel\"></div>")
                    .doesNotContain("class=\"hero\"");
        } finally {
            Contexts.clearSlotPreview();
        }
    }

    private static ResolvedRegion.Panel desktopOnlyPanel() {
        return new ResolvedRegion.Panel("p1", "HeroCarousel", "1.0.0", Map.of(),
                List.of(Map.of("headline", "甲", "link", Map.of("url", "/a"))), true, "desktop");
    }

    @Test
    void deviceHiddenPanelIsSkippedForNonMatchingUa() throws Exception {
        // 按设备显示（design-review P2-5）：模板轨生产按 UA（Contexts.isMobile）服务端跳过——
        // visibility=desktop 的面板移动端整体不输出（无壳无桩），桌面端照常渲染并带显示设备标记
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(desktopOnlyPanel())))));
        Contexts.setMobile(true);
        try {
            String out = render("<@region name='body'/>");
            assertThat(out).contains("<div data-col=\"0\"><div class=\"space-y-8\"></div></div>")
                    .doesNotContain("data-panel=").doesNotContain("data-panel-visibility");
        } finally {
            Contexts.clearMobile();
        }
        String desktop = render("<@region name='body'/>");
        assertThat(desktop)
                .contains("<div data-panel=\"HeroCarousel\" data-panel-id=\"p1\" data-panel-visibility=\"desktop\">")
                .contains("class=\"hero\"");
    }

    @Test
    void deviceHiddenPanelRendersInSlotPreviewRegardlessOfUa() throws Exception {
        // 预览一律渲染（构建器画布按视口预设经桥的编辑态 CSS 隐藏），显示设备以标记透出
        when(support.resolve(any(), any(), any(), any(), any(), any(), any())).thenReturn(region(
                row("r1", "full", new ResolvedRegion.Column(List.of(desktopOnlyPanel())))));
        Contexts.setSlotPreview(true);
        Contexts.setMobile(true);
        try {
            String out = render("<@region name='body'/>");
            assertThat(out).contains("data-panel-visibility=\"desktop\"").contains("class=\"hero\"");
        } finally {
            Contexts.clearSlotPreview();
            Contexts.clearMobile();
        }
    }
}

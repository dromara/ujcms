package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.web.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 导出值改写器测试（theme-seed-export §5.1/§6）：channelRef/articleRef 反向令牌化
 * （Long 与字符串编码ID两种存储形）、media/link/html 的资源改写、list/object 递归、
 * 不可种子化引用（voteRef/blockRef）原样保留 + 诊断、模型 customs 的类型分派与 fail-loud。
 */
class SeedExportValueRewriterTest {
    private static final Long SITE = 1L;
    private static final String THEME = "demo";

    @TempDir
    Path uploads;

    private SeedExportValueRewriter rewriter;
    private List<ExportPlan.RefIssue> issues;
    private SeedAssetExtractor.Session session;

    @BeforeEach
    void setUp() throws IOException {
        ConfigService configService = mock(ConfigService.class);
        Config config = new Config();
        config.setUploadStorage(new Config.Storage(0, "file:" + uploads, "/uploads"));
        config.setTemplateStorage(new Config.Storage(0, "file:" + uploads + "/tpl", "/themes"));
        when(configService.getUnique()).thenReturn(config);
        session = new SeedAssetExtractor(configService, new PathResolver(null), mock(AttachmentService.class))
                .open(SITE, THEME);
        Files.createDirectories(uploads.resolve("theme-seed/1/demo/img"));
        Files.writeString(uploads.resolve("theme-seed/1/demo/img/a.svg"), "<svg/>");

        issues = new ArrayList<>();
        Map<Long, String> channelAlias = Map.of(42L, "news");
        Map<Long, String> articleOrigin = Map.of(77L, "a-welcome");
        Map<Long, String> blockAlias = Map.of(40L, "focus");
        Map<Long, String> voteOrigin = Map.of(90L, "v-site-source");
        rewriter = new SeedExportValueRewriter(session, "/themes/1/demo", channelAlias, articleOrigin,
                blockAlias, voteOrigin, Set.of(42L), Set.of(40L), issues);
    }

    private static FieldDef def(Map<String, Object> raw) {
        return new FieldDef(raw);
    }

    @Test
    void refsTokenizeInBothStoredForms() {
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        defs.put("channel", def(Map.of("type", "channelRef")));
        defs.put("article", def(Map.of("type", "articleRef")));
        // 存储形：savePanel 归一化后的 Long，或跨轨道契约的字符串编码ID（§10.1）
        Map<String, Object> settings = Map.of("channel", 42L, "article", "77");
        Map<String, Object> out = rewriter.rewriteSettings(settings, defs, "p");
        assertEquals("{{channel:news}}", out.get("channel"));
        assertEquals("{{article:a-welcome}}", out.get("article"));
        assertTrue(issues.isEmpty());
    }

    @Test
    void unresolvableRefsKeptVerbatimWithIssue() {
        Map<String, FieldDef> defs = Map.of(
                "channel", def(Map.of("type", "channelRef")),
                "article", def(Map.of("type", "articleRef")),
                "vote", def(Map.of("type", "voteRef")));
        Map<String, Object> out = rewriter.rewriteSettings(
                Map.of("channel", 999L, "article", 888L, "vote", 5L), defs, "p");
        // 选择之外/不可种子化 → 原样保留（导入侧照常存ID），计划诊断提示放宽选择
        assertEquals(999L, out.get("channel"));
        assertEquals(888L, out.get("article"));
        assertEquals(5L, out.get("vote"));
        assertEquals(3, issues.size());
    }

    @Test
    void legacyLiteralTokensKeptWithErrorIssue() {
        // D18：引用字段值本身是种子令牌字面量（D9 之前导入的遗留）——原样保留 + error 级诊断
        Map<String, FieldDef> defs = Map.of(
                "channel", def(Map.of("type", "channelRef")),
                "article", def(Map.of("type", "articleRef")));
        Map<String, Object> out = rewriter.rewriteSettings(
                Map.of("channel", "{{channel:video}}", "article", "{{article:a-x}}"), defs, "p");
        assertEquals("{{channel:video}}", out.get("channel"));
        assertEquals("{{article:a-x}}", out.get("article"));
        assertEquals(2, issues.size());
        assertTrue(issues.stream().allMatch(i -> ExportPlan.RefIssue.SEVERITY_ERROR.equals(i.severity())));
        assertTrue(issues.get(0).detail().contains("修复种子令牌"));
    }

    @Test
    void aliasRefMapKeptWithRepairHint() {
        // D20：别名映射遗留形（渲染迟绑可用、跨站可散装）——warning 级 + 修复指引，原样保留
        Map<String, FieldDef> defs = Map.of("channel", def(Map.of("type", "channelRef")));
        Map<String, Object> out = rewriter.rewriteSettings(
                Map.of("channel", Map.of("alias", "video")), defs, "p");
        assertEquals(Map.of("alias", "video"), out.get("channel"));
        assertEquals(1, issues.size());
        assertEquals(ExportPlan.RefIssue.SEVERITY_WARNING, issues.get(0).severity());
        assertTrue(issues.get(0).detail().contains("修复种子令牌"));
    }

    @Test
    void channelRefOutsideExportSetKeepsRawId() {
        // D19：别名可解析但栏目不在导出集内 → 按原始ID导出 + warning 诊断（空白站装入退化为悬挂软引用）
        Map<String, FieldDef> defs = Map.of("channel", def(Map.of("type", "channelRef")));
        SeedExportValueRewriter scoped = new SeedExportValueRewriter(session, "/themes/1/demo",
                Map.of(42L, "news"), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), issues);
        Map<String, Object> out = scoped.rewriteSettings(Map.of("channel", 42L), defs, "p");
        assertEquals(42L, out.get("channel"));
        assertEquals(1, issues.size());
        assertEquals(ExportPlan.RefIssue.SEVERITY_WARNING, issues.get(0).severity());
        assertEquals("news", issues.get(0).ref());
    }

    @Test
    void compositionOnlyScopeTokenizesAllResolvableChannels() {
        // D19：仅构成导出（exportedChannelIds = null）——迟绑就是数据包的意义，可解析别名照常令牌化
        Map<String, FieldDef> defs = Map.of("channel", def(Map.of("type", "channelRef")));
        SeedExportValueRewriter compositionOnly = new SeedExportValueRewriter(session, "/themes/1/demo",
                Map.of(42L, "news"), Map.of(), Map.of(40L, "focus"), Map.of(), null, null, issues);
        Map<String, Object> out = compositionOnly.rewriteSettings(Map.of("channel", 42L), defs, "p");
        assertEquals("{{channel:news}}", out.get("channel"));
        assertTrue(issues.isEmpty());
    }

    @Test
    void mediaAndLinkUrlKeysRewritten() {
        Map<String, FieldDef> defs = Map.of(
                "cover", def(Map.of("type", "media")),
                "cta", def(Map.of("type", "link")));
        Map<String, Object> settings = Map.of(
                "cover", Map.of("url", "/uploads/theme-seed/1/demo/img/a.svg", "alt", "图"),
                "cta", Map.of("url", "/themes/1/demo/assets/page.css", "targetBlank", true));
        Map<String, Object> out = rewriter.rewriteSettings(settings, defs, "p");
        assertEquals("{{asset:img/a.svg}}", ((Map<?, ?>) out.get("cover")).get("url"));
        assertEquals("图", ((Map<?, ?>) out.get("cover")).get("alt"));
        assertEquals("{{theme}}/assets/page.css", ((Map<?, ?>) out.get("cta")).get("url"));
    }

    @Test
    void listAndObjectRecurse() {
        FieldDef listDef = def(Map.of("type", "list", "of", Map.of(
                "type", "object", "fields", Map.of(
                        "label", Map.of("type", "text"),
                        "target", Map.of("type", "channelRef")))));
        Map<String, Object> settings = Map.of("menu", List.of(
                Map.of("label", "新闻", "target", "42")));
        Map<String, Object> out = rewriter.rewriteSettings(settings, Map.of("menu", listDef), "p");
        Map<?, ?> item = (Map<?, ?>) ((List<?>) out.get("menu")).get(0);
        assertEquals("{{channel:news}}", item.get("target"));
        assertEquals("新闻", item.get("label"));
    }

    @Test
    void htmlAndPlainStringsGetThemePrefixReversal() {
        Map<String, FieldDef> defs = Map.of(
                "body", def(Map.of("type", "html")),
                "note", def(Map.of("type", "text")));
        Map<String, Object> settings = Map.of(
                "body", "<img src=\"/uploads/theme-seed/1/demo/img/a.svg\">",
                "note", "bg at /themes/1/demo/assets/bg.png");
        Map<String, Object> out = rewriter.rewriteSettings(settings, defs, "p");
        assertEquals("<img src=\"{{asset:img/a.svg}}\">", out.get("body"));
        // NONE 类型只做主题前缀反向（导入侧对全部字符串无条件展开 {{theme}}，反向须对称）
        assertEquals("bg at {{theme}}/assets/bg.png", out.get("note"));
    }

    @Test
    void customsFollowModelFieldRules() {
        Model model = new Model();
        model.setCustoms("""
                [
                  { "code": "cover", "type": "imageUpload" },
                  { "code": "body",  "type": "richEditor", "clob": true },
                  { "code": "note",  "type": "text" }
                ]
                """);
        Map<String, Object> customs = Map.of(
                "cover", "/uploads/theme-seed/1/demo/img/a.svg",
                "body", "<p><img src=\"/uploads/theme-seed/1/demo/img/a.svg\"></p>",
                "note", "纯文本");
        Map<String, Object> out = rewriter.rewriteCustoms(customs, model, "article:a-1.customs");
        assertEquals("{{asset:img/a.svg}}", out.get("cover"));
        assertTrue(((String) out.get("body")).contains("{{asset:img/a.svg}}"));
        assertEquals("纯文本", out.get("note"));
        assertTrue(issues.isEmpty());
    }

    @Test
    void unknownModelFieldTypeFailsLoud() {
        Model model = new Model();
        model.setCustoms("[{ \"code\": \"x\", \"type\": \"hologram\" }]");
        assertThrows(IllegalStateException.class,
                () -> rewriter.rewriteCustoms(Map.of("x", "v"), model, "w"));
    }

    @Test
    void missingModelSkipsRewriteWithIssue() {
        Map<String, Object> out = rewriter.rewriteCustoms(Map.of("cover", "/uploads/x.jpg"), null, "w");
        assertEquals("/uploads/x.jpg", out.get("cover"));
        assertFalse(issues.isEmpty());
    }
}

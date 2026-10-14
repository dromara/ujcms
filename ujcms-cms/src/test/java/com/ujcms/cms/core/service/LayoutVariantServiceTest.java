package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.TemplateService;
import com.ujcms.cms.core.composition.LayoutVariantCatalog;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.RegistrySnapshot;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 布局变体服务测试（template-track-variants 轨道拆分重置后）：
 * 模板轨道活动变体按有效模板反查主题声明（D2，先声明者胜出）；
 * 有效模板两级规则（显式 &gt; 默认，布局列不参与，D1）；孤立模板选择计数（D7，空集合整面不计）。
 */
class LayoutVariantServiceTest {
    private ManifestService manifestService;
    private ChannelMapper channelMapper;
    private ArticleMapper articleMapper;
    private TopicMapper topicMapper;
    private LayoutVariantService service;
    private Site site;

    @BeforeEach
    void setUp() {
        manifestService = mock(ManifestService.class);
        channelMapper = mock(ChannelMapper.class);
        articleMapper = mock(ArticleMapper.class);
        topicMapper = mock(TopicMapper.class);
        // 真实 TemplateService：无 Config 上下文的用例不触达它（Q2 回退先判 site.getConfig()）
        service = new LayoutVariantService(manifestService, new TemplateService(new DefaultResourceLoader()),
                channelMapper, articleMapper, topicMapper);
        site = mock(Site.class);
        when(site.getThemeKey()).thenReturn("1/default");
        when(site.getMobileThemeKey()).thenReturn("1/default");
    }

    private static LayoutVariantCatalog catalog(Object... valueEnabledPairs) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < valueEnabledPairs.length; i += 2) {
            items.add(Map.of("value", valueEnabledPairs[i], "enabled", valueEnabledPairs[i + 1]));
        }
        return LayoutVariantCatalog.parse(Map.of("channel", items));
    }

    private void snapshotWith(LayoutVariantCatalog theme) {
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        if (!theme.isEmpty()) {
            builder.addThemeLayoutVariants("1/default", theme, "{}");
        }
        when(manifestService.getSnapshot()).thenReturn(builder.build());
    }

    // ---- 目录列表（主题声明） ----

    @Test
    void themeCatalogListsBySurface() {
        snapshotWith(catalog("magazine", true));
        assertEquals(List.of("magazine"),
                service.listThemeBySurface(site, "channel").stream()
                        .map(LayoutVariantCatalog.Item::getValue).toList());
    }

    // ---- 模板轨道活动变体反查（D2） ----

    private static LayoutVariantCatalog themeCatalogWithTemplates() {
        return LayoutVariantCatalog.parse(Map.of("channel", List.of(
                Map.of("value", "default", "template", "channel"),
                Map.of("value", "grid", "template", "channel_grid"),
                Map.of("value", "list"),
                Map.of("value", "legacy", "enabled", false, "template", "channel_legacy"),
                Map.of("value", "grid_dup", "template", "channel_grid"))));
    }

    @Test
    void resolveThemeVariantByTemplateHitsEnabledBindingFirstDeclaredWins() {
        snapshotWith(themeCatalogWithTemplates());
        // 基础模板绑定 default 变体：空白/显式基础模板的实体共同解析到它
        LayoutVariantCatalog.Item base = service.resolveThemeVariantByTemplate(site, "channel", "channel");
        assertEquals("default", base != null ? base.getValue() : null);
        // 绑定命中携带变体元数据
        LayoutVariantCatalog.Item grid = service.resolveThemeVariantByTemplate(site, "channel", "channel_grid");
        assertEquals("grid", grid != null ? grid.getValue() : null);
        // 禁用绑定不命中；未绑定模板/空白模板无变体上下文
        assertNull(service.resolveThemeVariantByTemplate(site, "channel", "channel_legacy"));
        assertNull(service.resolveThemeVariantByTemplate(site, "channel", "channel_custom"));
        assertNull(service.resolveThemeVariantByTemplate(site, "channel", " "));
    }

    // ---- 有效模板 helper（两级规则 + 反查，控制器与解析器共用） ----

    private void snapshotWithSurfaceCatalogs() {
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        builder.addThemeLayoutVariants("1/default", LayoutVariantCatalog.parse(Map.of(
                "channel", List.of(
                        Map.of("value", "default", "template", "channel"),
                        Map.of("value", "channel_video", "template", "channel_video")),
                "article", List.of(Map.of("value", "article_product", "template", "article_product")),
                "topic", List.of(Map.of("value", "topic_special", "template", "topic_special")))), "{}");
        when(manifestService.getSnapshot()).thenReturn(builder.build());
    }

    private static com.ujcms.cms.core.domain.Channel channelOf(String template) {
        com.ujcms.cms.core.domain.Channel channel = mock(com.ujcms.cms.core.domain.Channel.class);
        when(channel.getChannelTemplate()).thenReturn(template);
        when(channel.getTemplateName()).thenReturn(
                template != null && !template.isBlank() ? template : "channel");
        return channel;
    }

    @Test
    void effectiveChannelTemplateExplicitBeatsDefault() {
        snapshotWithSurfaceCatalogs();
        // 默认：无显式 → channel，反查命中 default 变体（默认变体不再特殊，就是绑定基础模板的变体）
        LayoutVariantService.EffectiveTemplate byDefault = service.effectiveChannelTemplate(site, channelOf(null));
        assertEquals("channel", byDefault.template());
        assertEquals("default", byDefault.variant() != null ? byDefault.variant().getValue() : null);
        // 显式基础模板与空白同解析（操作员表单总是写显式 channel 的存量数据由此归位）
        LayoutVariantService.EffectiveTemplate byExplicitBase =
                service.effectiveChannelTemplate(site, channelOf("channel"));
        assertEquals("default", byExplicitBase.variant() != null ? byExplicitBase.variant().getValue() : null);
        // 显式变体绑定模板 → 该变体
        LayoutVariantService.EffectiveTemplate byExplicit =
                service.effectiveChannelTemplate(site, channelOf("channel_video"));
        assertEquals("channel_video", byExplicit.template());
        assertEquals("channel_video", byExplicit.variant() != null ? byExplicit.variant().getValue() : null);
        // 未绑定变体的显式模板：有模板无变体上下文
        LayoutVariantService.EffectiveTemplate unbound =
                service.effectiveChannelTemplate(site, channelOf("channel_custom"));
        assertEquals("channel_custom", unbound.template());
        assertNull(unbound.variant());
    }

    @Test
    void effectiveArticleTemplateCascadesThroughChannelExplicitOnly() {
        snapshotWithSurfaceCatalogs();
        // 栏目级显式 article_template_ → 该模板 + 反查变体
        com.ujcms.cms.core.domain.Channel explicitChannel = mock(com.ujcms.cms.core.domain.Channel.class);
        when(explicitChannel.getArticleTemplate()).thenReturn("article_product");
        com.ujcms.cms.core.domain.Article inheriting = mock(com.ujcms.cms.core.domain.Article.class);
        when(inheriting.getArticleTemplate()).thenReturn(null);
        LayoutVariantService.EffectiveTemplate byChannelExplicit =
                service.effectiveArticleTemplate(site, inheriting, explicitChannel);
        assertEquals("article_product", byChannelExplicit.template());
        assertEquals("article_product",
                byChannelExplicit.variant() != null ? byChannelExplicit.variant().getValue() : null);
        // 文章级显式压过栏目级
        com.ujcms.cms.core.domain.Article explicitArticle = mock(com.ujcms.cms.core.domain.Article.class);
        when(explicitArticle.getArticleTemplate()).thenReturn("article_wenku");
        LayoutVariantService.EffectiveTemplate byArticleExplicit =
                service.effectiveArticleTemplate(site, explicitArticle, explicitChannel);
        assertEquals("article_wenku", byArticleExplicit.template());
        assertNull(byArticleExplicit.variant());
        // 皆无显式 → 默认 article
        com.ujcms.cms.core.domain.Channel plain = mock(com.ujcms.cms.core.domain.Channel.class);
        when(plain.getArticleTemplate()).thenReturn(null);
        LayoutVariantService.EffectiveTemplate byDefault =
                service.effectiveArticleTemplate(site, inheriting, plain);
        assertEquals("article", byDefault.template());
        assertNull(byDefault.variant());
        // 无栏目实例：按实体自身级联（getTemplateName）解析
        com.ujcms.cms.core.domain.Article standalone = mock(com.ujcms.cms.core.domain.Article.class);
        when(standalone.getTemplateName()).thenReturn("article");
        LayoutVariantService.EffectiveTemplate withoutChannel =
                service.effectiveArticleTemplate(site, standalone, null);
        assertEquals("article", withoutChannel.template());
    }

    @Test
    void effectiveTopicTemplateMatrix() {
        snapshotWithSurfaceCatalogs();
        com.ujcms.cms.core.domain.Topic byDefault = mock(com.ujcms.cms.core.domain.Topic.class);
        when(byDefault.getTemplateName()).thenReturn("topic");
        // 模板与变体都按 getTemplateName 解析
        LayoutVariantService.EffectiveTemplate resolved = service.effectiveTopicTemplate(site, byDefault);
        assertEquals("topic", resolved.template());
        assertNull(resolved.variant());
        com.ujcms.cms.core.domain.Topic byExplicit = mock(com.ujcms.cms.core.domain.Topic.class);
        when(byExplicit.getTemplateName()).thenReturn("topic_special");
        LayoutVariantService.EffectiveTemplate explicit = service.effectiveTopicTemplate(site, byExplicit);
        assertEquals("topic_special", explicit.template());
        assertEquals("topic_special", explicit.variant() != null ? explicit.variant().getValue() : null);
    }

    // ---- 孤立显式模板渲染回退（Q2） ----

    /**
     * 站点 Config 指向真实模板存储（file: 临时目录），主题 {@code templates/} 内含
     * {@code channel.html} 与 {@code channel_video.html}。
     */
    private void storageWithTemplates(Path themesRoot) throws IOException {
        Path templates = themesRoot.resolve("1/default/templates");
        Files.createDirectories(templates);
        Files.createFile(templates.resolve("channel.html"));
        Files.createFile(templates.resolve("channel_video.html"));
        stubStorage(themesRoot);
    }

    private void stubStorage(Path themesRoot) {
        Config config = mock(Config.class);
        when(config.getTemplateStorage()).thenReturn(new Config.Storage(0, "file:" + themesRoot, ""));
        when(site.getConfig()).thenReturn(config);
        when(site.getId()).thenReturn(1L);
    }

    @Test
    void orphanedExplicitTemplateFallsBackToDefaultAndVariantFollows(@TempDir Path themesRoot) throws IOException {
        snapshotWithSurfaceCatalogs();
        storageWithTemplates(themesRoot);
        // 显式选择在主题中无文件 → 回退默认模板，变体随回退后的模板反查（default 绑定基础模板）
        LayoutVariantService.EffectiveTemplate orphaned =
                service.effectiveChannelTemplate(site, channelOf("channel_gone"));
        assertEquals("channel", orphaned.template());
        assertEquals("default", orphaned.variant() != null ? orphaned.variant().getValue() : null);
        // 文章/专题同规（默认模板未绑定变体 → 回退后无变体上下文）
        com.ujcms.cms.core.domain.Channel orphanedArticleChannel = mock(com.ujcms.cms.core.domain.Channel.class);
        when(orphanedArticleChannel.getArticleTemplate()).thenReturn("article_gone");
        com.ujcms.cms.core.domain.Article inheriting = mock(com.ujcms.cms.core.domain.Article.class);
        assertEquals("article",
                service.effectiveArticleTemplate(site, inheriting, orphanedArticleChannel).template());
        com.ujcms.cms.core.domain.Topic orphanedTopic = mock(com.ujcms.cms.core.domain.Topic.class);
        when(orphanedTopic.getTemplateName()).thenReturn("topic_gone");
        assertEquals("topic", service.effectiveTopicTemplate(site, orphanedTopic).template());
    }

    @Test
    void existingExplicitTemplateIsTrusted(@TempDir Path themesRoot) throws IOException {
        snapshotWithSurfaceCatalogs();
        storageWithTemplates(themesRoot);
        LayoutVariantService.EffectiveTemplate existing =
                service.effectiveChannelTemplate(site, channelOf("channel_video"));
        assertEquals("channel_video", existing.template());
        assertEquals("channel_video", existing.variant() != null ? existing.variant().getValue() : null);
    }

    @Test
    void unverifiableStorageTrustsExplicitPick(@TempDir Path themesRoot) {
        snapshotWithSurfaceCatalogs();
        // templates/ 目录不存在（存储路径可解析但主题目录缺失）→ 无法校验，信任显式选择
        stubStorage(themesRoot.resolve("nowhere"));
        assertEquals("channel_gone",
                service.effectiveChannelTemplate(site, channelOf("channel_gone")).template());
        // 无 Config 上下文（如单测/后台线程装配不全）→ 同样信任
        when(site.getConfig()).thenReturn(null);
        assertEquals("channel_gone",
                service.effectiveChannelTemplate(site, channelOf("channel_gone")).template());
    }

    // ---- 孤立模板选择计数（D7） ----

    @Test
    void countOrphanedTemplateSelectionsSumsSurfacesAndSkipsEmptySets() {
        when(channelMapper.countChannelTemplateNotIn(1L, Set.of("channel"))).thenReturn(2);
        when(channelMapper.countArticleTemplateNotIn(1L, Set.of("article"))).thenReturn(1);
        when(articleMapper.countArticleTemplateNotIn(1L, Set.of("article"))).thenReturn(3);
        when(topicMapper.countTopicTemplateNotIn(1L, Set.of("topic"))).thenReturn(4);
        assertEquals(10, service.countOrphanedTemplateSelections(1L,
                Set.of("channel"), Set.of("article"), Set.of("topic")));
        // 空集合整面不计（主题无该前缀模板/扫描失败 → 避免整面误报），不触发 SQL：
        // 第二次调用后各 mapper 仍只被首次调用命中过一次
        assertEquals(0, service.countOrphanedTemplateSelections(1L, Set.of(), Set.of(), Set.of()));
        verify(channelMapper, times(1)).countChannelTemplateNotIn(any(), anyCollection());
        verify(topicMapper, times(1)).countTopicTemplateNotIn(any(), anyCollection());
    }
}

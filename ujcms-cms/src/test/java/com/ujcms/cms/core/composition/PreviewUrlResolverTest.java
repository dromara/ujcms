package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.service.FormService;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.args.FormArgs;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 代表性预览地址解析器测试（preview-url-resolver P1 / template-track-variants 重置后）：
 * 解析矩阵（显式/默认 × 栏目/文章/专题；变体 = 有效模板反查）、
 * 固定路由（清单 route + 内置表，对 {@link UrlConstants} 断言）、参数化路由不作固定路由、
 * limit 截断、q 过滤、空态原因。
 */
class PreviewUrlResolverTest {
    private static final String THEME_KEY = "1/default";

    private ManifestService manifestService;
    private ChannelService channelService;
    private ArticleService articleService;
    private TopicService topicService;
    private FormTypeService formTypeService;
    private FormService formService;
    private PreviewUrlResolver resolver;
    private Site site;

    @BeforeEach
    void setUp() {
        manifestService = mock(ManifestService.class);
        channelService = mock(ChannelService.class);
        articleService = mock(ArticleService.class);
        topicService = mock(TopicService.class);
        formTypeService = mock(FormTypeService.class);
        formService = mock(FormService.class);
        LayoutVariantService layoutVariantService = new LayoutVariantService(manifestService,
                mock(com.ujcms.cms.core.component.TemplateService.class),
                mock(com.ujcms.cms.core.mapper.ChannelMapper.class),
                mock(com.ujcms.cms.core.mapper.ArticleMapper.class), mock(com.ujcms.cms.core.mapper.TopicMapper.class));
        // 表单/问卷路由由商业版 T2 提供：测试按 T2 级别解析，社区版分支另有用例
        resolver = new PreviewUrlResolver(manifestService, layoutVariantService,
                channelService, articleService, topicService, formTypeService, formService, () -> 2);
        site = mock(Site.class);
        when(site.getId()).thenReturn(1L);
        when(site.getThemeKey()).thenReturn(THEME_KEY);
        when(site.getMobileThemeKey()).thenReturn(THEME_KEY);
        when(site.getDynamicUrl()).thenReturn("/");
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of());
        when(articleService.selectList(any(ArticleArgs.class), anyInt(), anyInt())).thenReturn(List.of());
        when(topicService.selectList(any(com.ujcms.cms.core.service.args.TopicArgs.class))).thenReturn(List.of());
        when(formTypeService.selectList(any(FormTypeArgs.class))).thenReturn(List.of());
        when(formService.selectList(any(FormArgs.class), anyInt(), anyInt())).thenReturn(List.of());
    }

    // ---- 快照装配 ----------------------------------------------------------------------------------

    private static PageManifest manifest(String name, List<String> templates, String route) {
        return new PageManifest(name, Map.of("zh-CN", name + "页"), templates, route, null, Map.of(),
                ThemeSettingsSchema.EMPTY, Map.of());
    }

    private void snapshot(PageManifest... themeManifests) {
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        int i = 0;
        for (PageManifest m : themeManifests) {
            builder.addThemeManifest(THEME_KEY, m, "{}", "test-" + i++);
        }
        // 参考主题式变体目录：channel_video/article_product 绑定同名模板
        builder.addThemeLayoutVariants(THEME_KEY, LayoutVariantCatalog.parse(Map.of(
                "channel", List.of(
                        Map.of("value", "default", "template", "channel"),
                        Map.of("value", "channel_video", "template", "channel_video")),
                "article", List.of(Map.of("value", "article_product", "template", "article_product")),
                "topic", List.of(Map.of("value", "topic_special", "template", "topic_special")))), "{}");
        when(manifestService.getSnapshot()).thenReturn(builder.build());
    }

    // ---- 实体桩 ------------------------------------------------------------------------------------

    private static Channel channel(long id, String name, String template) {
        Channel ch = mock(Channel.class);
        when(ch.getId()).thenReturn(id);
        when(ch.getName()).thenReturn(name);
        when(ch.getType()).thenReturn(Channel.TYPE_NORMAL);
        when(ch.getChannelTemplate()).thenReturn(template);
        when(ch.getTemplateName()).thenReturn(StringUtils.isNotBlank(template) ? template : "channel");
        when(ch.getDynamicUrl()).thenReturn("/channel/c" + id);
        return ch;
    }

    private static Article article(long id, String title, Channel channel, String template) {
        Article article = mock(Article.class);
        when(article.getId()).thenReturn(id);
        when(article.getTitle()).thenReturn(title);
        when(article.getChannel()).thenReturn(channel);
        when(article.getArticleTemplate()).thenReturn(template);
        String base = StringUtils.isNotBlank(template) ? template
                : (channel != null && StringUtils.isNotBlank(channel.getArticleTemplate())
                ? channel.getArticleTemplate() : "article");
        when(article.getTemplateName()).thenReturn(base);
        when(article.getDynamicUrl()).thenReturn("/article/" + id);
        return article;
    }

    private static Topic topic(long id, String title, String template) {
        Topic topic = mock(Topic.class);
        when(topic.getId()).thenReturn(id);
        when(topic.getTitle()).thenReturn(title);
        when(topic.getTopicTemplate()).thenReturn(template);
        when(topic.getTemplateName()).thenReturn(StringUtils.isNotBlank(template) ? template : "topic");
        when(topic.getDynamicUrl()).thenReturn("/topic/t" + id);
        return topic;
    }

    private PreviewUrlResolver.Result resolve(String page) {
        return resolver.resolve(site, page, null, null, null, 20);
    }

    // ---- 固定路由（§3.1） --------------------------------------------------------------------------

    @Test
    void manifestRouteYieldsSingleFixedCandidate() {
        snapshot(manifest("home", List.of("index"), "/"));
        PreviewUrlResolver.Result result = resolve("home");
        assertNull(result.emptyReason());
        assertEquals(1, result.candidates().size());
        PreviewUrlResolver.Candidate candidate = result.candidates().get(0);
        assertEquals(PreviewUrlResolver.KIND_FIXED, candidate.kind());
        assertEquals("/", candidate.url());
        // 固定路由不再走实体查询
        verify(channelService, never()).selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class));
    }

    @Test
    void manifestRouteAppliesSiteSubDir() {
        when(site.getDynamicUrl()).thenReturn("/sub");
        snapshot(manifest("home", List.of("index"), "/"), manifest("search", List.of("sys_search"), null));
        assertEquals("/sub", resolve("home").candidates().get(0).url());
        assertEquals("/sub/search", resolve("search").candidates().get(0).url());
    }

    @Test
    void builtInFixedRouteTableCoversSystemTemplatesAndMatchesUrlConstants() {
        snapshot(manifest("search", List.of("sys_search"), null),
                manifest("login", List.of("mem_login"), null),
                manifest("topics", List.of("topics"), null));
        assertEquals("/search", resolve("search").candidates().get(0).url());
        // 有常量者对 UrlConstants 断言（§3.1：控制器仍是路由唯一真相）
        assertEquals(UrlConstants.LOGIN_URL, resolve("login").candidates().get(0).url());
        assertEquals(UrlConstants.TOPIC, resolve("topics").candidates().get(0).url());
    }

    @Test
    void parameterizedRouteIsNotFixed() {
        snapshot(manifest("channel", List.of("channel"), "/channel/[alias]"));
        Channel news = channel(11L, "新闻", null);
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(news));
        PreviewUrlResolver.Result result = resolve("channel");
        assertEquals(1, result.candidates().size());
        assertEquals("channel", result.candidates().get(0).kind());
        assertEquals("/channel/c11", result.candidates().get(0).url());
    }

    // ---- 空态（§6） --------------------------------------------------------------------------------

    @Test
    void missingManifestReportsNoManifest() {
        snapshot(manifest("home", List.of("index"), "/"));
        assertEquals(PreviewUrlResolver.EMPTY_NO_MANIFEST,
                resolve("channel").emptyReason());
    }

    @Test
    void entityPageWithoutContentReportsNoContent() {
        snapshot(manifest("channel", List.of("channel", "channel_video"), null));
        PreviewUrlResolver.Result result = resolve("channel");
        assertTrue(result.candidates().isEmpty());
        assertEquals(PreviewUrlResolver.EMPTY_NO_CONTENT, result.emptyReason());
    }

    // ---- 栏目解析矩阵（模板轨道，§3.2） ------------------------------------------------------------

    @Test
    void channelMatrixExplicitDefaultAndBindingFilter() {
        snapshot(manifest("channel", List.of("channel", "channel_video", "channel_about"), null));
        Channel byDefault = channel(1L, "默认栏目", null);
        Channel byExplicitVideo = channel(2L, "视频栏目", "channel_video");
        Channel inheritedDefault = channel(3L, "继承默认", null);
        Channel outside = channel(4L, "外部模板", "weird_template");
        Channel link = channel(5L, "转向栏目", null);
        when(link.getType()).thenReturn(Channel.TYPE_LINK);
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(byDefault, byExplicitVideo, inheritedDefault, outside, link));
        PreviewUrlResolver.Result result = resolve("channel");
        assertNull(result.emptyReason());
        List<PreviewUrlResolver.Candidate> candidates = result.candidates().stream()
                .filter(c -> "channel".equals(c.kind())).toList();
        assertEquals(List.of("1", "2", "3"),
                candidates.stream().map(PreviewUrlResolver.Candidate::entityId).toList());
        // 默认：模板 channel，反查命中 default 变体（基础模板被主题绑定，template-track-variants D2）
        assertEquals("channel", candidates.get(0).template());
        assertEquals("default", candidates.get(0).variant());
        // 显式模板选择：模板与变体值均为 channel_video
        assertEquals("channel_video", candidates.get(1).template());
        assertEquals("channel_video", candidates.get(1).variant());
        // 未显式选择模板的行按默认模板解析
        assertEquals("channel", candidates.get(2).template());
        assertEquals("default", candidates.get(2).variant());
    }

    @Test
    void variantFilterMatchesTemplateReverseLookup() {
        snapshot(manifest("channel", List.of("channel", "channel_video", "channel_custom"), null));
        Channel blank = channel(1L, "默认栏目", null);
        Channel explicitBase = channel(2L, "显式基础模板", "channel");
        Channel video = channel(3L, "视频栏目", "channel_video");
        Channel inheritedDefault = channel(4L, "继承默认", null);
        Channel unbound = channel(5L, "未绑定模板", "channel_custom");
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(blank, explicitBase, video, inheritedDefault, unbound));
        List<PreviewUrlResolver.Candidate> videoOnly = resolver
                .resolve(site, "channel", "channel_video", null, null, 20).candidates();
        assertEquals(List.of("3"), videoOnly.stream().map(PreviewUrlResolver.Candidate::entityId).toList());
        // 变体 = 有效模板反查（D2）：空白与显式基础模板同解析 default；
        // 未绑定变体的模板不匹配任何变体过滤
        List<PreviewUrlResolver.Candidate> defaults = resolver
                .resolve(site, "channel", "default", null, null, 20).candidates();
        assertEquals(List.of("1", "2", "4"),
                defaults.stream().map(PreviewUrlResolver.Candidate::entityId).toList());
    }

    @Test
    void limitTruncatesAndQFiltersByName() {
        snapshot(manifest("channel", List.of("channel"), null));
        List<Channel> channels = List.of(channel(1L, "新闻资讯", null),
                channel(2L, "产品中心", null), channel(3L, "新闻专栏", null));
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(channels);
        assertEquals(1, resolver.resolve(site, "channel", null, null, null, 1)
                .candidates().size());
        List<PreviewUrlResolver.Candidate> filtered = resolver
                .resolve(site, "channel", null, null, "新闻", 20).candidates();
        assertEquals(List.of("1", "3"), filtered.stream().map(PreviewUrlResolver.Candidate::entityId).toList());
    }

    // ---- 文章解析矩阵（模板轨道，§3.2 栏目级联 + 有界查询） ----------------------------------------

    @Test
    void articleMatrixRestrictsQueryToCandidateChannelsAndFiltersPerArticle() {
        snapshot(manifest("articleProduct", List.of("article_product"), null));
        // 栏目级联：ch1 显式 article_template_ = article_product（候选）；ch2 默认 article（非候选）
        Channel ch1 = channel(1L, "产品", null);
        when(ch1.getArticleTemplate()).thenReturn("article_product");
        Channel ch2 = channel(2L, "新闻", null);
        when(ch2.getArticleTemplate()).thenReturn(null);
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(ch1, ch2));
        // 继承文章（命中）+ 文章级显式选择跳出清单（逐篇过滤剔除）
        Article inheriting = article(11L, "产品甲", ch1, null);
        Article optedOut = article(12L, "改用默认模板", ch1, "article");
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt()))
                .thenReturn(List.of(inheriting, optedOut));
        PreviewUrlResolver.Result result = resolve("articleProduct");
        assertEquals(List.of("11"),
                result.candidates().stream().map(PreviewUrlResolver.Candidate::entityId).toList());
        assertEquals("article_product", result.candidates().get(0).template());
        assertEquals("article_product", result.candidates().get(0).variant());
        // 查询限定在候选栏目（ch1）内
        ArgumentCaptor<ArticleArgs> captor = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(articleService).selectList(captor.capture(), eq(0), anyInt());
        assertEquals(List.of(1L), List.copyOf((java.util.Collection<?>)
                captor.getValue().getQueryMap().get("In_channelId_Long")));
    }

    @Test
    void articleVariantFilterNarrowsChannelsAndFloorsFetchWindow() {
        // 范围自动寻址传 limit=1：变体过滤须收窄到栏目级变体命中的栏目，且取数窗口不随 limit 缩小
        // （回归：limit×3 的最近文章窗口整窗错过稀有变体 → 自动寻址空手而归）
        snapshot(manifest("article", List.of("article", "article_product"), null));
        Channel newsCh = channel(1L, "新闻", null);
        when(newsCh.getArticleTemplate()).thenReturn(null);
        Channel productCh = channel(2L, "产品", null);
        when(productCh.getArticleTemplate()).thenReturn("article_product");
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(newsCh, productCh));
        Article productArticle = article(21L, "产品甲", productCh, null);
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt()))
                .thenReturn(List.of(productArticle));
        PreviewUrlResolver.Result result = resolver
                .resolve(site, "article", "article_product", null, null, 1);
        assertEquals(List.of("21"),
                result.candidates().stream().map(PreviewUrlResolver.Candidate::entityId).toList());
        ArgumentCaptor<ArticleArgs> captor = ArgumentCaptor.forClass(ArticleArgs.class);
        ArgumentCaptor<Integer> windowCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(articleService).selectList(captor.capture(), eq(0), windowCaptor.capture());
        // 查询收窄到变体命中的产品栏目；窗口取下限 60 而非 limit×3=3
        assertEquals(List.of(2L), List.copyOf((java.util.Collection<?>)
                captor.getValue().getQueryMap().get("In_channelId_Long")));
        assertEquals(60, windowCaptor.getValue());
    }

    @Test
    void articleChannelIdOutsideCandidateChannelsSkipsQuery() {
        snapshot(manifest("articleProduct", List.of("article_product"), null));
        Channel ch2 = channel(2L, "新闻", null);
        when(ch2.getArticleTemplate()).thenReturn(null);
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(ch2));
        PreviewUrlResolver.Result result = resolver
                .resolve(site, "articleProduct", null, 2L, null, 20);
        assertEquals(PreviewUrlResolver.EMPTY_NO_CONTENT, result.emptyReason());
        verify(articleService, never()).selectList(any(ArticleArgs.class), anyInt(), anyInt());
    }

    // ---- 专题解析矩阵（模板轨道） ------------------------------------------------------------------

    @Test
    void topicMatrixExplicitAndDefault() {
        snapshot(manifest("topic", List.of("topic", "topic_special"), null));
        List<Topic> topics = List.of(topic(1L, "默认专题", null),
                topic(2L, "显式模板", "topic_special"),
                topic(3L, "继承默认", null),
                topic(4L, "清单之外", "topic_other"));
        when(topicService.selectList(any(com.ujcms.cms.core.service.args.TopicArgs.class)))
                .thenReturn(topics);
        List<PreviewUrlResolver.Candidate> candidates =
                resolve("topic").candidates();
        assertEquals(List.of("1", "2", "3"),
                candidates.stream().map(PreviewUrlResolver.Candidate::entityId).toList());
        // 默认模板未被主题绑定变体（fixture 只绑定 topic_special）→ 有模板无变体上下文
        assertEquals("topic", candidates.get(0).template());
        assertNull(candidates.get(0).variant());
        assertEquals("topic_special", candidates.get(1).template());
        assertEquals("topic_special", candidates.get(1).variant());
        // 未显式选择模板的行按默认模板解析
        assertEquals("topic", candidates.get(2).template());
    }

    // ---- 按模板名解析（P3 模板列表预览按钮） --------------------------------------------------------

    private static FormType formType(long id, String name, String listTemplate, String itemTemplate) {
        FormType type = new FormType();
        type.setId(id);
        type.setName(name);
        type.setListTemplate(listTemplate);
        type.setItemTemplate(itemTemplate);
        return type;
    }

    @Test
    void byTemplateBuiltInFixedRouteWithoutManifestBinding() {
        snapshot(manifest("home", List.of("index"), "/"));
        PreviewUrlResolver.Result result = resolver.resolveByTemplate(site, "sys_search", 20);
        assertNull(result.emptyReason());
        assertEquals(1, result.candidates().size());
        assertEquals(PreviewUrlResolver.KIND_FIXED, result.candidates().get(0).kind());
        assertEquals("/search", result.candidates().get(0).url());
    }

    @Test
    void byTemplateManifestRouteWinsOverEntityWalk() {
        snapshot(manifest("home", List.of("index"), "/"));
        PreviewUrlResolver.Result result = resolver.resolveByTemplate(site, "index", 20);
        assertEquals(List.of("/"), result.candidates().stream().map(PreviewUrlResolver.Candidate::url).toList());
        assertEquals(PreviewUrlResolver.KIND_FIXED, result.candidates().get(0).kind());
        verify(channelService, never()).selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class));
    }

    @Test
    void byTemplateEntityWalkFiltersToSingleTemplate() {
        // channel 与 channel_video 绑定同一页面清单；按模板名反查只回该模板的实体（单模板过滤集）
        snapshot(manifest("channel", List.of("channel", "channel_video"), null));
        Channel byDefault = channel(1L, "默认栏目", null);
        Channel byExplicitVideo = channel(2L, "视频栏目", "channel_video");
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(byDefault, byExplicitVideo));
        PreviewUrlResolver.Result result = resolver.resolveByTemplate(site, "channel_video", 20);
        assertEquals(List.of("2"),
                result.candidates().stream().map(PreviewUrlResolver.Candidate::entityId).toList());
        assertEquals("channel_video", result.candidates().get(0).template());
    }

    @Test
    void byTemplateFormAndSurveyRoutesHiddenBelowTier2() {
        // 社区版/T1：/form-type、/form-list、/survey 由 T2 前台控制器渲染，不存在的路由不作候选
        snapshot(manifest("home", List.of("index"), "/"));
        LayoutVariantService layoutVariantService = new LayoutVariantService(manifestService,
                mock(com.ujcms.cms.core.component.TemplateService.class),
                mock(com.ujcms.cms.core.mapper.ChannelMapper.class),
                mock(com.ujcms.cms.core.mapper.ArticleMapper.class), mock(com.ujcms.cms.core.mapper.TopicMapper.class));
        PreviewUrlResolver community = new PreviewUrlResolver(manifestService, layoutVariantService,
                channelService, articleService, topicService, formTypeService, formService, () -> 0);
        when(formTypeService.selectList(any(FormTypeArgs.class))).thenReturn(List.of(
                formType(31L, "问卷", "form_survey_list", "form_survey_item")));
        assertEquals(0, community.resolveByTemplate(site, "form_survey_list", 20).candidates().size());
        assertEquals(0, community.resolveByTemplate(site, "sys_form_type", 20).candidates().size());
        assertEquals(0, community.resolveByTemplate(site, "sys_survey", 20).candidates().size());
        // 非商业固定路由不受影响
        assertEquals(1, community.resolveByTemplate(site, "sys_vote", 20).candidates().size());
        verify(formTypeService, never()).selectList(any(FormTypeArgs.class));
    }

    @Test
    void topicsRouteAndTopicCandidatesHiddenBelowTier2() {
        // 社区版/T1：/topic 与 /topic/{slug} 由 T2 前台控制器渲染（2026-09-15 专题归属 T2）——
        // 页面名解析的固定路由表、模板名解析与专题实体候选均不给出会 404 的地址，且不查询专题
        snapshot(manifest("topics", List.of("topics"), null), manifest("topic", List.of("topic"), null));
        LayoutVariantService layoutVariantService = new LayoutVariantService(manifestService,
                mock(com.ujcms.cms.core.component.TemplateService.class),
                mock(com.ujcms.cms.core.mapper.ChannelMapper.class),
                mock(com.ujcms.cms.core.mapper.ArticleMapper.class), mock(com.ujcms.cms.core.mapper.TopicMapper.class));
        PreviewUrlResolver community = new PreviewUrlResolver(manifestService, layoutVariantService,
                channelService, articleService, topicService, formTypeService, formService, () -> 0);
        assertEquals(0, community.resolve(site, "topics", null, null, null, 20).candidates().size());
        assertEquals(0, community.resolveByTemplate(site, "topics", 20).candidates().size());
        assertEquals(0, community.resolve(site, "topic", null, null, null, 20).candidates().size());
        assertEquals(0, community.resolveByTemplate(site, "topic", 20).candidates().size());
        verify(topicService, never()).selectList(any(com.ujcms.cms.core.service.args.TopicArgs.class));
    }

    @Test
    void byTemplateFormReverseQueryListAndItem() {
        snapshot(manifest("home", List.of("index"), "/"));
        when(formTypeService.selectList(any(FormTypeArgs.class))).thenReturn(List.of(
                formType(31L, "问卷", "form_survey_list", "form_survey_item"),
                formType(32L, "报名", "form_signup_list", null)));
        // 列表模板：直接以类型ID成链
        PreviewUrlResolver.Result listResult = resolver.resolveByTemplate(site, "form_survey_list", 20);
        assertEquals(1, listResult.candidates().size());
        assertEquals(PreviewUrlResolver.KIND_FORM, listResult.candidates().get(0).kind());
        assertEquals("/form-list/31", listResult.candidates().get(0).url());
        assertEquals("问卷", listResult.candidates().get(0).label());
        // 单条模板：需代表性表单数据行（最新一条）
        Form form = new Form();
        form.setId(88L);
        when(formService.selectList(any(FormArgs.class), anyInt(), anyInt())).thenReturn(List.of(form));
        PreviewUrlResolver.Result itemResult = resolver.resolveByTemplate(site, "form_survey_item", 20);
        assertEquals(List.of("/form-item/88"),
                itemResult.candidates().stream().map(PreviewUrlResolver.Candidate::url).toList());
        // 无数据行则无候选
        when(formService.selectList(any(FormArgs.class), anyInt(), anyInt())).thenReturn(List.of());
        assertEquals(PreviewUrlResolver.EMPTY_NO_CONTENT,
                resolver.resolveByTemplate(site, "form_survey_item", 20).emptyReason());
    }

    @Test
    void byTemplateUnknownTemplateReportsNoContent() {
        snapshot(manifest("home", List.of("index"), "/"));
        PreviewUrlResolver.Result result = resolver.resolveByTemplate(site, "nonexistent", 20);
        assertTrue(result.candidates().isEmpty());
        assertEquals(PreviewUrlResolver.EMPTY_NO_CONTENT, result.emptyReason());
    }
}

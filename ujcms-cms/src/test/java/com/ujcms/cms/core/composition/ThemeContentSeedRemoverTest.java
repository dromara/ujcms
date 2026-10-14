package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.ThemeContentSeedRemover.RemoveResult;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleTopic;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TopicService;
import com.ujcms.cms.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 演示数据移除器测试（§6.1 安全不变量）：
 * <b>不变量 1</b>——只触碰来源列表返回的行（按 {@code (siteId, origin)} 定位，操作员行绝不入列）；
 * <b>不变量 2</b>——干跑不落一笔且计数与实做同一算法；
 * 守卫——含非本源文章/子栏目的栏目只清标记不删除、非本源绑定的专题只清标记、
 * 干跑按「总数 − 本源数」推算栏目可删性；移除即物理删除（回收模式已裁撤），
 * 回收站中的本源文章一并收走。
 */
class ThemeContentSeedRemoverTest {
    private static final Long SITE = 1L;
    private static final String THEME = "mytheme";
    private static final String ORIGIN = "theme:mytheme";

    private ChannelService channelService;
    private ArticleService articleService;
    private TopicService topicService;
    private PlacementService placementService;
    private ChannelMapper channelMapper;
    private ArticleMapper articleMapper;
    private TopicMapper topicMapper;
    private ArticleTopicMapper articleTopicMapper;
    private com.ujcms.cms.core.service.BlockService blockService;
    private com.ujcms.cms.core.mapper.BlockMapper blockMapper;
    private com.ujcms.cms.core.mapper.BlockItemMapper blockItemMapper;
    private com.ujcms.cms.ext.service.VoteService voteService;
    private com.ujcms.cms.ext.mapper.VoteMapper voteMapper;
    private com.ujcms.cms.core.service.ActionService actionService;
    private com.ujcms.cms.ext.mapper.SurveyMapper surveyMapper;
    private com.ujcms.cms.ext.service.FormTypeService formTypeService;
    private com.ujcms.cms.ext.mapper.FormTypeMapper formTypeMapper;
    private com.ujcms.cms.ext.service.LinkGroupService linkGroupService;
    private com.ujcms.cms.ext.service.LinkService linkService;
    private com.ujcms.cms.ext.mapper.LinkGroupMapper linkGroupMapper;
    private com.ujcms.cms.ext.mapper.FormMapper formMapper;
    private com.ujcms.cms.core.service.ModelService modelService;
    private com.ujcms.cms.core.mapper.ModelMapper modelMapper;
    private com.ujcms.cms.core.service.DictTypeService dictTypeService;
    private com.ujcms.cms.core.service.DictService dictService;
    private com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper;
    private com.ujcms.cms.ext.service.SurveyService surveyService;
    private com.ujcms.cms.ext.service.SurveyFeedbackService surveyFeedbackService;
    private ThemeContentSeedRemover remover;
    private final User operator = new User();

    // 夹具：父栏目 news(10) 含子栏目 sub(11)；sub 无文章；news 无自身文章；
    // 栏目 busy(12) 含 1 篇操作员文章；本源文章 a1(100, 已发布, in busy)、a2(101, 已在回收站, in busy)；
    // 专题 t1(20) 只绑本源文章、t2(21) 绑操作员文章 999。
    private Channel news;
    private Channel sub;
    private Channel busy;
    private Article a1;
    private Article a2;
    private Topic t1;
    private Topic t2;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        articleService = mock(ArticleService.class);
        topicService = mock(TopicService.class);
        channelMapper = mock(ChannelMapper.class);
        articleMapper = mock(ArticleMapper.class);
        topicMapper = mock(TopicMapper.class);
        articleTopicMapper = mock(ArticleTopicMapper.class);
        UserService userService = mock(UserService.class);
        blockService = mock(com.ujcms.cms.core.service.BlockService.class);
        blockMapper = mock(com.ujcms.cms.core.mapper.BlockMapper.class);
        blockItemMapper = mock(com.ujcms.cms.core.mapper.BlockItemMapper.class);
        when(blockMapper.listByOrigin(any(), any())).thenReturn(List.of());
        voteService = mock(com.ujcms.cms.ext.service.VoteService.class);
        voteMapper = mock(com.ujcms.cms.ext.mapper.VoteMapper.class);
        actionService = mock(com.ujcms.cms.core.service.ActionService.class);
        when(voteMapper.listByOrigin(any(), any())).thenReturn(List.of());
        surveyMapper = mock(com.ujcms.cms.ext.mapper.SurveyMapper.class);
        surveyService = mock(com.ujcms.cms.ext.service.SurveyService.class);
        surveyFeedbackService = mock(com.ujcms.cms.ext.service.SurveyFeedbackService.class);
        when(surveyMapper.listByOrigin(any(), any())).thenReturn(List.of());
        formTypeService = mock(com.ujcms.cms.ext.service.FormTypeService.class);
        formTypeMapper = mock(com.ujcms.cms.ext.mapper.FormTypeMapper.class);
        formMapper = mock(com.ujcms.cms.ext.mapper.FormMapper.class);
        when(formTypeMapper.listByOrigin(any(), any())).thenReturn(List.of());
        when(formTypeService.selectList(any(com.ujcms.cms.ext.service.args.FormTypeArgs.class)))
                .thenReturn(List.of());
        modelService = mock(com.ujcms.cms.core.service.ModelService.class);
        modelMapper = mock(com.ujcms.cms.core.mapper.ModelMapper.class);
        dictTypeService = mock(com.ujcms.cms.core.service.DictTypeService.class);
        dictService = mock(com.ujcms.cms.core.service.DictService.class);
        dictTypeMapper = mock(com.ujcms.cms.core.mapper.DictTypeMapper.class);
        linkGroupService = mock(com.ujcms.cms.ext.service.LinkGroupService.class);
        linkService = mock(com.ujcms.cms.ext.service.LinkService.class);
        linkGroupMapper = mock(com.ujcms.cms.ext.mapper.LinkGroupMapper.class);
        when(linkGroupMapper.listByOrigin(any(), any())).thenReturn(List.of());
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenReturn(List.of());
        placementService = mock(PlacementService.class);
        when(placementService.listBySiteId(any())).thenReturn(List.of());
        remover = new ThemeContentSeedRemover(channelService, articleService, topicService, blockService,
                userService, channelMapper, articleMapper, topicMapper, articleTopicMapper,
                blockMapper, blockItemMapper, voteService, voteMapper, actionService,
                surveyMapper, surveyService, surveyFeedbackService, formTypeService, formTypeMapper, formMapper,
                linkGroupService, linkService, linkGroupMapper,
                modelService, modelMapper, dictTypeService, dictService, dictTypeMapper, placementService);

        news = channel(10L, null);
        sub = channel(11L, 10L);
        busy = channel(12L, null);
        a1 = article(100L, 12L, Article.STATUS_PUBLISHED);
        a2 = article(101L, 12L, Article.STATUS_DELETED);
        t1 = topic(20L);
        t2 = topic(21L);

        when(channelMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(news, sub, busy));
        when(articleMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(a1, a2));
        when(topicMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(t1, t2));
        // busy 含 3 篇文章（2 本源 + 1 操作员）；news/sub 无文章
        when(articleMapper.countByChannelId(12L)).thenReturn(3);
        when(articleMapper.countByChannelId(10L)).thenReturn(0);
        when(articleMapper.countByChannelId(11L)).thenReturn(0);
        // news 的子栏目只有本源 sub；sub/busy 无子栏目
        when(channelMapper.listChildren(10L)).thenReturn(List.of(sub));
        when(channelMapper.listChildren(11L)).thenReturn(List.of());
        when(channelMapper.listChildren(12L)).thenReturn(List.of());
        // t1 只绑本源文章；t2 绑操作员文章 999
        when(articleTopicMapper.listByTopicId(20L)).thenReturn(
                List.of(new ArticleTopic(100L, 20L, 0), new ArticleTopic(101L, 20L, 1)));
        when(articleTopicMapper.listByTopicId(21L)).thenReturn(
                List.of(new ArticleTopic(999L, 21L, 0)));
    }

    private static Channel channel(Long id, Long parentId) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setSiteId(SITE);
        channel.setParentId(parentId);
        channel.setOrigin(ORIGIN);
        return channel;
    }

    private static Article article(Long id, Long channelId, short status) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(SITE);
        article.setChannelId(channelId);
        article.setStatus(status);
        article.setOrigin(ORIGIN);
        return article;
    }

    private static Topic topic(Long id) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setSiteId(SITE);
        topic.setOrigin(ORIGIN);
        return topic;
    }

    @Test
    void deletesArticlesPhysicallyWithGuards() {
        // 实做时本源文章已物理删除：busy 只剩 1 篇操作员文章
        when(articleMapper.countByChannelId(12L)).thenReturn(1);

        RemoveResult result = remover.remove(SITE, THEME, false, operator);

        // 文章：已发布与回收站中的本源文章一并物理删除
        verify(articleService).completelyDelete(a1, operator);
        verify(articleService).completelyDelete(a2, operator);
        assertEquals(2, result.articlesDeleted());
        // 栏目：sub（空）→ 删；news（子栏目 sub 已删，无文章）→ 删；
        // busy 仍含操作员文章 → 清标记，绝不删除（不变量 1 / D7）
        verify(channelService).delete(sub);
        verify(channelService).delete(news);
        verify(channelService, never()).delete(busy);
        assertEquals(2, result.channelsDeleted());
        assertEquals(1, result.channelsOriginCleared());
        // 专题：t1（只绑本源）→ 删；t2（绑操作员文章）→ 清标记
        verify(topicService).delete(t1);
        verify(topicService, never()).delete(t2);
        assertEquals(1, result.topicsDeleted());
        assertEquals(1, result.topicsOriginCleared());
        // 幸存者清标记（不变量 1：按 (siteId, origin) 定位）；文章不清——失败行保留标记以便重跑续收
        verify(channelMapper).clearOrigin(SITE, ORIGIN);
        verify(topicMapper).clearOrigin(SITE, ORIGIN);
        verify(articleMapper, never()).clearOrigin(anyLong(), any());
    }

    @Test
    void dryRunSubtractsOriginArticlesButOperatorContentStillBlocks() {
        RemoveResult result = remover.remove(SITE, THEME, true, operator);

        // busy：总数 3 − 本源 2 = 1 篇操作员文章 → 仍阻止删除
        assertEquals(1, result.channelsOriginCleared());
        assertEquals(2, result.channelsDeleted());
        assertEquals(2, result.articlesDeleted());
    }

    /** E4b / D31：选票守卫——times 计数或 Action 记录任一存在即清标记不删；两者皆无才删除。 */
    @Test
    void voteBallotGuardClearsOriginOtherwiseDeletes() {
        com.ujcms.cms.ext.domain.Vote clean = vote(90L, 0);
        com.ujcms.cms.ext.domain.Vote counted = vote(91L, 3);
        com.ujcms.cms.ext.domain.Vote balloted = vote(92L, 0);
        when(voteMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(clean, counted, balloted));
        when(actionService.existsBy(eq(com.ujcms.cms.ext.domain.Vote.ACTION_TYPE), eq(92L),
                any(), any(), any(), any(), any())).thenReturn(true);

        RemoveResult result = remover.remove(SITE, THEME, false, operator);

        verify(voteService).delete(90L);
        verify(voteService, never()).delete(91L);
        verify(voteService, never()).delete(92L);
        assertEquals(1, result.votesDeleted());
        assertEquals(2, result.votesOriginCleared());
        verify(voteMapper).clearOrigin(SITE, ORIGIN);
    }

    /** E4f / D44：散列守卫——分组连同链接仍为种子形（存储散列复现）才删（链接先行、分组随后），
     * 操作员任何改动（含链接增删）即清标记转普通操作员行。 */
    @Test
    void linkGroupHashGuardDeletesSeedShapedClearsModified() {
        com.ujcms.cms.ext.domain.Link cleanLink = new com.ujcms.cms.ext.domain.Link();
        cleanLink.setId(71L);
        cleanLink.setGroupId(70L);
        cleanLink.setName("链接");
        cleanLink.setLinkUrl("https://example.com/");
        cleanLink.setOrder(71L);
        com.ujcms.cms.ext.domain.LinkGroup clean = linkGroup(70L, "gov");
        clean.setOriginHash(SeedHash.linkGroupHash(clean, List.of(cleanLink)));
        // 操作员改过：存储散列出自旧状态（链接后被操作员增改），当前投影不再复现
        com.ujcms.cms.ext.domain.LinkGroup modified = linkGroup(72L, "media");
        modified.setOriginHash("stale-hash");
        when(linkGroupMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(clean, modified));
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenAnswer(inv -> {
                    Object typeId = inv.<com.ujcms.cms.ext.service.args.LinkArgs>getArgument(0)
                            .getQueryMap().get("EQ_groupId_Long");
                    return Long.valueOf(70L).equals(typeId) ? List.of(cleanLink) : List.of();
                });

        RemoveResult result = remover.remove(SITE, THEME, false, operator);

        verify(linkService).delete(71L);
        verify(linkGroupService).delete(70L);
        verify(linkGroupService, never()).delete(72L);
        assertEquals(1, result.linkGroupsDeleted());
        assertEquals(1, result.linkGroupsOriginCleared());
        verify(linkGroupMapper).clearOrigin(SITE, ORIGIN);
    }

    /** 不变量 2（干跑与实做计数一致）：链接指向本源栏目时，实做的栏目删除经
     * {@code LinkService.preChannelDelete} 级联收走该链接——散列守卫须在内容处置之前判定，
     * 否则实做时投影已缺行，种子形分组被误判为「操作员改过」而留下（干跑报删、实做报清）。 */
    @Test
    void linkGroupGuardIgnoresCascadeOfRemovedChannels() {
        sub.setSlug("sub");
        com.ujcms.cms.ext.domain.Link urlLink = new com.ujcms.cms.ext.domain.Link();
        urlLink.setId(71L);
        urlLink.setGroupId(70L);
        urlLink.setName("外部链接");
        urlLink.setLinkUrl("https://example.com/");
        urlLink.setOrder(71L);
        com.ujcms.cms.ext.domain.Link channelLink = new com.ujcms.cms.ext.domain.Link();
        channelLink.setId(73L);
        channelLink.setGroupId(70L);
        channelLink.setName("栏目链接");
        channelLink.setChannelId(sub.getId());
        channelLink.setChannel(sub);
        channelLink.setOrder(73L);
        List<com.ujcms.cms.ext.domain.Link> stored = new ArrayList<>(List.of(urlLink, channelLink));
        com.ujcms.cms.ext.domain.LinkGroup group = linkGroup(70L, "site-nav");
        group.setOriginHash(SeedHash.linkGroupHash(group, stored));
        when(linkGroupMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(group));
        when(linkService.selectList(any(com.ujcms.cms.ext.service.args.LinkArgs.class)))
                .thenAnswer(inv -> List.copyOf(stored));
        // 镜像 LinkService.preChannelDelete：删除栏目即收走指向它的链接行
        when(channelService.delete(sub)).thenAnswer(inv -> {
            stored.removeIf(link -> sub.getId().equals(link.getChannelId()));
            return 1;
        });

        RemoveResult dry = remover.remove(SITE, THEME, true, operator);
        RemoveResult real = remover.remove(SITE, THEME, false, operator);

        assertEquals(1, dry.linkGroupsDeleted());
        assertEquals(0, dry.linkGroupsOriginCleared());
        assertEquals(dry.linkGroupsDeleted(), real.linkGroupsDeleted());
        assertEquals(dry.linkGroupsOriginCleared(), real.linkGroupsOriginCleared());
        verify(linkService).delete(71L);
        verify(linkService).delete(73L);
        verify(linkGroupService).delete(70L);
    }

    private static com.ujcms.cms.ext.domain.LinkGroup linkGroup(Long id, String alias) {
        com.ujcms.cms.ext.domain.LinkGroup type = new com.ujcms.cms.ext.domain.LinkGroup();
        type.setId(id);
        type.setSiteId(SITE);
        type.setAlias(alias);
        type.setName(alias);
        type.setOrigin(ORIGIN);
        type.setOriginId(alias);
        return type;
    }

    /** E4d / D35：反馈守卫——times 或反馈记录任一存在即清标记不删；删除经核心 SurveyService（任何版本都可及，D46）。 */
    @Test
    void surveyFeedbackGuardClearsOrigin() {
        com.ujcms.cms.ext.domain.Survey clean = survey(80L, 0);
        com.ujcms.cms.ext.domain.Survey counted = survey(81L, 2);
        com.ujcms.cms.ext.domain.Survey responded = survey(82L, 0);
        when(surveyMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(clean, counted, responded));
        when(surveyFeedbackService.existsBySurveyId(82L)).thenReturn(true);
        when(surveyService.delete(anyLong())).thenReturn(1);

        RemoveResult result = remover.remove(SITE, THEME, false, operator);
        verify(surveyService).delete(80L);
        verify(surveyService, never()).delete(81L);
        verify(surveyService, never()).delete(82L);
        assertEquals(1, result.surveysDeleted());
        assertEquals(2, result.surveysOriginCleared());
        verify(surveyMapper).clearOrigin(SITE, ORIGIN);
    }

    /** E4e / D40：提交守卫——有提交即清标记不删；删除经核心 FormTypeService（任何版本都可及，D46）。 */
    @Test
    void formSubmissionGuardClearsOrigin() {
        com.ujcms.cms.ext.domain.FormType clean = formType(90L);
        com.ujcms.cms.ext.domain.FormType submitted = formType(91L);
        when(formTypeMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(clean, submitted));
        when(formMapper.existsByTypeId(90L)).thenReturn(0);
        when(formMapper.existsByTypeId(91L)).thenReturn(1);
        when(formTypeService.delete(anyLong())).thenReturn(1);

        RemoveResult result = remover.remove(SITE, THEME, false, operator);
        verify(formTypeService).delete(90L);
        verify(formTypeService, never()).delete(91L);   // 有提交 → 清标记不删（用户提交恒不销毁）
        assertEquals(1, result.formsDeleted());
        assertEquals(1, result.formsOriginCleared());
        verify(formTypeMapper).clearOrigin(SITE, ORIGIN);
    }

    /** E4e：模型移除守卫纳入表单类型绑定——幸存表单类型仍占用其 form 模型即清标记不删。 */
    @Test
    void modelGuardSeesSurvivingFormTypeBinding() {
        com.ujcms.cms.core.domain.Model formModel = new com.ujcms.cms.core.domain.Model();
        formModel.setId(600L);
        formModel.setSiteId(SITE);
        formModel.setType("form");
        formModel.setName("报名表");
        formModel.setOrigin(ORIGIN);
        formModel.setOriginId("m-form-signup");
        when(modelMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(formModel));
        // 一枚操作员表单类型（本次未删——非本源）绑定该 form 模型 → 模型须保留
        com.ujcms.cms.ext.domain.FormType operatorForm = formType(95L);
        operatorForm.setModelId(600L);
        operatorForm.setOrigin(null);
        when(formTypeService.selectList(any(com.ujcms.cms.ext.service.args.FormTypeArgs.class)))
                .thenReturn(List.of(operatorForm));

        RemoveResult result = remover.remove(SITE, THEME, false, operator);

        assertEquals(0, result.modelsDeleted());
        assertEquals(1, result.modelsOriginCleared());     // 绑定幸存 → 转普通操作员行
        verify(modelService, never()).delete(anyLong());
    }

    private static com.ujcms.cms.ext.domain.FormType formType(Long id) {
        com.ujcms.cms.ext.domain.FormType bean = new com.ujcms.cms.ext.domain.FormType();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setModelId(600L);
        bean.setOrigin(ORIGIN);
        return bean;
    }

    private static com.ujcms.cms.ext.domain.Survey survey(Long id, int times) {
        com.ujcms.cms.ext.domain.Survey bean = new com.ujcms.cms.ext.domain.Survey();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setTimes(times);
        return bean;
    }

    private static com.ujcms.cms.ext.domain.Vote vote(Long id, int times) {
        com.ujcms.cms.ext.domain.Vote bean = new com.ujcms.cms.ext.domain.Vote();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setTimes(times);
        return bean;
    }

    @Test
    void dryRunMutatesNothingAndCountsMatchActRun() {
        RemoveResult dry = remover.remove(SITE, THEME, true, operator);

        // 不变量 2：干跑不落一笔
        verifyNoInteractions(channelService, articleService, topicService);
        verify(channelMapper, never()).clearOrigin(anyLong(), any());
        verify(topicMapper, never()).clearOrigin(anyLong(), any());

        // 实做时本源文章已物理删除：busy 只剩 1 篇操作员文章（与干跑的「总数 − 本源数」同值）
        when(articleMapper.countByChannelId(12L)).thenReturn(1);
        RemoveResult act = remover.remove(SITE, THEME, false, operator);
        // 同一算法：干跑计数与实做一致（dryRun 标志位除外）
        assertEquals(act.articlesDeleted(), dry.articlesDeleted());
        assertEquals(act.channelsDeleted(), dry.channelsDeleted());
        assertEquals(act.channelsOriginCleared(), dry.channelsOriginCleared());
        assertEquals(act.topicsDeleted(), dry.topicsDeleted());
        assertEquals(act.topicsOriginCleared(), dry.topicsOriginCleared());
    }

    @Test
    void operatorRowsAreNeverTouched() {
        // 不变量 1：全部定位都经 (siteId, origin) 列表——空列表 = 无本源行 = 无任何触碰
        when(channelMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of());
        when(articleMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of());
        when(topicMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of());

        RemoveResult result = remover.remove(SITE, THEME, false, operator);

        verifyNoInteractions(channelService, articleService, topicService);
        assertEquals(0, result.articlesDeleted());
        assertEquals(0, result.channelsDeleted());
        assertEquals(0, result.topicsDeleted());
    }

    @Test
    void uninstallKeepClearsOriginOnAllThreeTables() {
        remover.keepForUninstall(SITE, THEME);
        verify(articleMapper).clearOrigin(SITE, ORIGIN);
        verify(channelMapper).clearOrigin(SITE, ORIGIN);
        verify(topicMapper).clearOrigin(SITE, ORIGIN);
        verifyNoInteractions(channelService, articleService, topicService);
    }

    @Test
    void uninstallArchiveArchivesArticlesAndClearsChannelTopicOrigin() {
        remover.archiveForUninstall(SITE, THEME);
        verify(articleService).archive(a1);
        verify(articleService).archive(a2);
        verify(channelMapper).clearOrigin(SITE, ORIGIN);
        verify(topicMapper).clearOrigin(SITE, ORIGIN);
        // 文章保留来源标记（与构成行归档口径一致）
        verify(articleMapper, never()).clearOrigin(anyLong(), any());
    }
    /** model-seeding D9：幸存行仍绑定的模型清标记；无绑定的删除（守卫以幸存集在内存推算）。 */
    @Test
    void modelRemovalHonorsBindingGuard() {
        com.ujcms.cms.core.domain.Model bound = new com.ujcms.cms.core.domain.Model();
        bound.setId(900L);
        bound.setSiteId(SITE);
        bound.setType("article");
        bound.setName("视频");
        com.ujcms.cms.core.domain.Model free = new com.ujcms.cms.core.domain.Model();
        free.setId(901L);
        free.setSiteId(SITE);
        free.setType("topic");
        free.setName("常规专题");
        when(modelMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(bound, free));
        // 幸存操作员栏目绑定 900（列表含本次不删的行）；901 无任何绑定
        Channel operatorChannel = channel(70L, null);
        operatorChannel.setChannelModelId(12L);
        operatorChannel.setArticleModelId(900L);
        when(channelService.selectList(any(com.ujcms.cms.core.service.args.ChannelArgs.class)))
                .thenReturn(List.of(operatorChannel));
        when(topicService.selectList(any(com.ujcms.cms.core.service.args.TopicArgs.class)))
                .thenReturn(List.of());

        RemoveResult dry = remover.remove(SITE, THEME, true, operator);
        assertEquals(1, dry.modelsDeleted());
        assertEquals(1, dry.modelsOriginCleared());
        verify(modelService, never()).delete(anyLong());

        RemoveResult act = remover.remove(SITE, THEME, false, operator);
        assertEquals(1, act.modelsDeleted());
        assertEquals(1, act.modelsOriginCleared());
        verify(modelService).delete(901L);
        verify(modelService, never()).delete(900L);
        verify(modelMapper).clearOrigin(SITE, ORIGIN);
    }

    /** D8/D9：被幸存模型字段引用的字典类型清标记；无引用的删除（类型与条目由 DictTypeService.delete 一次删除）。 */
    @Test
    void dictRemovalHonorsModelReferenceGuardAndDeletesTypeWithEntriesAtOnce() {
        com.ujcms.cms.core.domain.DictType referenced = new com.ujcms.cms.core.domain.DictType();
        referenced.setId(700L);
        referenced.setSiteId(SITE);
        referenced.setAlias("video_area");
        com.ujcms.cms.core.domain.DictType free = new com.ujcms.cms.core.domain.DictType();
        free.setId(701L);
        free.setSiteId(SITE);
        free.setAlias("download_type");
        when(dictTypeMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(referenced, free));
        // 幸存可见模型的 customs 引用 700；701 无引用
        com.ujcms.cms.core.domain.Model visible = new com.ujcms.cms.core.domain.Model();
        visible.setId(910L);
        visible.setSiteId(SITE);
        visible.setType("article");
        visible.setName("视频");
        visible.setCustoms("[{\"code\":\"area\",\"type\":\"select\",\"dictTypeId\":\"700\"}]");
        when(modelService.selectList(any(com.ujcms.cms.core.service.args.ModelArgs.class)))
                .thenReturn(List.of(visible));
        // 701 的条目：81、82（随类型一并删除，移除器不逐条删）
        when(dictService.listByTypeId(701L)).thenReturn(List.of(dictEntry(81L, 701L, false), dictEntry(82L, 701L, false)));

        RemoveResult dry = remover.remove(SITE, THEME, true, operator);
        assertEquals(1, dry.dictsDeleted());
        assertEquals(1, dry.dictsOriginCleared());
        verify(dictTypeService, never()).delete(anyLong());

        RemoveResult act = remover.remove(SITE, THEME, false, operator);
        assertEquals(1, act.dictsDeleted());
        assertEquals(1, act.dictsOriginCleared());
        verify(dictTypeService).delete(701L);
        verify(dictTypeService, never()).delete(700L);
        verify(dictService, never()).delete(anyLong());
        verify(dictTypeMapper).clearOrigin(SITE, ORIGIN);
    }

    /**
     * 其它站点的模型绑定了本站字典类型（早期站点复制不重映射字典ID）：守卫须看所有站点的模型，
     * 类型只清标记、条目一条不动——否则条目先被清空、类型删除再被 DictTypeService 拒绝，留下空类型。
     */
    @Test
    void dictBoundByAnotherSitesModelKeepsItsEntries() {
        com.ujcms.cms.core.domain.DictType type = new com.ujcms.cms.core.domain.DictType();
        type.setId(700L);
        type.setSiteId(SITE);
        type.setAlias("video_area");
        when(dictTypeMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(type));
        com.ujcms.cms.core.domain.Model foreign = new com.ujcms.cms.core.domain.Model();
        foreign.setId(920L);
        foreign.setSiteId(SITE + 1);
        foreign.setType("article");
        foreign.setName("视频");
        foreign.setCustoms("[{\"code\":\"area\",\"type\":\"select\",\"dictTypeId\":\"700\"}]");
        // 仅「不带站点范围」的查询才返回其它站点的模型：守卫若只查本站可见模型，就看不到这处绑定
        when(modelService.selectList(org.mockito.ArgumentMatchers.argThat(
                (com.ujcms.cms.core.service.args.ModelArgs args) -> args != null && args.getQueryMap().isEmpty())))
                .thenReturn(List.of(foreign));
        when(dictService.listByTypeId(700L)).thenReturn(List.of(dictEntry(71L, 700L, false)));

        RemoveResult act = remover.remove(SITE, THEME, false, operator);
        assertEquals(0, act.dictsDeleted());
        assertEquals(1, act.dictsOriginCleared());
        verify(dictTypeService, never()).delete(anyLong());
        verify(dictService, never()).delete(anyLong());
        verify(dictTypeMapper).clearOrigin(SITE, ORIGIN);
    }

    /** 含 sys 条目的类型不删（DictTypeService.delete 的级联不拒绝 sys 条目）：清标记，条目保留。 */
    @Test
    void dictWithSysEntryIsKept() {
        com.ujcms.cms.core.domain.DictType type = new com.ujcms.cms.core.domain.DictType();
        type.setId(702L);
        type.setSiteId(SITE);
        type.setAlias("download_license");
        when(dictTypeMapper.listByOrigin(SITE, ORIGIN)).thenReturn(List.of(type));
        when(modelService.selectList(any(com.ujcms.cms.core.service.args.ModelArgs.class))).thenReturn(List.of());
        when(dictService.listByTypeId(702L)).thenReturn(List.of(dictEntry(91L, 702L, false), dictEntry(92L, 702L, true)));

        RemoveResult act = remover.remove(SITE, THEME, false, operator);
        assertEquals(0, act.dictsDeleted());
        assertEquals(1, act.dictsOriginCleared());
        verify(dictTypeService, never()).delete(anyLong());
        verify(dictService, never()).delete(anyLong());
    }

    private static com.ujcms.cms.core.domain.Dict dictEntry(long id, long typeId, boolean sys) {
        com.ujcms.cms.core.domain.Dict entry = new com.ujcms.cms.core.domain.Dict();
        entry.setId(id);
        entry.setTypeId(typeId);
        entry.setValue(String.valueOf(id));
        entry.setSys(sys);
        return entry;
    }

    @Test
    void countsLivePanelsWhoseRefsWillBreakWithoutTouchingThem() {
        // D24：设置里钉住了将删实体ID（news 10 将删；busy 12 守卫不过仅清标记；文章 100 将删）的在用面板计数
        // ——形状无关递归匹配裸ID（含嵌套列表/对象）；归档面板不计；干跑与实做同值；面板行绝不被触碰
        com.ujcms.cms.core.domain.Placement viaChannel = placement(1L, java.util.Map.of("channel", "10"));
        com.ujcms.cms.core.domain.Placement viaKeptChannel = placement(2L, java.util.Map.of("channel", "12"));
        com.ujcms.cms.core.domain.Placement viaNestedArticle = placement(3L,
                java.util.Map.of("slides", List.of(java.util.Map.of("article", 100))));
        com.ujcms.cms.core.domain.Placement archived = placement(4L, java.util.Map.of("channel", "10"));
        archived.setArchivedAt(java.time.OffsetDateTime.now());
        when(placementService.listBySiteId(SITE)).thenReturn(
                List.of(viaChannel, viaKeptChannel, viaNestedArticle, archived));

        RemoveResult dry = remover.remove(SITE, THEME, true, operator);
        assertEquals(2, dry.panelRefsBroken());

        // 实做时本源文章已物理删除：busy 只剩 1 篇操作员文章（守卫不过，仍只清标记）
        when(articleMapper.countByChannelId(12L)).thenReturn(1);
        RemoveResult act = remover.remove(SITE, THEME, false, operator);
        assertEquals(2, act.panelRefsBroken());
        verify(placementService, never()).savePanel(any(), any());
        verify(placementService, never()).delete(any());
    }

    private static com.ujcms.cms.core.domain.Placement placement(long id, java.util.Map<String, Object> settings) {
        com.ujcms.cms.core.domain.Placement placement = new com.ujcms.cms.core.domain.Placement();
        placement.setId(id);
        placement.setSiteId(SITE);
        placement.setPageRef("home");
        placement.setSlotName("aside");
        placement.setPanelShape("RankedList");
        placement.setSettings(new java.util.HashMap<>(settings));
        placement.setEnabled(true);
        return placement;
    }
}

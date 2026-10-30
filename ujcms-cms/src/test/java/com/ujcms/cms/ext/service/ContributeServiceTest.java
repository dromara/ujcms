package com.ujcms.cms.ext.service;

import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.GroupContribute;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.GroupContributeMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.ext.component.UgcSubmitDispatcher;
import com.ujcms.cms.ext.listener.UgcSubmitListener.UgcSubmission;
import com.ujcms.cms.ext.service.ContributeService.Content;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.domain.Article.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员投稿规则（docs/spec/member-contribute/plan.md）：谁能向哪个栏目投稿（D2、D3），投稿一律待审核、服务端设定全部
 * 可信字段（D6），正文按半可信层级净化、自定义字段只收简单类型并校验必填（D5），归属与状态的修改、删除守卫。
 */
class ContributeServiceTest {
    private static final long SITE = 1L;
    private static final long ORG = 3L;
    private static final long CHANNEL = 5L;
    private static final long MEMBER = 7L;
    private static final long MEMBER_GROUP = 10L;
    private static final long WRITER_GROUP = 20L;

    private ArticleService articleService;
    private ChannelService channelService;
    private AttachmentService attachmentService;
    private GroupContributeMapper groupContributeMapper;
    private HtmlSanitizeService sanitizeService;
    private UgcSubmitDispatcher dispatcher;
    private TagService tagService;
    private ContributeService service;

    private final Site site = new Site();
    private final User member = new User();

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        attachmentService = mock(AttachmentService.class);
        groupContributeMapper = mock(GroupContributeMapper.class);
        sanitizeService = mock(HtmlSanitizeService.class);
        dispatcher = mock(UgcSubmitDispatcher.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(new Config());
        WordScreeningService wordScreeningService = mock(WordScreeningService.class);
        when(wordScreeningService.screen(any())).thenReturn(List.of());
        // 净化器原样返回：只断言调用的层级
        when(sanitizeService.sanitize(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        tagService = mock(TagService.class);
        // 本站已有标签 t1；其它名字一律“不存在”
        when(tagService.selectByName(SITE, "t1")).thenReturn(new Tag(SITE, 1L, "t1"));
        service = new ContributeService(articleService, channelService, attachmentService, configService, tagService,
                groupContributeMapper, sanitizeService, wordScreeningService, dispatcher);
        site.setId(SITE);
        site.setOrgId(ORG);
        Site.Contribute settings = new Site.Contribute();
        settings.setEnabled(true);
        site.setContribute(settings);
        member.setId(MEMBER);
        member.setGroupId(MEMBER_GROUP);
        member.setType(User.TYPE_MEMBER);
    }

    private Channel channel(boolean allowContribute, short type) {
        Channel channel = new Channel();
        channel.setId(CHANNEL);
        channel.setSiteId(SITE);
        channel.setAllowContribute(allowContribute);
        channel.setType(type);
        Model model = new Model();
        model.setCustoms("[{\"code\":\"city\",\"name\":\"城市\",\"type\":\"text\",\"required\":true},"
                + "{\"code\":\"photo\",\"name\":\"照片\",\"type\":\"imageUpload\"},"
                + "{\"code\":\"intro\",\"name\":\"简介\",\"type\":\"richEditor\"}]");
        channel.setArticleModel(model);
        return channel;
    }

    private static Content content(String city) {
        Map<String, Object> customs = new HashMap<>();
        customs.put("city", city);
        customs.put("photo", "/uploads/evil.png");
        customs.put("intro", "<script>x</script>");
        return new Content(" 标题 ", "摘要", "作者", null, "<p onclick=\"x()\">正文</p>", List.of("t1"), customs);
    }

    // ----- 谁能投（D2、D3） -----

    @Test
    void contributionNeedsTheSiteSwitchTheChannelFlagAndAnArticleChannel() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);
        assertTrue(service.canContribute(site, member, channel));

        site.getContribute().setEnabled(false);
        assertFalse(service.canContribute(site, member, channel));
        site.getContribute().setEnabled(true);

        assertFalse(service.canContribute(site, member, channel(false, Channel.TYPE_NORMAL)));
        assertFalse(service.canContribute(site, member, channel(true, Channel.TYPE_LINK)));
        assertFalse(service.canContribute(site, member, channel(true, Channel.TYPE_SINGLE)));
    }

    @Test
    void groupListRestrictsWhoMayContributeAndAppliesToAdminsToo() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);
        when(groupContributeMapper.listGroupByChannelId(CHANNEL)).thenReturn(List.of(WRITER_GROUP));
        // 普通组不在名单里
        assertFalse(service.canContribute(site, member, channel));
        // 管理员从前台投稿同样看用户组
        User admin = new User();
        admin.setId(1L);
        admin.setType(User.TYPE_NORMAL);
        admin.setGroupId(Group.MEMBER_GROUP_ID);
        assertFalse(service.canContribute(site, admin, channel));

        member.setGroupId(WRITER_GROUP);
        assertTrue(service.canContribute(site, member, channel));
    }

    @Test
    void listChannelsFiltersTheSiteTreeByFlagTypeAndGroup() {
        Channel open = channel(true, Channel.TYPE_NORMAL);
        Channel closed = channel(false, Channel.TYPE_NORMAL);
        closed.setId(6L);
        Channel restricted = channel(true, Channel.TYPE_NORMAL);
        restricted.setId(8L);
        // 整站矩阵一次装入（不逐栏目查询）
        when(groupContributeMapper.listBySiteId(SITE)).thenReturn(List.of(new GroupContribute(WRITER_GROUP, 8L, SITE)));
        when(channelService.selectList(any(ChannelArgs.class))).thenReturn(List.of(open, closed, restricted));

        assertEquals(List.of(open), service.listChannels(site, member));
        verify(groupContributeMapper, never()).listGroupByChannelId(anyLong());

        site.getContribute().setEnabled(false);
        assertTrue(service.listChannels(site, member).isEmpty());
    }

    // ----- 新投稿（D5、D6） -----

    @Test
    void submitSetsEveryTrustedFieldSanitisesAtBasicAndKeepsOnlySimpleCustoms() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);

        Article article = service.submit(site, member, channel, content("北京"));

        assertEquals(SITE, article.getSiteId());
        assertEquals(CHANNEL, article.getChannelId());
        assertEquals(ORG, article.getOrgId());
        assertEquals(INPUT_TYPE_CONTRIBUTE, article.getInputType());
        assertEquals(STATUS_PENDING, article.getStatus());
        assertEquals(TYPE_NORMAL, article.getType());
        assertEquals("标题", article.getTitle());
        assertEquals("摘要", article.getSeoDescription());
        assertEquals("作者", article.getAuthor());
        assertNull(article.getImage());
        verify(sanitizeService).sanitize("<p onclick=\"x()\">正文</p>", PolicyTier.BASIC);
        // 只收简单类型字段；上传类、富文本字段被丢弃
        assertEquals("北京", article.getCustoms().get("city"));
        assertFalse(article.getCustoms().containsKey("photo"));
        assertFalse(article.getCustoms().containsKey("intro"));
        verify(articleService).insert(article, MEMBER, List.of("t1"));
        verify(articleService).submitForReview(article, MEMBER);
        ArgumentCaptor<UgcSubmission> captor = ArgumentCaptor.forClass(UgcSubmission.class);
        verify(dispatcher).dispatch(captor.capture());
        assertEquals(UgcSubmission.TYPE_ARTICLE, captor.getValue().type());
        assertEquals(MEMBER, captor.getValue().userId());
        assertEquals(STATUS_PENDING, captor.getValue().heldStatus());
    }

    @Test
    void submitCountsAgainstTheMembersDailyQuota() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);
        site.getContribute().setDailyLimit(2);
        when(articleService.count(any(ArticleArgs.class))).thenReturn(2L);

        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.submit(site, member, channel, content("北京")));
        assertEquals("error.contribute.dailyLimit", e.getCode());
        verify(articleService, never()).insert(any(Article.class), anyLong(), anyList());

        // 计数窗口：本人、本站、投稿、24 小时内
        ArgumentCaptor<ArticleArgs> captor = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(articleService).count(captor.capture());
        Map<String, Object> q = captor.getValue().getQueryMap();
        assertEquals(MEMBER, q.get("EQ_userId_Long"));
        assertEquals(SITE, q.get("EQ_siteId_Long"));
        assertEquals(INPUT_TYPE_CONTRIBUTE, q.get("EQ_inputType_Short"));
        assertTrue(q.containsKey("GE_created_DateTime"));

        // 0 为不限
        site.getContribute().setDailyLimit(0);
        service.submit(site, member, channel, content("北京"));
        verify(articleService).insert(any(Article.class), anyLong(), anyList());
    }

    @Test
    void tagsMustAlreadyExistOnTheSiteAndAreTrimmedAndDeduplicated() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);
        Map<String, Object> customs = Map.of("city", "c");
        Content unknown = new Content("t", null, null, null, "<p>x</p>", List.of("t1", "新词"), customs);

        Http400Exception e = assertThrows(Http400Exception.class, () -> service.submit(site, member, channel, unknown));
        assertEquals("error.contribute.tagUnknown", e.getCode());
        verify(articleService, never()).insert(any(Article.class), anyLong(), anyList());

        Content known = new Content("t", null, null, null, "<p>x</p>", List.of(" t1 ", "t1", " "), customs);
        service.submit(site, member, channel, known);
        verify(articleService).insert(any(Article.class), anyLong(), org.mockito.ArgumentMatchers.eq(List.of("t1")));
    }

    @Test
    void customValuesMustBeScalarsOrShortScalarListsWithinTheLengthCap() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);
        Map<String, Object> nested = new HashMap<>();
        nested.put("city", Map.of("k", "v"));
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.submit(site, member, channel,
                new Content("t", null, null, null, "<p>x</p>", null, nested)));
        assertEquals("error.contribute.fieldInvalid", e.getCode());

        Map<String, Object> tooLong = new HashMap<>();
        tooLong.put("city", "x".repeat(ContributeService.CUSTOM_VALUE_MAX_LENGTH + 1));
        e = assertThrows(Http400Exception.class, () -> service.submit(site, member, channel,
                new Content("t", null, null, null, "<p>x</p>", null, tooLong)));
        assertEquals("error.contribute.fieldInvalid", e.getCode());

        assertTrue(ContributeService.isAcceptableValue(List.of("a", 1, true)));
        assertFalse(ContributeService.isAcceptableValue(List.of(List.of("a"))));
        assertFalse(ContributeService.isAcceptableValue(java.util.Collections.nCopies(
                ContributeService.CUSTOM_LIST_MAX_SIZE + 1, "a")));
        verify(articleService, never()).insert(any(Article.class), anyLong(), anyList());
    }

    @Test
    void submitRefusesAMissingRequiredCustomField() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);

        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.submit(site, member, channel, content(" ")));

        assertEquals("error.contribute.fieldRequired", e.getCode());
        verify(articleService, never()).insert(any(Article.class), anyLong(), anyList());
    }

    @Test
    void coverMustBeTheMembersOwnImageAttachment() {
        Channel channel = channel(true, Channel.TYPE_NORMAL);
        Content content = new Content("t", null, null, "/uploads/a.png", "<p>x</p>", null, Map.of("city", "c"));
        Attachment foreign = new Attachment();
        foreign.setUserId(99L);
        foreign.setPath("/uploads/a.png");
        when(attachmentService.findByUrl("/uploads/a.png")).thenReturn(foreign);

        Http400Exception e = assertThrows(Http400Exception.class, () -> service.submit(site, member, channel, content));
        assertEquals("error.contribute.imageInvalid", e.getCode());

        foreign.setUserId(MEMBER);
        assertEquals("/uploads/a.png", service.submit(site, member, channel, content).getImage());
    }

    // ----- 修改、删除的归属与状态守卫 -----

    private Article own(short status) {
        Article article = new Article();
        article.setId(100L);
        article.setSiteId(SITE);
        article.setUserId(MEMBER);
        article.setInputType(INPUT_TYPE_CONTRIBUTE);
        article.setStatus(status);
        article.setChannelId(CHANNEL);
        article.setChannel(channel(true, Channel.TYPE_NORMAL));
        when(articleService.select(100L)).thenReturn(article);
        return article;
    }

    @Test
    void getOwnHidesArticlesThatAreNotTheMembersContributionsOnThisSite() {
        Article article = own(STATUS_REJECTED);
        assertEquals(article, service.getOwn(site, member, 100L));

        article.setUserId(8L);
        assertNull(service.getOwn(site, member, 100L));
        article.setUserId(MEMBER);
        article.setInputType(INPUT_TYPE_NORMAL);
        assertNull(service.getOwn(site, member, 100L));
        article.setInputType(INPUT_TYPE_CONTRIBUTE);
        article.setSiteId(2L);
        assertNull(service.getOwn(site, member, 100L));
        assertThrows(Http404Exception.class, () -> service.update(site, member, 100L, content("c")));
    }

    @Test
    void updateResubmitsARejectedContributionAndRefusesPublishedOnes() {
        Article article = own(STATUS_REJECTED);

        service.update(site, member, 100L, content("上海"));

        assertEquals("上海", article.getCustoms().get("city"));
        verify(articleService).update(article, MEMBER, List.of("t1"));
        verify(articleService).submitForReview(article, MEMBER);

        article.setStatus(STATUS_PUBLISHED);
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.update(site, member, 100L, content("c")));
        assertEquals("error.contribute.notEditable", e.getCode());
    }

    @Test
    void updateLocksTheRowFirstAndRefusesAChannelThatNoLongerAcceptsTheMember() {
        Article article = own(STATUS_REJECTED);
        // 栏目关掉了投稿：重提 403，什么都不写
        article.getChannel().setAllowContribute(false);
        Http403Exception e = assertThrows(Http403Exception.class,
                () -> service.update(site, member, 100L, content("c")));
        assertEquals("error.contribute.channelNotAllowed", e.getCode());
        verify(articleService, never()).update(any(Article.class), anyLong(), anyList());

        // 锁在读之前（D8）
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(articleService);
        order.verify(articleService).lockArticle(100L);
        order.verify(articleService).select(100L);
    }

    @Test
    void pendingContributionIsEditableOnlyWhileNoProcessInstanceRuns() {
        Article article = own(STATUS_PENDING);
        assertTrue(ContributeService.isEditable(article));
        article.setProcessInstanceId("p1");
        assertFalse(ContributeService.isEditable(article));

        Http400Exception e = assertThrows(Http400Exception.class, () -> service.delete(site, member, 100L));
        assertEquals("error.contribute.notDeletable", e.getCode());
        verify(articleService, never()).delete(any(Article.class), any(User.class));

        // 删除 = 进回收站（行留着让配额算得住），从不彻底删除
        article.setProcessInstanceId(null);
        service.delete(site, member, 100L);
        verify(articleService).delete(article, member);
        verify(articleService, never()).completelyDelete(any(Article.class), any(User.class));
    }

    @Test
    void recycledContributionsAreInvisibleToTheMember() {
        assertNull(service.getOwn(site, member, 100L));

        service.myList(site, member, null, 0, 10);
        ArgumentCaptor<ArticleArgs> captor = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(articleService).selectList(captor.capture(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(10));
        assertEquals(List.of(STATUS_DELETED), captor.getValue().getQueryMap().get("NotIn_status_Short"));
    }
}

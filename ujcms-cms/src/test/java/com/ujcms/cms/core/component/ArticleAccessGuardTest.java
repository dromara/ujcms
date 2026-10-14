package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 前台读取文章的访问判定（docs/spec/article-management/plan.md Phase 2）。
 * <p>
 * 正常访问：未公开的文章答 404（与不存在相同），公开的再看会员组的栏目浏览权限。
 * 预览：须能进入文章所在站点，且文章在数据权限之内或持有其审核任务——以前只查文章权限，“全部文章权限 + 数据范围自身”
 * 的编辑可以凭 ID 预览同事的草稿，别的站点的也行。
 */
class ArticleAccessGuardTest {
    private static final long SITE = 1L;
    private static final long CHANNEL = 5L;
    private static final long SITE_ORG = 10L;

    private GroupService groupService;
    private OrgService orgService;
    private ArticleService articleService;
    private ArticleDataPermission dataPermission;
    private ArticleAccessGuard guard;
    private Group anonymous;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        orgService = mock(OrgService.class);
        articleService = mock(ArticleService.class);
        dataPermission = mock(ArticleDataPermission.class);
        guard = new ArticleAccessGuard(groupService, orgService, articleService, dataPermission);
        anonymous = new Group();
        anonymous.setId(100L);
        when(groupService.getAnonymous()).thenReturn(anonymous);
    }

    private static Article article(short status) {
        Site site = new Site();
        site.setId(SITE);
        site.setOrgId(SITE_ORG);
        Article article = new Article();
        article.setId(1L);
        article.setSiteId(SITE);
        article.setSite(site);
        article.setChannelId(CHANNEL);
        article.setStatus(status);
        return article;
    }

    private static User member(long groupId, boolean allAccess) {
        Group group = new Group();
        group.setId(groupId);
        group.setAllAccessPermission(allAccess);
        User user = new User();
        user.setId(7L);
        user.setOrgId(20L);
        user.setGroup(group);
        return user;
    }

    // region 正常访问

    @Test
    void unpublishedArticlesAnswer404LikeMissingOnes() {
        for (short status : new short[]{Article.STATUS_DRAFT, Article.STATUS_PENDING, Article.STATUS_REVIEWING,
                Article.STATUS_READY, Article.STATUS_OFFLINE, Article.STATUS_DELETED, Article.STATUS_REJECTED}) {
            Article article = article(status);
            assertThrows(Http404Exception.class, () -> guard.check(article, null, false), "status " + status);
            assertFalse(guard.isReadable(article, null));
        }
    }

    @Test
    void publishedAndArchivedAreReadableWhenTheGroupHasAccess() {
        anonymous.setAllAccessPermission(true);
        assertDoesNotThrow(() -> guard.check(article(Article.STATUS_PUBLISHED), null, false));
        assertDoesNotThrow(() -> guard.check(article(Article.STATUS_ARCHIVED), null, false));
        assertTrue(guard.isReadable(article(Article.STATUS_PUBLISHED), null));
    }

    @Test
    void anAnonymousVisitorWithoutChannelAccessGets401AndIsNotReadable() {
        anonymous.setAllAccessPermission(false);
        when(groupService.listAccessPermissions(100L, SITE)).thenReturn(List.of(99L));
        Article article = article(Article.STATUS_PUBLISHED);

        assertThrows(Http401Exception.class, () -> guard.check(article, null, false));
        assertFalse(guard.isReadable(article, null));
    }

    @Test
    void aMemberIsJudgedByTheirOwnGroup() {
        Article article = article(Article.STATUS_PUBLISHED);
        User denied = member(200L, false);
        when(groupService.listAccessPermissions(200L, SITE)).thenReturn(List.of());
        assertThrows(Http403Exception.class, () -> guard.check(article, denied, false));

        User granted = member(201L, false);
        when(groupService.listAccessPermissions(201L, SITE)).thenReturn(List.of(CHANNEL));
        assertDoesNotThrow(() -> guard.check(article, granted, false));
        assertTrue(guard.isReadable(article, granted));
    }

    // endregion

    // region 预览

    @Test
    void previewNeedsALogin() {
        assertThrows(Http401Exception.class, () -> guard.check(article(Article.STATUS_DRAFT), null, true));
    }

    @Test
    void previewIsRefusedOutsideTheOperatorsSites() {
        Article article = article(Article.STATUS_DRAFT);
        User user = member(200L, true);
        when(orgService.hasRelationship(20L, SITE_ORG)).thenReturn(false);
        // 即使文章权限、数据范围都放行：进不了这个站点的人不能预览它的文章
        when(dataPermission.permits(user, article)).thenReturn(true);

        assertThrows(Http403Exception.class, () -> guard.check(article, user, true));
    }

    @Test
    void previewFollowsTheDataPermissionNotJustTheChannelGrant() {
        Article article = article(Article.STATUS_DRAFT);
        User user = member(200L, true);
        when(orgService.hasRelationship(20L, SITE_ORG)).thenReturn(true);

        // 文章在数据权限之外（如数据范围“自身”，文章是同事的），也没有审核任务：拒绝
        when(dataPermission.permits(user, article)).thenReturn(false);
        when(articleService.isReviewer(article, user)).thenReturn(false);
        assertThrows(Http403Exception.class, () -> guard.check(article, user, true));

        // 数据权限之内：放行，状态不限（预览看的正是未公开的文章）
        when(dataPermission.permits(user, article)).thenReturn(true);
        assertDoesNotThrow(() -> guard.check(article, user, true));
    }

    @Test
    void aReviewerPreviewsTheArticleOfTheirTask() {
        Article article = article(Article.STATUS_REVIEWING);
        User user = member(200L, true);
        when(orgService.hasRelationship(20L, SITE_ORG)).thenReturn(true);
        when(dataPermission.permits(user, article)).thenReturn(false);
        when(articleService.isReviewer(article, user)).thenReturn(true);

        assertDoesNotThrow(() -> guard.check(article, user, true));
    }

    // endregion
}

package com.ujcms.cms.core.component;

import com.ujcms.cms.core.component.ArticleDataPermission.Scope;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.common.web.exception.Http403Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章数据权限的判定规则（docs/spec/article-data-permission/plan.md）：文章权限按栏目、数据范围按组织或本人、
 * 状态权限只管保存；权限集合取自控制台所读的同一份数据，无任何授权时一概拒绝。
 */
class ArticleDataPermissionTest {
    private static final long SITE = 1L;
    private static final long USER = 50L;
    private static final long OWN_ORG = 10L;
    private static final long SECOND_ORG = 11L;
    private static final long GRANTED_ORG = 20L;
    private static final long OTHER_ORG = 30L;
    private static final long C1 = 101L;
    private static final long C2 = 102L;
    private static final long C3 = 103L;

    private ChannelService channelService;
    private OrgService orgService;
    private ArticleDataPermission permission;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        orgService = mock(OrgService.class);
        permission = new ArticleDataPermission(channelService, orgService);
        when(channelService.listArticlePermissions(anyCollection(), anyCollection(), eq(SITE)))
                .thenReturn(List.of(C1, C2));
        when(orgService.listPermissions(anyCollection(), anyCollection())).thenReturn(List.of(GRANTED_ORG));
    }

    private static Role role(long id, boolean allArticle, short dataScope, boolean allStatus) {
        Role role = new Role();
        role.setId(id);
        role.setAllArticlePermission(allArticle);
        role.setDataScope(dataScope);
        role.setAllStatusPermission(allStatus);
        return role;
    }

    private static User user(long id, Role... roles) {
        User user = new User();
        user.setId(id);
        user.setOrgId(OWN_ORG);
        Org second = new Org();
        second.setId(SECOND_ORG);
        user.setOrgList(List.of(second));
        user.setRoleList(List.of(roles));
        return user;
    }

    private static Article article(long id, long channelId, long orgId, long userId) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(SITE);
        article.setChannelId(channelId);
        article.setOrgId(orgId);
        article.setUserId(userId);
        return article;
    }

    // ---------------------------------------------------------------- 不受限的操作员

    @Test
    void rootAndUnrestrictedRolesNeverQuery() {
        // 超级管理员没有角色也不受限
        Scope root = permission.scopeOf(user(1L), SITE);
        assertNull(root.channelIds());
        assertNull(root.orgIds());
        assertTrue(root.permits(article(1L, C3, OTHER_ORG, 999L)));

        Scope all = permission.scopeOf(user(USER, role(5L, true, Role.DATA_SCOPE_ALL, false)), SITE);
        assertTrue(all.permits(article(1L, C3, OTHER_ORG, 999L)));
        assertTrue(all.permitsOrg(OTHER_ORG));

        verify(channelService, never()).listArticlePermissions(anyCollection(), anyCollection(), anyLong());
        verify(orgService, never()).listPermissions(anyCollection(), anyCollection());
    }

    // ---------------------------------------------------------------- 文章权限

    @Test
    void limitedArticlePermissionReadsTheChannelListOnceAndJudgesByChannel() {
        User user = user(USER, role(5L, false, Role.DATA_SCOPE_ALL, false));

        Scope scope = permission.scopeOf(user, SITE);

        assertEquals(Set.of(C1, C2), scope.channelIds());
        assertTrue(scope.permits(article(1L, C1, OTHER_ORG, 999L)));
        assertFalse(scope.permits(article(2L, C3, OWN_ORG, USER)));
        // 一次请求解析一次，逐篇判定不再查询
        verify(channelService, times(1)).listArticlePermissions(eq(List.of(5L)), eq(Set.of(OWN_ORG, SECOND_ORG)),
                eq(SITE));
    }

    @Test
    void anyRoleWithAllArticlePermissionLiftsTheLimit() {
        User user = user(USER, role(5L, false, Role.DATA_SCOPE_ALL, false), role(6L, true, Role.DATA_SCOPE_ALL, false));
        assertNull(permission.scopeOf(user, SITE).channelIds());
    }

    @Test
    void noRoleAndNoOrgPermitsNothing() {
        // 真实的 User 总带有所属组织；这里构造两边都为空的情形：查询不带条件会返回全部数据，必须不查、一概拒绝
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER);
        when(user.fetchRoleIds()).thenReturn(List.of());
        when(user.fetchAllOrgIds()).thenReturn(Set.of());
        when(user.hasAllArticlePermission()).thenReturn(false);
        when(user.getDataScope()).thenReturn(Role.DATA_SCOPE_PERM_ORG);

        Scope scope = permission.scopeOf(user, SITE);

        assertEquals(Set.of(), scope.channelIds());
        assertEquals(Set.of(), scope.orgIds());
        assertFalse(scope.permits(article(1L, C1, OWN_ORG, USER)));
        verify(channelService, never()).listArticlePermissions(anyCollection(), anyCollection(), anyLong());
        verify(orgService, never()).listPermissions(anyCollection(), anyCollection());

        ArticleArgs args = ArticleArgs.of();
        permission.filter(args, user);
        // 列表：空的栏目祖先集合令查询不返回任何数据
        assertNotNull(args.getChannelAncestorIds());
        assertTrue(args.getChannelAncestorIds().isEmpty());
    }

    // ---------------------------------------------------------------- 数据范围

    @Test
    void dataScopeOrgCoversTheOperatorsOwnOrgsOnly() {
        Scope scope = permission.scopeOf(user(USER, role(5L, true, Role.DATA_SCOPE_ORG, false)), SITE);

        assertEquals(Set.of(OWN_ORG, SECOND_ORG), scope.orgIds());
        assertTrue(scope.permits(article(1L, C3, SECOND_ORG, 999L)));
        assertFalse(scope.permits(article(2L, C3, GRANTED_ORG, USER)));
        verify(orgService, never()).listPermissions(anyCollection(), anyCollection());
    }

    @Test
    void dataScopePermOrgAddsGrantedOrgsToTheOwnOnes() {
        Scope scope = permission.scopeOf(user(USER, role(5L, true, Role.DATA_SCOPE_PERM_ORG, false)), SITE);

        // 与文章列表的条件一致：本人的组织 + 获授权的组织
        assertEquals(Set.of(OWN_ORG, SECOND_ORG, GRANTED_ORG), scope.orgIds());
        assertTrue(scope.permits(article(1L, C3, OWN_ORG, 999L)));
        assertTrue(scope.permits(article(2L, C3, GRANTED_ORG, 999L)));
        assertFalse(scope.permits(article(3L, C3, OTHER_ORG, USER)));
    }

    @Test
    void dataScopeSelfJudgesTheCreatorButLimitsNewOrgsToOwnOrgs() {
        Scope scope = permission.scopeOf(user(USER, role(5L, true, Role.DATA_SCOPE_SELF, false)), SITE);

        // 自己创建的文章，哪怕已被划到别的组织
        assertTrue(scope.permits(article(1L, C3, OTHER_ORG, USER)));
        assertFalse(scope.permits(article(2L, C3, OWN_ORG, 999L)));
        // 新的归属组织只能选本人的组织
        assertTrue(scope.permitsOrg(OWN_ORG));
        assertFalse(scope.permitsOrg(OTHER_ORG));
    }

    @Test
    void theWidestDataScopeAmongRolesWins() {
        User user = user(USER, role(5L, true, Role.DATA_SCOPE_SELF, false), role(6L, true, Role.DATA_SCOPE_ORG, false));
        Scope scope = permission.scopeOf(user, SITE);
        assertEquals(Role.DATA_SCOPE_ORG, scope.dataScope());
        assertTrue(scope.permits(article(1L, C3, OWN_ORG, 999L)));
    }

    // ---------------------------------------------------------------- 拒绝

    @Test
    void dataScopeOrgIdsIsTheOrgSetSharedWithFormReview() {
        // 数据范围是角色上的设置，表单审核取同一个组织集合（plan.md D10）
        assertNull(permission.dataScopeOrgIds(user(USER, role(1, false, Role.DATA_SCOPE_ALL, false))));
        assertEquals(Set.of(OWN_ORG, SECOND_ORG),
                permission.dataScopeOrgIds(user(USER, role(1, false, Role.DATA_SCOPE_ORG, false))));
        assertEquals(Set.of(OWN_ORG, SECOND_ORG),
                permission.dataScopeOrgIds(user(USER, role(1, false, Role.DATA_SCOPE_SELF, false))));
        verify(orgService, never()).listPermissions(anyCollection(), anyCollection());
        assertEquals(Set.of(OWN_ORG, SECOND_ORG, GRANTED_ORG),
                permission.dataScopeOrgIds(user(USER, role(1, false, Role.DATA_SCOPE_PERM_ORG, false))));
    }

    @Test
    void dataScopeOfJudgesOwnershipOnlyAndNeverReadsArticlePermissions() {
        // 表单没有栏目这一维度（plan.md D12）：文章权限受限的操作员，按数据范围判定时不带栏目条件
        Scope scope = permission.dataScopeOf(user(USER, role(5L, false, Role.DATA_SCOPE_SELF, false)));
        assertNull(scope.channelIds());
        assertTrue(scope.permitsOwner(OTHER_ORG, USER));
        assertFalse(scope.permitsOwner(OWN_ORG, 999L));
        verify(channelService, never()).listArticlePermissions(anyCollection(), anyCollection(), anyLong());
    }

    @Test
    void requireArticlesRejectsTheWholeBatchOnAnyOffender() {
        Scope scope = permission.scopeOf(user(USER, role(5L, false, Role.DATA_SCOPE_SELF, false)), SITE);
        Article mine = article(1L, C1, OWN_ORG, USER);
        Article othersInMyChannel = article(2L, C1, OWN_ORG, 999L);
        Article mineInForeignChannel = article(3L, C3, OWN_ORG, USER);

        assertDoesNotThrow(() -> scope.requireArticles(List.of()));
        assertDoesNotThrow(() -> scope.requireArticles(List.of(mine)));
        Http403Exception e = assertThrows(Http403Exception.class,
                () -> scope.requireArticles(List.of(mine, othersInMyChannel)));
        assertEquals("error.article.forbidden", e.getCode());
        assertThrows(Http403Exception.class, () -> scope.requireArticle(mineInForeignChannel));
    }

    @Test
    void requireChannelsAndRequireOrgNameTheirOwnErrors() {
        Scope scope = permission.scopeOf(user(USER, role(5L, false, Role.DATA_SCOPE_ORG, false)), SITE);

        assertDoesNotThrow(() -> scope.requireChannels(List.of(C1, C2)));
        assertEquals("error.article.channel.forbidden",
                assertThrows(Http403Exception.class, () -> scope.requireChannels(List.of(C1, C3))).getCode());
        assertDoesNotThrow(() -> scope.requireOrg(SECOND_ORG));
        assertEquals("error.article.org.forbidden",
                assertThrows(Http403Exception.class, () -> scope.requireOrg(OTHER_ORG)).getCode());
    }

    // ---------------------------------------------------------------- 状态权限

    private static Article articleWithStatus(String processKey, short status) {
        Article article = article(1L, C1, OWN_ORG, USER);
        Channel channel = new Channel();
        channel.setProcessKey(processKey);
        article.setChannel(channel);
        article.setStatus(status);
        return article;
    }

    @Test
    void requireEditableAppliesOnlyToNonEditableArticles() {
        User plain = user(USER, role(5L, true, Role.DATA_SCOPE_ALL, false));
        AtomicInteger reviewerCalls = new AtomicInteger();
        // 栏目没有审核流程，或状态可编辑：放行，且不去查审核任务
        assertDoesNotThrow(() -> permission.requireEditable(plain,
                articleWithStatus(null, Article.STATUS_PUBLISHED), () -> reviewerCalls.incrementAndGet() > 0));
        assertDoesNotThrow(() -> permission.requireEditable(plain,
                articleWithStatus("flow", Article.STATUS_DRAFT), () -> reviewerCalls.incrementAndGet() > 0));
        assertDoesNotThrow(() -> permission.requireEditable(plain,
                articleWithStatus("flow", Article.STATUS_PENDING), () -> reviewerCalls.incrementAndGet() > 0));
        assertEquals(0, reviewerCalls.get());

        // 有审核流程且已发布：没有全部状态权限、也不是审核人，拒绝
        Article published = articleWithStatus("flow", Article.STATUS_PUBLISHED);
        Http403Exception e = assertThrows(Http403Exception.class,
                () -> permission.requireEditable(plain, published, () -> false));
        assertEquals("error.article.status.forbidden", e.getCode());
        // 持有审核任务的审核人放行
        assertDoesNotThrow(() -> permission.requireEditable(plain,
                articleWithStatus("flow", Article.STATUS_REVIEWING), () -> true));
    }

    @Test
    void allStatusPermissionSkipsTheReviewerLookup() {
        User editor = user(USER, role(5L, true, Role.DATA_SCOPE_ALL, true));
        AtomicInteger reviewerCalls = new AtomicInteger();
        assertDoesNotThrow(() -> permission.requireEditable(editor,
                articleWithStatus("flow", Article.STATUS_ARCHIVED), () -> reviewerCalls.incrementAndGet() > 0));
        assertEquals(0, reviewerCalls.get());
    }

    // ---------------------------------------------------------------- 列表条件

    @Test
    void filterSetsTheListConditionsForEachDataScope() {
        ArticleArgs permOrg = ArticleArgs.of();
        permission.filter(permOrg, user(USER, role(5L, false, Role.DATA_SCOPE_PERM_ORG, false)));
        assertEquals(List.of(5L), permOrg.getOrgRoleIds());
        assertEquals(Set.of(OWN_ORG, SECOND_ORG), permOrg.getOrgPermIds());
        assertEquals(List.of(5L), permOrg.getArticleRoleIds());
        assertEquals(Set.of(OWN_ORG, SECOND_ORG), permOrg.getArticleOrgIds());

        ArticleArgs org = ArticleArgs.of();
        permission.filter(org, user(USER, role(5L, true, Role.DATA_SCOPE_ORG, false)));
        assertEquals(Set.of(OWN_ORG, SECOND_ORG), org.getOrgIds());
        assertNull(org.getArticleRoleIds());

        ArticleArgs self = ArticleArgs.of();
        permission.filter(self, user(USER, role(5L, true, Role.DATA_SCOPE_SELF, false)));
        assertEquals(USER, self.getQueryMap().get("EQ_userId_Long"));

        ArticleArgs all = ArticleArgs.of();
        permission.filter(all, user(USER, role(5L, true, Role.DATA_SCOPE_ALL, false)));
        assertNull(all.getOrgIds());
        assertNull(all.getOrgRoleIds());
        assertNull(all.getArticleRoleIds());
        assertTrue(all.getQueryMap().isEmpty());
        verify(channelService, never()).listArticlePermissions(any(), any(), anyLong());
    }
}

package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 前台读取一篇文章的访问判定（docs/spec/article-management/plan.md Phase 2）：公共接口、前台页面、附件下载、
 * 浏览与顶踩计数共用这一处，不再各判各的。
 * <p>
 * 正常访问：文章须已发布或已归档，且访问者的会员组有该栏目的浏览权限。未公开的文章与不存在的文章回答相同（404），
 * 不透露“有这么一篇，只是你看不了”。
 * <p>
 * 预览（控制台的“前台预览”）：操作员须能进入文章所在的站点，且文章在他的文章权限与数据范围之内——与控制台读取
 * 单篇文章是同一条规则（docs/spec/article-data-permission/plan.md D2：列表里看不到的文章，也不能凭 ID 读取）；
 * 持有该文章审核任务的审核人同样放行（D6）。以前预览只查文章权限：数据范围为“自身”的编辑能凭 ID 预览同事的草稿。
 *
 * @author PONY
 */
@Component
public class ArticleAccessGuard {
    private final GroupService groupService;
    private final OrgService orgService;
    private final ArticleService articleService;
    private final ArticleDataPermission dataPermission;

    public ArticleAccessGuard(GroupService groupService, OrgService orgService, ArticleService articleService,
                              ArticleDataPermission dataPermission) {
        this.groupService = groupService;
        this.orgService = orgService;
        this.articleService = articleService;
        this.dataPermission = dataPermission;
    }

    /**
     * 访问判定，不通过即抛出异常
     *
     * @param user    当前用户，未登录为 {@code null}
     * @param preview 是否后台预览
     * @throws Http404Exception 文章未公开（非预览）
     * @throws Http401Exception 未登录：预览须登录；匿名会员组没有该栏目的浏览权限
     * @throws Http403Exception 已登录但没有权限
     */
    public void check(Article article, @Nullable User user, boolean preview) {
        if (preview) {
            checkPreview(article, user);
            return;
        }
        if (!article.isNormal()) {
            throw new Http404Exception(Article.NOT_FOUND + article.getId());
        }
        if (!hasGroupAccess(article, user)) {
            if (user == null) {
                throw new Http401Exception();
            }
            throw new Http403Exception("Article access forbidden. ID: " + article.getId());
        }
    }

    /**
     * 访问者能否在前台读到这篇文章：已发布或已归档，且会员组有该栏目的浏览权限。不抛异常——上一篇、下一篇、
     * 浏览计数等“读不到就当没有”的场合用它
     */
    public boolean isReadable(Article article, @Nullable User user) {
        return article.isNormal() && hasGroupAccess(article, user);
    }

    private boolean hasGroupAccess(Article article, @Nullable User user) {
        Group group = user != null ? user.getGroup() : groupService.getAnonymous();
        if (Boolean.TRUE.equals(group.getAllAccessPermission())) {
            return true;
        }
        List<Long> channelIds = groupService.listAccessPermissions(group.getId(), article.getSiteId());
        return channelIds.contains(article.getChannelId());
    }

    private void checkPreview(Article article, @Nullable User user) {
        if (user == null) {
            throw new Http401Exception();
        }
        // 能进入文章所在的站点（与后台按站点头的校验一致），且文章在其数据权限之内或持有其审核任务
        boolean inSite = orgService.hasRelationship(user.getOrgId(), article.getSite().getOrgId());
        if (!inSite || !(dataPermission.permits(user, article) || articleService.isReviewer(article, user))) {
            throw new Http403Exception("No preview permission. ID: " + article.getId());
        }
    }
}

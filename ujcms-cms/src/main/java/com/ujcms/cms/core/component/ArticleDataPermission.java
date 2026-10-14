package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.common.web.exception.Http403Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 文章的数据权限（docs/spec/article-data-permission/plan.md）：文章权限（按栏目）、数据范围（按组织或本人）、状态权限。
 * <p>
 * 控制台据这些权限过滤文章列表、收窄栏目与组织选择器、把表单置为只读；这里用同一份数据在服务端拒绝，两边不会各说各话。
 * 文章列表的过滤条件也由本类给出，与单篇文章的判定同源。
 * <p>
 * 只用于操作员经后台接口读写文章；定时任务、种子导入、采集线程等系统路径不是“操作员在编辑文章”，不调用本类。
 *
 * @author PONY
 */
@Component
public class ArticleDataPermission {
    private final ChannelService channelService;
    private final OrgService orgService;

    public ArticleDataPermission(ChannelService channelService, OrgService orgService) {
        this.channelService = channelService;
        this.orgService = orgService;
    }

    /**
     * 操作员在某站点的文章数据权限。一次请求解析一次，再逐篇判定
     *
     * @param userId     操作员ID
     * @param dataScope  数据范围，见 {@link Role#DATA_SCOPE_ALL} 等
     * @param channelIds 文章权限之内的栏目。{@code null} 表示全部文章权限
     * @param orgIds     可归属的组织：“所属组织”“自身”为本人的各组织，“有权限的组织”另加获授权的组织。{@code null} 表示数据范围为全部
     */
    public record Scope(Long userId, short dataScope, @Nullable Set<Long> channelIds, @Nullable Set<Long> orgIds) {
        /**
         * 栏目是否在文章权限之内
         */
        public boolean permitsChannel(Long channelId) {
            return channelIds == null || channelIds.contains(channelId);
        }

        /**
         * 文章可否归属到该组织（新增、改换组织时）
         */
        public boolean permitsOrg(Long orgId) {
            return orgIds == null || orgIds.contains(orgId);
        }

        /**
         * 数据范围是否覆盖该归属：“自身”看创建人，其余看组织
         */
        public boolean permitsOwner(Long orgId, Long ownerId) {
            if (dataScope == Role.DATA_SCOPE_SELF) {
                return userId.equals(ownerId);
            }
            return permitsOrg(orgId);
        }

        /**
         * 文章是否在文章权限与数据范围之内，即是否会出现在该操作员的文章列表里
         */
        public boolean permits(Article article) {
            return permitsChannel(article.getChannelId()) && permitsOwner(article.getOrgId(), article.getUserId());
        }

        /**
         * 文章须在文章权限与数据范围之内
         */
        public void requireArticle(Article article) {
            if (!permits(article)) {
                throw new Http403Exception("error.article.forbidden", String.valueOf(article.getId()));
            }
        }

        /**
         * 一批文章须都在文章权限与数据范围之内。先整体校验、再动手写入：有一篇不在范围内，整批都不执行
         */
        public void requireArticles(Collection<Article> articles) {
            articles.forEach(this::requireArticle);
        }

        /**
         * 文章要放入的栏目（新增、改换栏目、新加的扩展栏目、推送目标等）须在文章权限之内
         */
        public void requireChannels(Collection<Long> channelIds) {
            for (Long channelId : channelIds) {
                if (!permitsChannel(channelId)) {
                    throw new Http403Exception("error.article.channel.forbidden", String.valueOf(channelId));
                }
            }
        }

        /**
         * 文章新的归属组织（新增、改换组织）须在可归属的组织之内
         */
        public void requireOrg(Long orgId) {
            if (!permitsOrg(orgId)) {
                throw new Http403Exception("error.article.org.forbidden", String.valueOf(orgId));
            }
        }
    }

    /**
     * 解析操作员在某站点的文章数据权限。文章权限按站点查询（与栏目选择器同一查询），组织与站点无关
     */
    public Scope scopeOf(User user, Long siteId) {
        Collection<Long> roleIds = user.fetchRoleIds();
        Set<Long> ownOrgIds = user.fetchAllOrgIds();
        // 既无角色也无组织时，查询不带条件、会返回全部数据：此时应视为没有任何权限
        boolean noGrant = roleIds.isEmpty() && ownOrgIds.isEmpty();
        Set<Long> channelIds = null;
        if (!user.hasAllArticlePermission()) {
            channelIds = noGrant ? Set.of()
                    : new HashSet<>(channelService.listArticlePermissions(roleIds, ownOrgIds, siteId));
        }
        return new Scope(user.getId(), user.getDataScope(), channelIds, dataScopeOrgIds(user));
    }

    /**
     * 只按数据范围（归属）解析，不含文章权限：表单这类没有栏目维度的数据，用它的 {@link Scope#permitsOwner} 判定
     */
    public Scope dataScopeOf(User user) {
        return new Scope(user.getId(), user.getDataScope(), null, dataScopeOrgIds(user));
    }

    /**
     * 数据范围之内的组织：“所属组织”“自身”为本人的各组织，“有权限的组织”另加角色、组织获授权的组织（与列表的查询条件一致）。
     * 数据范围是角色上的设置，文章与表单共用：表单审核也由此取组织集合。
     *
     * @return 组织ID集合。{@code null} 表示数据范围为全部
     */
    @Nullable
    public Set<Long> dataScopeOrgIds(User user) {
        short dataScope = user.getDataScope();
        if (dataScope == Role.DATA_SCOPE_ALL) {
            return null;
        }
        Collection<Long> roleIds = user.fetchRoleIds();
        Set<Long> ownOrgIds = user.fetchAllOrgIds();
        Set<Long> orgIds = new HashSet<>(ownOrgIds);
        // 既无角色也无组织时查询不带条件、会返回全部组织：此时没有任何获授权的组织
        if (dataScope == Role.DATA_SCOPE_PERM_ORG && !(roleIds.isEmpty() && ownOrgIds.isEmpty())) {
            orgIds.addAll(orgService.listPermissions(roleIds, ownOrgIds));
        }
        return orgIds;
    }

    /**
     * 文章列表的数据权限条件。须在其它条件之后调用：无任何授权时借“栏目祖先为空集”令查询不返回数据
     */
    public void filter(ArticleArgs args, User user) {
        Collection<Long> roleIds = user.fetchRoleIds();
        Set<Long> orgIds = user.fetchAllOrgIds();
        // 数据范围
        switch (user.getDataScope()) {
            case Role.DATA_SCOPE_PERM_ORG:
                args.orgPermission(roleIds, orgIds);
                break;
            case Role.DATA_SCOPE_ORG:
                args.orgIds(orgIds);
                break;
            case Role.DATA_SCOPE_SELF:
                args.userId(user.getId());
                break;
            default:
        }
        // 文章权限
        if (!user.hasAllArticlePermission()) {
            if (roleIds.isEmpty() && orgIds.isEmpty()) {
                args.channelAncestorIds(Collections.emptyList());
            } else {
                args.articlePermission(roleIds, orgIds);
            }
        }
    }

    /**
     * 栏目选择器的文章权限条件：只列出文章权限之内的栏目
     */
    public void filter(ChannelArgs args, User user) {
        if (!user.hasAllArticlePermission()) {
            args.articlePermission(user.fetchRoleIds(), user.fetchAllOrgIds());
        }
    }

    /**
     * 文章须在操作员的文章权限与数据范围之内。一次请求里要判定多篇文章时，先 {@link #scopeOf} 再逐篇判定
     */
    public void requireArticle(User user, Article article) {
        scopeOf(user, article.getSiteId()).requireArticle(article);
    }

    /**
     * 文章是否在操作员的文章权限与数据范围之内
     */
    public boolean permits(User user, Article article) {
        return scopeOf(user, article.getSiteId()).permits(article);
    }

    /**
     * 状态权限：不可编辑状态的文章（所在栏目有审核流程，且已发布、已归档、待发布或审核中）只有具备“全部状态权限”者、
     * 或持有该文章审核任务的审核人可以保存
     *
     * @param reviewer 操作员是否持有该文章的审核任务。只在前两项都不满足时才求值
     */
    public void requireEditable(User user, Article article, BooleanSupplier reviewer) {
        if (article.isEditable() || user.hasAllStatusPermission() || reviewer.getAsBoolean()) {
            return;
        }
        throw new Http403Exception("error.article.status.forbidden", String.valueOf(article.getId()));
    }
}

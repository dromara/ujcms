package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 文章查询参数
 *
 * @author PONY
 */
public class ArticleArgs extends BaseQueryArgs {
    @Nullable
    private Map<String, String> customsQueryMap;
    @Nullable
    private Collection<Long> channelAncestorIds;
    @Nullable
    private Collection<Long> orgIds;
    @Nullable
    private Collection<Long> orgRoleIds;
    @Nullable
    private Collection<Long> orgPermIds;
    @Nullable
    private Collection<Long> articleRoleIds;
    @Nullable
    private Collection<Long> articleOrgIds;

    public ArticleArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public ArticleArgs channelAncestorId(@Nullable Long channelAncestorId) {
        if (channelAncestorId != null) {
            this.channelAncestorIds = Collections.singleton(channelAncestorId);
        }
        return this;
    }

    public ArticleArgs channelAncestorIds(@Nullable Collection<Long> channelAncestorIds) {
        if (channelAncestorIds != null) {
            this.channelAncestorIds = channelAncestorIds;
        }
        return this;
    }

    public ArticleArgs orgIds(@Nullable Collection<Long> orgIds) {
        if (orgIds != null) {
            this.orgIds = orgIds;
        }
        return this;
    }

    public ArticleArgs orgPermission(Collection<Long> orgRoleIds, Collection<Long> orgPermIds) {
        this.orgRoleIds = orgRoleIds;
        this.orgPermIds = orgPermIds;
        return this;
    }

    public ArticleArgs articlePermission(Collection<Long> articleRoleIds, Collection<Long> articleOrgIds) {
        this.articleRoleIds = articleRoleIds;
        this.articleOrgIds = articleOrgIds;
        return this;
    }

    public ArticleArgs channelId(@Nullable Long channelId) {
        if (channelId != null) {
            queryMap.put("EQ_channelId_Long", channelId);
        }
        return this;
    }

    public ArticleArgs inChannelIds(@Nullable Collection<Long> channelIds) {
        if (CollectionUtils.isNotEmpty(channelIds)) {
            queryMap.put("In_channelId_Long", channelIds);
        }
        return this;
    }

    public ArticleArgs siteAncestorId(@Nullable Long siteAncestorId) {
        if (siteAncestorId != null) {
            queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Long", siteAncestorId);
        }
        return this;
    }

    public ArticleArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public ArticleArgs tagId(@Nullable Long tagId) {
        if (tagId != null) {
            queryMap.put("EQ_article@ArticleTag-tagId_Long", tagId);
        }
        return this;
    }

    public ArticleArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public ArticleArgs minId(@Nullable Long minId) {
        if (minId != null) {
            queryMap.put("GT_id_Long", minId);
        }
        return this;
    }

    public ArticleArgs gePublishDate(@Nullable OffsetDateTime begin) {
        if (begin != null) {
            queryMap.put("GE_publishDate_DateTime", begin);
        }
        return this;
    }

    public ArticleArgs lePublishDate(@Nullable OffsetDateTime end) {
        if (end != null) {
            queryMap.put("LE_publishDate_DateTime", end);
        }
        return this;
    }

    public ArticleArgs isWithImage(@Nullable Boolean isWithImage) {
        if (isWithImage != null) {
            queryMap.put("EQ_withImage_Boolean", isWithImage);
        }
        return this;
    }

    public ArticleArgs q(@Nullable String q) {
        if (StringUtils.isNotBlank(q)) {
            queryMap.put("Contains_1_@articleExt-title", q);
            queryMap.put("Contains_1_@articleExt-text", q);
        }
        return this;
    }

    public ArticleArgs containsTitle(@Nullable String title) {
        if (StringUtils.isNotBlank(title)) {
            queryMap.put("Contains_@articleExt-title", title);
        }
        return this;
    }

    public ArticleArgs containText(@Nullable String text) {
        if (StringUtils.isNotBlank(text)) {
            queryMap.put("Contains_@articleExt-text", text);
        }
        return this;
    }

    public ArticleArgs geSticky(@Nullable Short sticky) {
        if (sticky != null) {
            queryMap.put("GE_sticky_Short", sticky);
        }
        return this;
    }

    public ArticleArgs leStickyDate(@Nullable OffsetDateTime stickyDate) {
        if (stickyDate != null) {
            queryMap.put("LE_stickyDate_DateTime", stickyDate);
        }
        return this;
    }

    public ArticleArgs leOnlineDateOrNull(@Nullable OffsetDateTime onlineDate) {
        if (onlineDate != null) {
            queryMap.put("LE_1_onlineDate_DateTime", onlineDate);
            queryMap.put("IsNull_1_onlineDate_DateTime", null);
        }
        return this;
    }

    public ArticleArgs leOfflineDate(@Nullable OffsetDateTime offlineDate) {
        if (offlineDate != null) {
            queryMap.put("LE_offlineDate_DateTime", offlineDate);
        }
        return this;
    }

    public ArticleArgs excludeIds(@Nullable Collection<Long> excludeIds) {
        if (CollectionUtils.isNotEmpty(excludeIds)) {
            queryMap.put("NotIn_id", excludeIds);
        }
        return this;
    }

    public ArticleArgs status(@Nullable Collection<Short> status) {
        if (CollectionUtils.isNotEmpty(status)) {
            queryMap.put("In_status_Short", status);
        }
        return this;
    }

    public ArticleArgs orderById() {
        queryMap.put("OrderBy", "id");
        return this;
    }

    public static ArticleArgs of() {
        return of(new HashMap<>(16));
    }

    public static ArticleArgs of(Map<String, Object> queryMap) {
        return new ArticleArgs(queryMap);
    }

    private ArticleArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    @Nullable
    public Map<String, String> getCustomsQueryMap() {
        return customsQueryMap;
    }

    @Nullable
    public Collection<Long> getChannelAncestorIds() {
        return channelAncestorIds;
    }

    @Nullable
    public Collection<Long> getOrgIds() {
        return orgIds;
    }

    public void setChannelAncestorIds(@Nullable Collection<Long> channelAncestorIds) {
        this.channelAncestorIds = channelAncestorIds;
    }

    @Nullable
    public Collection<Long> getOrgRoleIds() {
        return orgRoleIds;
    }

    @Nullable
    public Collection<Long> getOrgPermIds() {
        return orgPermIds;
    }

    @Nullable
    public Collection<Long> getArticleRoleIds() {
        return articleRoleIds;
    }

    @Nullable
    public Collection<Long> getArticleOrgIds() {
        return articleOrgIds;
    }
}

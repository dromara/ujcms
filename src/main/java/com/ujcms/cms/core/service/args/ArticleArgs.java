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
    private Integer orgAncestorId;
    @Nullable
    private Collection<Integer> channelAncestorIds;
    @Nullable
    private Collection<Integer> roleIds;

    public ArticleArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public ArticleArgs channelAncestorId(@Nullable Integer channelAncestorId) {
        if (channelAncestorId != null) {
            this.channelAncestorIds = Collections.singleton(channelAncestorId);
        }
        return this;
    }

    public ArticleArgs orgAncestorId(@Nullable Integer orgAncestorId) {
        if (orgAncestorId != null) {
            this.orgAncestorId = orgAncestorId;
        }
        return this;
    }

    public ArticleArgs channelAncestorIds(@Nullable Collection<Integer> channelAncestorIds) {
        if (channelAncestorIds != null) {
            this.channelAncestorIds = channelAncestorIds;
        }
        return this;
    }

    public ArticleArgs channelId(@Nullable Integer channelId) {
        if (channelId != null) {
            queryMap.put("EQ_channelId_Int", channelId);
        }
        return this;
    }

    public ArticleArgs inChannelIds(@Nullable Collection<Integer> channelIds) {
        if (CollectionUtils.isNotEmpty(channelIds)) {
            queryMap.put("In_channelId_Int", channelIds);
        }
        return this;
    }

    public ArticleArgs inRoleIds(@Nullable Collection<Integer> roleIds) {
        // 允许 roleIds.size() == 0，代表没有角色权限，将不会返回任何数据
        if (roleIds != null) {
            this.roleIds = roleIds;
        }
        return this;

    }

    public ArticleArgs siteAncestorId(@Nullable Integer siteAncestorId) {
        if (siteAncestorId != null) {
            queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Int", siteAncestorId);
        }
        return this;
    }

    public ArticleArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Int", siteId);
        }
        return this;
    }

    public ArticleArgs tagId(@Nullable Integer tagId) {
        if (tagId != null) {
            queryMap.put("EQ_article@ArticleTag-tagId_Int", tagId);
        }
        return this;
    }

    public ArticleArgs userId(@Nullable Integer userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Int", userId);
        }
        return this;
    }

    public ArticleArgs minId(@Nullable Integer minId) {
        if (minId != null) {
            queryMap.put("GT_id", minId);
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

    public ArticleArgs geOnlineDateOrNull(@Nullable OffsetDateTime onlineDate) {
        if (onlineDate != null) {
            queryMap.put("GE_1_stickyDate_DateTime", onlineDate);
            queryMap.put("IsNull_1_stickyDate_DateTime", null);
        }
        return this;
    }

    public ArticleArgs geOfflineDate(@Nullable OffsetDateTime offlineDate) {
        if (offlineDate != null) {
            queryMap.put("GE_offlineDate_DateTime", offlineDate);
        }
        return this;
    }

    public ArticleArgs excludeIds(@Nullable Collection<Integer> excludeIds) {
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
    public Collection<Integer> getChannelAncestorIds() {
        return channelAncestorIds;
    }

    public void setChannelAncestorIds(@Nullable Collection<Integer> channelAncestorIds) {
        this.channelAncestorIds = channelAncestorIds;
    }

    @Nullable
    public Integer getOrgAncestorId() {
        return orgAncestorId;
    }

    @Nullable
    public Collection<Integer> getRoleIds() {
        return roleIds;
    }
}

package com.ujcms.cms.core.service.args;

import com.ujcms.util.query.BaseQueryArgs;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;
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
    private Integer subChannelId;
    @Nullable
    private Integer subOrgId;

    public ArticleArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public ArticleArgs subChannelId(@Nullable Integer subChannelId) {
        if (subChannelId != null) {
            this.subChannelId = subChannelId;
        }
        return this;
    }

    public ArticleArgs subOrgId(@Nullable Integer subOrgId) {
        if (subOrgId != null) {
            this.subOrgId = subOrgId;
        }
        return this;
    }

    public ArticleArgs inSubChannelIds(@Nullable Collection<Integer> subChannelIds) {
        if (CollectionUtils.isNotEmpty(subChannelIds)) {
            queryMap.put("In_channel@ChannelTree@descendant-ancestorId_Int", subChannelIds);
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
        if (CollectionUtils.isNotEmpty(roleIds)) {
            queryMap.put("In_channel-channel@RoleArticle-roleId_Int", roleIds);
        }
        return this;

    }

    public ArticleArgs subSiteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Int", siteId);
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
    public Integer getSubChannelId() {
        return subChannelId;
    }

    @Nullable
    public Integer getSubOrgId() {
        return subOrgId;
    }
}

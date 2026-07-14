package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 评论 查询参数
 *
 * @author PONY
 */
public class CommentArgs extends BaseQueryArgs {
    public CommentArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public CommentArgs articleId(@Nullable Long articleId) {
        if (articleId != null) {
            queryMap.put("EQ_articleId_Long", articleId);
        }
        return this;
    }

    public CommentArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public CommentArgs parentId(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_parentId_Long", parentId);
        }
        return this;
    }

    public CommentArgs isRecommended(@Nullable Boolean isRecommended) {
        if (isRecommended != null) {
            queryMap.put("EQ_recommended_Boolean", isRecommended);
        }
        return this;
    }

    public CommentArgs status(Collection<Short> status) {
        if (!status.isEmpty()) {
            queryMap.put("In_status_Short", status);
        }
        return this;
    }

    public static CommentArgs of() {
        return of(new HashMap<>(16));
    }

    public static CommentArgs of(Map<String, Object> queryMap) {
        return new CommentArgs(queryMap);
    }

    private CommentArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}

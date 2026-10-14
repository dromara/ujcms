package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.ext.domain.generated.GeneratedLinkGroup;
import com.ujcms.common.db.order.OrderEntity;
import org.springframework.lang.Nullable;

/**
 * 链接组 实体类
 *
 * @author PONY
 */
public class LinkGroup extends GeneratedLinkGroup implements OrderEntity {
    /**
     * 链接数。只有在查询链接组列表且要求统计时有效（{@code LinkGroupArgs.queryLinkCount()}），其余为 {@code null}
     */
    @Nullable
    private Integer linkCount;

    /**
     * 未统计时不输出：链接组也经公开接口（{@code /api/link-group}）返回，不应多出一个恒为 null 的字段
     */
    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getLinkCount() {
        return linkCount;
    }

    public void setLinkCount(@Nullable Integer linkCount) {
        this.linkCount = linkCount;
    }

    /**
     * 类型：文字链接
     */
    public static final short TYPE_TEXT = 1;
    /**
     * 类型：图片链接
     */
    public static final short TYPE_IMAGE = 2;

    public static final String NOT_FOUND = "LinkGroup not found. ID = ";
}

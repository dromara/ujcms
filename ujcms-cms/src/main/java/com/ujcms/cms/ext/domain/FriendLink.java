package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.support.Anchor;
import com.ujcms.cms.ext.domain.generated.GeneratedFriendLink;
import com.ujcms.common.db.order.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;

/**
 * 友情链接实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class FriendLink extends GeneratedFriendLink implements Anchor, OrderEntity {
    @Schema(description = "链接地址")
    @Pattern(regexp = "^(http|/).*$")
    @Override
    public String getUrl() {
        return super.getUrl();
    }

    /**
     * 友情链接类型
     */
    @JsonIncludeProperties({"id", "name", "alias", "type"})
    private FriendLinkType type = new FriendLinkType();

    public FriendLinkType getType() {
        return type;
    }

    public void setType(FriendLinkType type) {
        this.type = type;
    }

    public static final String NOT_FOUND = "FriendLink not found. ID = ";
}

package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.generated.GeneratedFriendLinkType;
import com.ujcms.common.db.order.OrderEntity;

/**
 * 友情链接类型 实体类
 *
 * @author PONY
 */
public class FriendLinkType extends GeneratedFriendLinkType implements OrderEntity {
    /**
     * 类型：文字链接
     */
    public static final short TYPE_TEXT = 1;
    /**
     * 类型：图片链接
     */
    public static final short TYPE_IMAGE = 2;

    public static final String NOT_FOUND = "FriendLinkType not found. ID = ";
}

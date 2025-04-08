package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.CollectionFieldBase;

import java.io.Serializable;

/**
 * 采集 实体类
 *
 * @author PONY
 */
public class CollectionField extends CollectionFieldBase implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 来源：详细页
     */
    public static final short SOURCE_DETAIL = 1;
    /**
     * 来源：列表页
     */
    public static final short SOURCE_LIST = 2;
    /**
     * 来源：固定值
     */
    public static final short SOURCE_STATIC = 3;

    public static final String NOT_FOUND = "CollectionField not found. ID: ";
}
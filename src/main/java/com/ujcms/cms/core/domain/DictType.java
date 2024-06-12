package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.DictTypeBase;

import java.io.Serializable;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

/**
 * 字典类型实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class DictType extends DictTypeBase implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否全局共享
     */
    @JsonIgnore
    public boolean isGlobal() {
        return getScope() == SCOPE_GLOBAL;
    }

    /**
     * 数据类型：字符串
     */
    public static final short DATA_TYPE_STRING = 0;
    /**
     * 数据类型：整型
     */
    public static final short DATA_TYPE_INTEGER = 1;

    public static final String NOT_FOUND = "DictType not found. ID: ";
}
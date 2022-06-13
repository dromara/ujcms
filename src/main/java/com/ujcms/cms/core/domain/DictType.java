package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.cms.core.domain.base.DictTypeBase;

import java.io.Serializable;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

/**
 * 字典类型 实体类
 *
 * @author PONY
 */
public class DictType extends DictTypeBase implements Serializable {
    /**
     * 是否全局共享
     */
    @JsonIgnore
    public boolean isGlobal() {
        return getScope() == SCOPE_GLOBAL;
    }
}
package com.ujcms.cms.core.domain;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedDictType;
import org.jspecify.annotations.Nullable;

/**
 * 字典类型实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class DictType extends GeneratedDictType {

    /**
     * 是否全局共享
     */
    @JsonIgnore
    public boolean isGlobal() {
        return getScope() == SCOPE_GLOBAL;
    }

    /**
     * 条目数。只有在查询类型列表且要求统计时有效（{@code DictTypeArgs.queryEntryCount()}），其余为 {@code null}
     */
    @Nullable
    private Integer entryCount;

    @Nullable
    public Integer getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(@Nullable Integer entryCount) {
        this.entryCount = entryCount;
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
package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedResourceCategory;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 资源库分类实体类（共享资源库分类树）
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ResourceCategory extends GeneratedResourceCategory {

    public static final String NOT_FOUND = "ResourceCategory not found. ID: ";

    /**
     * 默认分类名称（共享但未归类的附件归入此分类）
     */
    public static final String UNCATEGORIZED = "未分类";

    /**
     * 子分类（构建树时填充，叶子节点为 null）
     */
    @Nullable
    private List<ResourceCategory> children;

    @Nullable
    public List<ResourceCategory> getChildren() {
        return children;
    }

    public void setChildren(@Nullable List<ResourceCategory> children) {
        this.children = children;
    }
}

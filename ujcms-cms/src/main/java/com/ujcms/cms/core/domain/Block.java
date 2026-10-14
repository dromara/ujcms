package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.generated.GeneratedBlock;
import org.hibernate.validator.constraints.Length;
import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

/**
 * 区块实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Block extends GeneratedBlock {
    /**
     * 区块项数。只有在查询区块列表且要求统计时有效（{@code BlockArgs.queryItemCount(siteId)}），其余为 {@code null}。
     * 只计指定站点的区块项：全局区块的区块项分属各站
     */
    @Nullable
    private Integer itemCount;

    @Nullable
    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(@Nullable Integer itemCount) {
        this.itemCount = itemCount;
    }

    /**
     * 是否全局共享
     */
    @JsonIgnore
    public boolean isGlobal() {
        return getScope() == SCOPE_GLOBAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Block bean = (Block) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    @Length(max = 50)
    @NotNull
    @Pattern(regexp = "^[\u4E00-\u9FA5\\w-]*$")
    public String getAlias() {
        return super.getAlias();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @JsonIncludeProperties({"id", "name", "fieldList"})
    @Nullable
    private Model blockItemModel;

    @Nullable
    public Model getBlockItemModel() {
        return blockItemModel;
    }

    public void setBlockItemModel(@Nullable Model blockItemModel) {
        this.blockItemModel = blockItemModel;
    }

    /**
     * 区块项模型类别
     */
    public static final String MODEL_TYPE = "blockItem";

    public static final String NOT_FOUND = "Block not found. ID: ";
}

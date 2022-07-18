package com.ujcms.cms.core.domain.base;

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.springframework.lang.Nullable;

/**
 * This class was generated by MyBatis Generator.
 *
 * @author MyBatis Generator
 */
public class DictBase {
    /**
     * 数据库表名
     */
    public static final String TABLE_NAME = "dict";

    /**
     * 字典ID
     */
    @NotNull
    private Integer id = 0;

    /**
     * 数据字典类型ID
     */
    @NotNull
    private Integer typeId = 0;

    /**
     * 上级ID
     */
    @Nullable
    private Integer parentId;

    /**
     * 名称
     */
    @Length(max = 50)
    @NotNull
    private String name = "";

    /**
     * 值
     */
    @Length(max = 50)
    @NotNull
    private String value = "";

    /**
     * 备注
     */
    @Length(max = 300)
    @Nullable
    private String remark;

    /**
     * 排列顺序
     */
    @NotNull
    private Short order = 32767;

    /**
     * 是否系统字典
     */
    @NotNull
    private Boolean sys = false;

    /**
     * 是否启用
     */
    @NotNull
    private Boolean enabled = true;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    @Nullable
    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(@Nullable Integer parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Nullable
    public String getRemark() {
        return remark;
    }

    public void setRemark(@Nullable String remark) {
        this.remark = remark;
    }

    public Short getOrder() {
        return order;
    }

    public void setOrder(Short order) {
        this.order = order;
    }

    public Boolean getSys() {
        return sys;
    }

    public void setSys(Boolean sys) {
        this.sys = sys;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
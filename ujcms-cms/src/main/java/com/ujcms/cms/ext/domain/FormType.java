package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.ext.domain.generated.GeneratedFormType;
import com.ujcms.common.db.order.OrderEntity;
import org.jspecify.annotations.Nullable;

/**
 * 模型类型
 *
 * @author PONY
 */
public class FormType extends GeneratedFormType implements OrderEntity {
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 表单模型
     */
    @JsonIncludeProperties({"id", "name"})
    private Model model = new Model();
    /**
     * 表单数，含各种状态（已删除状态的也算：类型下只要还有表单就不可删除）。只有在查询表单类型列表且要求统计时有效
     * （{@code FormTypeArgs.queryFormCount()}），其余为 {@code null}
     */
    @Nullable
    private Integer formCount;

    /**
     * 未统计时不输出：表单类型也经公开接口（{@code /api/form-type}）返回，不应多出一个恒为 null 的字段
     */
    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getFormCount() {
        return formCount;
    }

    public void setFormCount(@Nullable Integer formCount) {
        this.formCount = formCount;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public static final String NOT_FOUND = "FormType not found. ID: ";

    /**
     * 绑定模型类型（{@code ujcms_model.type_}）——表单类型绑定 {@code form} 型模型。
     * 与 {@code Article/Channel/Topic/Block.MODEL_TYPE} 同一约定（theme-seed-export E4e：
     * 种子导入按名称在此类型下解析绑定模型）。
     */
    public static final String MODEL_TYPE = "form";

    /**
     * 录入模式：前台游客
     */
    public static final short MODE_GUEST = 0;
    /**
     * 录入模式：前台用户
     */
    public static final short MODE_FRONTEND = 1;
    /**
     * 录入模式：仅后台用户
     */
    public static final short MODE_BACKEND = 2;
}
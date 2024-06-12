package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.ext.domain.base.FormTypeBase;
import com.ujcms.commons.db.order.OrderEntity;

import java.io.Serializable;

/**
 * 模型类型
 *
 * @author PONY
 */
public class FormType extends FormTypeBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;
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
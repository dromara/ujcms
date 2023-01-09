package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.SiteCustomBase;
import com.ujcms.cms.core.domain.support.CustomBean;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 站点自定义实体类
 *
 * @author PONY
 */
@Schema(name = "Site.SiteCustom")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class SiteCustom extends SiteCustomBase implements Serializable, CustomBean {
    private static final long serialVersionUID = 1L;

    public SiteCustom() {
    }

    public SiteCustom(Integer articleId, String name,String type, String value) {
        setSiteId(articleId);
        setName(name);
        setType(type);
        setValue(value);
    }
}
package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.SiteCustomBase;
import com.ujcms.cms.core.domain.support.CustomBean;

import java.io.Serializable;

/**
 * 站点自定义 实体类
 *
 * @author PONY
 */
public class SiteCustom extends SiteCustomBase implements Serializable, CustomBean {
    public SiteCustom() {
    }

    public SiteCustom(Integer articleId, String name,String type, String value) {
        setSiteId(articleId);
        setName(name);
        setType(type);
        setValue(value);
    }
}
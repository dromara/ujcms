package com.ujcms.core.domain;

import com.ujcms.core.domain.base.SiteCustomBase;
import com.ujcms.core.domain.support.CustomBean;

import java.io.Serializable;

/**
 * 站点自定义 实体类
 *
 * @author PONY
 */
public class SiteCustom extends SiteCustomBase implements Serializable, CustomBean {
    public SiteCustom() {
    }

    public SiteCustom(Integer articleId, String name, String value) {
        setSiteId(articleId);
        setName(name);
        setValue(value);
    }
}